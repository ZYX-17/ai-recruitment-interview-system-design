package com.recruit.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.idempotency.IdempotencyService;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import com.recruit.contracts.event.EventTypes;
import com.recruit.contracts.event.payload.InterviewCancelledPayload;
import com.recruit.contracts.event.payload.InterviewRescheduledPayload;
import com.recruit.contracts.event.payload.InterviewScheduledPayload;
import com.recruit.interview.dto.CalendarEventView;
import com.recruit.interview.dto.CalendarQuery;
import com.recruit.interview.dto.ConflictCheckForm;
import com.recruit.interview.dto.ConflictCheckResult;
import com.recruit.interview.dto.ConflictItemView;
import com.recruit.interview.dto.ScheduleBatchForm;
import com.recruit.interview.dto.ScheduleCreateForm;
import com.recruit.interview.dto.SchedulePageQuery;
import com.recruit.interview.dto.ScheduleUpdateForm;
import com.recruit.interview.dto.ScheduleView;
import com.recruit.interview.entity.Application;
import com.recruit.interview.entity.Interview;
import com.recruit.interview.entity.Job;
import com.recruit.interview.entity.ScheduleSlot;
import com.recruit.interview.event.OutboxPublisher;
import com.recruit.interview.mapper.ApplicationMapper;
import com.recruit.interview.mapper.InterviewMapper;
import com.recruit.interview.mapper.JobMapper;
import com.recruit.interview.mapper.ScheduleSlotMapper;
import com.recruit.interview.mapper.row.InterviewRow;
import com.recruit.interview.mapper.row.ScheduleConflictRow;
import com.recruit.interview.support.IdSupport;
import com.recruit.interview.support.ScheduleConflictException;
import com.recruit.interview.support.ScheduleParticipant;
import com.recruit.interview.support.ScheduleSlotKey;
import com.recruit.interview.support.ScheduleSlotWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 排期管理（P5-1~P5-6）。
 *
 * <p>关键口径（docs/4 §5.4、§7.5）：</p>
 * <ul>
 *   <li>起止必须对齐 30 分钟，槽位按 Asia/Shanghai 计算、时间戳存 UTC；</li>
 *   <li>参与者槽位 = 每个面试官 + 候选人 + 资源，写入与 Outbox 同事务；</li>
 *   <li>预检只读；真正并发由 {@code schedule_slot} 唯一索引兜底，冲突 409 {@code ITV3101}；</li>
 *   <li>改期先删旧槽再插新槽，冲突回滚保留旧排期，发 {@code interview.rescheduled}；</li>
 *   <li>取消物理删槽、面试置 {@code CANCELLED}（不删行），已开始不允许取消；</li>
 *   <li>批量 50 条上限，整批事务，失败返回全部冲突明细。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    public static final String STATUS_ROOM_PENDING = "ROOM_PENDING";
    public static final String STATUS_SCHEDULED = "SCHEDULED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_NO_SHOW = "NO_SHOW";

    private static final Set<String> STATUSES = Set.of(
            STATUS_ROOM_PENDING, STATUS_SCHEDULED, STATUS_IN_PROGRESS,
            STATUS_COMPLETED, STATUS_CANCELLED, STATUS_NO_SHOW);

    /** 排期只支持线上同步面试；异步试卷走 P9 的 async-interviews 端点。 */
    private static final Set<String> MODES = Set.of("ONLINE");

    private static final Set<String> ROOM_TYPES = Set.of("ONE_TO_ONE", "ONE_TO_MANY");

    private static final List<String> CANCELLABLE = List.of(STATUS_ROOM_PENDING, STATUS_SCHEDULED);

    private static final int MAX_BATCH = 50;

    private static final int MAX_CALENDAR_DAYS = 366;

    /** 单场面试最多覆盖 48 格（24 小时），防止异常入参生成超大槽位集合。 */
    private static final int MAX_COVERED_SLOTS = 48;

    private static final String SCOPE_CREATE = "interview.schedule.create";
    private static final String SCOPE_BATCH = "interview.schedule.batch";

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<ScheduleView>> SCHEDULE_LIST = new TypeReference<>() {
    };

    private final InterviewMapper interviewMapper;
    private final ScheduleSlotMapper slotMapper;
    private final ApplicationMapper applicationMapper;
    private final JobMapper jobMapper;
    private final OutboxPublisher outboxPublisher;
    private final ObjectMapper objectMapper;
    private final DataScopeService dataScopeService;
    private final IdempotencyService idempotencyService;

    /**
     * 排期分页。
     *
     * @param query 分页与过滤条件
     * @return 分页排期
     */
    public PageResult<ScheduleView> page(SchedulePageQuery query) {
        DataScopeService.DataScope scope = dataScopeService.current();
        String status = trimToNull(query.getStatus());
        if (status != null) {
            status = requireStatus(status);
        }
        LocalDate from = query.getDateFrom();
        LocalDate to = query.getDateTo();
        if (from != null && to != null && to.isBefore(from)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "结束日期不能早于开始日期");
        }
        LocalDateTime startFrom = from == null ? null : utcStartOfDay(from);
        LocalDateTime startTo = to == null ? null : utcStartOfDay(to.plusDays(1));
        Long interviewerId = IdSupport.optionalLong(query.getInterviewerId(), "interviewerId");
        String interviewerIdText = interviewerId == null ? null : String.valueOf(interviewerId);
        String userIdText = String.valueOf(scope.userId());

        long total = interviewMapper.countSchedulePage(status, startFrom, startTo,
                interviewerIdText, null, scope.sqlScope(), scope.deptIds(), scope.userId(), userIdText);
        if (total == 0) {
            return PageResult.of(List.of(), 0, query.getPageNum(), query.getPageSize());
        }
        long offset = (long) (query.getPageNum() - 1) * query.getPageSize();
        List<InterviewRow> rows = interviewMapper.selectSchedulePage(status, startFrom, startTo,
                interviewerIdText, null, scope.sqlScope(), scope.deptIds(), scope.userId(),
                userIdText, query.getPageSize(), offset);
        List<ScheduleView> views = new ArrayList<>(rows.size());
        for (InterviewRow row : rows) {
            views.add(toView(row));
        }
        return PageResult.of(views, total, query.getPageNum(), query.getPageSize());
    }

    /**
     * 排期详情。
     *
     * @param id 面试 ID
     * @return 排期详情
     */
    public ScheduleView detail(long id) {
        return toView(loadVisibleRow(id));
    }

    /**
     * 新建排期：interview + 全部 slot + {@code interview.scheduled} 同事务。
     *
     * @param form 创建入参
     * @param key  Idempotency-Key，可为空
     * @return 新排期
     */
    @Transactional
    public ScheduleView create(ScheduleCreateForm form, String key) {
        if (hasText(key)) {
            return idempotencyService.execute(SCOPE_CREATE, key, ScheduleView.class, () -> doCreate(form));
        }
        return doCreate(form);
    }

    private ScheduleView doCreate(ScheduleCreateForm form) {
        Plan plan = buildPlan(form);
        if (interviewMapper.countByApplicationAndRound(
                plan.application().getId(), plan.roundNo()) > 0) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "该投递本轮面试已存在");
        }
        List<ConflictItemView> conflicts = detect(plan.slots(), plan.participants(),
                List.of(), plan.application().getPersonName());
        if (!conflicts.isEmpty()) {
            throw new ScheduleConflictException(conflicts);
        }

        Interview interview = newInterview(plan);
        insertInterview(interview);
        try {
            insertSlots(interview.getId(), plan.participants(), plan.slots());
        } catch (DuplicateKeyException e) {
            throw new ScheduleConflictException(detect(plan.slots(), plan.participants(),
                    List.of(interview.getId()), plan.application().getPersonName()));
        }
        publishScheduled(interview, plan);
        return toView(reload(interview), plan.job(), plan.application());
    }

    /**
     * 改期/改面试官：先删旧槽再插新槽，同一事务；冲突则整体回滚保留旧排期。
     *
     * @param id   面试 ID
     * @param form 修改入参
     * @return 修改后的排期
     */
    @Transactional
    public ScheduleView update(long id, ScheduleUpdateForm form) {
        InterviewRow current = loadVisibleRow(id);
        if (!CANCELLABLE.contains(current.getStatus())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "当前状态不可改期");
        }
        if (!Objects.equals(current.getVersion(), form.getVersion())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }

        List<Long> interviewerIds = parseInterviewerIds(form.getInterviewerIds());
        Long resourceId = resolveResourceId(form.getResourceId(), current.getId());
        List<ScheduleSlotKey> slots = cover(form.getStartTime(), form.getEndTime());
        List<ScheduleParticipant> participants = participants(interviewerIds,
                current.getPersonId(), resourceId);
        String mode = requireMode(form.getMode());
        String roomType = requireRoomType(form.getRoomType());

        List<ConflictItemView> conflicts = detect(slots, participants,
                List.of(current.getId()), current.getPersonName());
        if (!conflicts.isEmpty()) {
            throw new ScheduleConflictException(conflicts);
        }

        slotMapper.deleteByInterviewId(id);
        try {
            insertSlots(id, participants, slots);
        } catch (DuplicateKeyException e) {
            throw new ScheduleConflictException(detect(slots, participants,
                    List.of(current.getId()), current.getPersonName()));
        }

        Interview update = new Interview();
        update.setId(id);
        update.setVersion(form.getVersion());
        update.setInterviewerIds(writeInterviewerIds(interviewerIds));
        update.setStartTime(toUtc(form.getStartTime()));
        update.setEndTime(toUtc(form.getEndTime()));
        update.setMode(mode);
        update.setRoomType(roomType);
        update.setNote(form.getNote() == null ? current.getNote() : trimToNull(form.getNote()));
        if (interviewMapper.updateScheduleWithVersion(update) == 0) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }

        if (!Objects.equals(current.getStartTime(), update.getStartTime())) {
            outboxPublisher.publish(EventTypes.INTERVIEW_RESCHEDULED, String.valueOf(id), "interview",
                    new InterviewRescheduledPayload(String.valueOf(id),
                            String.valueOf(current.getApplicationId()),
                            toInstant(current.getStartTime()), form.getStartTime(),
                            IdSupport.asString(current.getRoomId()), Instant.now()));
        }
        return detail(id);
    }

    /**
     * 取消排期：幂等；物理删槽 + 状态置 CANCELLED + {@code interview.cancelled}。
     *
     * @param id      面试 ID
     * @param version 期望版本；已取消时可省略
     * @param reason  取消原因
     * @return 取消后的排期
     */
    @Transactional
    public ScheduleView cancel(long id, Integer version, String reason) {
        InterviewRow current = loadVisibleRow(id);
        if (STATUS_CANCELLED.equals(current.getStatus())) {
            return toView(current);
        }
        if (!CANCELLABLE.contains(current.getStatus())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "面试已开始或已结束，不能取消");
        }
        if (version == null || !Objects.equals(current.getVersion(), version)) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }

        slotMapper.deleteByInterviewId(id);
        if (interviewMapper.cancelWithVersion(id, trimToNull(reason), version) == 0) {
            Interview latest = interviewMapper.selectById(id);
            if (latest != null && STATUS_CANCELLED.equals(latest.getStatus())) {
                return detail(id);
            }
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突或状态不允许取消");
        }
        outboxPublisher.publish(EventTypes.INTERVIEW_CANCELLED, String.valueOf(id), "interview",
                new InterviewCancelledPayload(String.valueOf(id),
                        String.valueOf(current.getApplicationId()),
                        IdSupport.asString(current.getRoomId()),
                        trimToNull(reason), Instant.now()));
        return detail(id);
    }

    /**
     * 日历查询：只读，按业务日期范围过滤。
     *
     * @param query 查询条件
     * @return 日历事件（按开始时间升序）
     */
    public List<CalendarEventView> calendar(CalendarQuery query) {
        DataScopeService.DataScope scope = dataScopeService.current();
        LocalDate from = query.getDateFrom() == null
                ? LocalDate.now(ScheduleSlotWindow.BUSINESS_ZONE) : query.getDateFrom();
        LocalDate to = query.getDateTo() == null ? from.plusDays(29) : query.getDateTo();
        if (to.isBefore(from)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "结束日期不能早于开始日期");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_CALENDAR_DAYS) {
            throw new BizException(ErrorCode.PARAM_INVALID, "日历查询范围不能超过 366 天");
        }
        Long interviewerId = IdSupport.optionalLong(query.getInterviewerId(), "interviewerId");
        Long deptId = IdSupport.optionalLong(query.getDeptId(), "deptId");
        List<InterviewRow> rows = interviewMapper.selectCalendar(utcStartOfDay(from),
                utcStartOfDay(to.plusDays(1)),
                interviewerId == null ? null : String.valueOf(interviewerId),
                deptId, scope.sqlScope(), scope.deptIds(), scope.userId(),
                String.valueOf(scope.userId()));
        List<CalendarEventView> events = new ArrayList<>(rows.size());
        for (InterviewRow row : rows) {
            events.add(new CalendarEventView(
                    IdSupport.asString(row.getId()),
                    IdSupport.asString(row.getApplicationId()),
                    IdSupport.asString(row.getJobId()),
                    row.getJobTitle(),
                    IdSupport.asString(row.getPersonId()),
                    row.getPersonName(),
                    row.getRoundNo(),
                    readInterviewerIds(row.getInterviewerIds()),
                    toInstant(row.getStartTime()),
                    toInstant(row.getEndTime()),
                    row.getStatus(),
                    row.getMode(),
                    row.getRoomType()));
        }
        return events;
    }

    /**
     * 冲突预检：只读，不锁行、不写库。
     *
     * @param form 预检入参
     * @return 冲突结果
     */
    public ConflictCheckResult conflictCheck(ConflictCheckForm form) {
        long candidateId = IdSupport.requireLong(form.getCandidateId(), "candidateId");
        List<Long> interviewerIds = parseInterviewerIds(form.getInterviewerIds());
        Long resourceId = IdSupport.optionalLong(form.getResourceId(), "resourceId");
        List<ScheduleSlotKey> slots = cover(form.getStartTime(), form.getEndTime());
        List<ScheduleParticipant> participants = participants(interviewerIds, candidateId, resourceId);
        return ConflictCheckResult.of(detect(slots, participants, List.of(),
                "候选人-" + candidateId));
    }

    /**
     * 批量排期：整批事务、单批 ≤50、失败返回全部冲突明细。
     *
     * @param form 批量入参
     * @param key  Idempotency-Key，可为空
     * @return 新建的排期列表（与入参顺序一致）
     */
    @Transactional
    public List<ScheduleView> batch(ScheduleBatchForm form, String key) {
        if (form == null || form.getItems() == null || form.getItems().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "批量排期不能为空");
        }
        if (form.getItems().size() > MAX_BATCH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单批最多 50 条排期");
        }
        if (hasText(key)) {
            return idempotencyService.execute(SCOPE_BATCH, key, SCHEDULE_LIST, () -> doBatch(form));
        }
        return doBatch(form);
    }

    private List<ScheduleView> doBatch(ScheduleBatchForm form) {
        List<Plan> plans = new ArrayList<>(form.getItems().size());
        Set<String> seen = new HashSet<>();
        for (ScheduleCreateForm item : form.getItems()) {
            Plan plan = buildPlan(item);
            String key = plan.application().getId() + ":" + plan.roundNo();
            if (!seen.add(key)
                    || interviewMapper.countByApplicationAndRound(
                    plan.application().getId(), plan.roundNo()) > 0) {
                throw new BizException(ErrorCode.ILLEGAL_TRANSITION,
                        "投递 " + plan.application().getId() + " 第 " + plan.roundNo() + " 轮面试已存在");
            }
            plans.add(plan);
        }

        List<ConflictItemView> conflicts = new ArrayList<>();
        for (Plan plan : plans) {
            conflicts.addAll(detect(plan.slots(), plan.participants(),
                    List.of(), plan.application().getPersonName()));
        }
        if (!conflicts.isEmpty()) {
            throw new ScheduleConflictException(conflicts);
        }

        List<Long> created = new ArrayList<>(plans.size());
        List<ScheduleView> views = new ArrayList<>(plans.size());
        List<ConflictItemView> raceConflicts = new ArrayList<>();
        for (Plan plan : plans) {
            Interview interview = newInterview(plan);
            insertInterview(interview);
            created.add(interview.getId());
            try {
                insertSlots(interview.getId(), plan.participants(), plan.slots());
            } catch (DuplicateKeyException e) {
                raceConflicts.addAll(detect(plan.slots(), plan.participants(), created,
                        plan.application().getPersonName()));
                continue;
            }
            publishScheduled(interview, plan);
            views.add(toView(reload(interview), plan.job(), plan.application()));
        }
        if (!raceConflicts.isEmpty()) {
            throw new ScheduleConflictException(raceConflicts);
        }
        return views;
    }

    private Plan buildPlan(ScheduleCreateForm form) {
        long applicationId = IdSupport.requireLong(form.getApplicationId(), "applicationId");
        Application application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BizException(ErrorCode.APP_NOT_FOUND, "投递不存在");
        }
        Job job = jobMapper.selectById(application.getJobId());
        if (job == null) {
            throw new BizException(ErrorCode.APP_NOT_FOUND, "投递关联岗位不存在");
        }
        List<Long> interviewerIds = parseInterviewerIds(form.getInterviewerIds());
        requireVisible(job, interviewerIds);

        Long declaredCandidateId = IdSupport.optionalLong(form.getCandidateId(), "candidateId");
        if (declaredCandidateId != null && !declaredCandidateId.equals(application.getPersonId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "候选人与投递不一致");
        }
        long candidateId = application.getPersonId();
        Long resourceId = IdSupport.optionalLong(form.getResourceId(), "resourceId");
        int roundNo = form.getRoundNo() == null ? 1 : form.getRoundNo();
        if (roundNo < 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "roundNo 必须大于 0");
        }
        String mode = requireMode(form.getMode());
        String roomType = requireRoomType(form.getRoomType());
        List<ScheduleSlotKey> slots = cover(form.getStartTime(), form.getEndTime());
        return new Plan(application, job, roundNo, candidateId, resourceId, interviewerIds, slots,
                participants(interviewerIds, candidateId, resourceId),
                form.getStartTime(), form.getEndTime(), mode, roomType, trimToNull(form.getNote()));
    }

    private static List<ScheduleSlotKey> cover(Instant start, Instant end) {
        List<ScheduleSlotKey> slots = ScheduleSlotWindow.cover(start, end);
        if (slots.size() > MAX_COVERED_SLOTS) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单场面试时长不能超过 24 小时");
        }
        return slots;
    }

    private Interview reload(Interview interview) {
        Interview saved = interview.getId() == null ? null : interviewMapper.selectById(interview.getId());
        return saved == null ? interview : saved;
    }

    private Interview newInterview(Plan plan) {
        Interview interview = new Interview();
        interview.setApplicationId(plan.application().getId());
        interview.setRoundNo(plan.roundNo());
        interview.setInterviewerIds(writeInterviewerIds(plan.interviewerIds()));
        interview.setStartTime(toUtc(plan.formStart()));
        interview.setEndTime(toUtc(plan.formEnd()));
        interview.setMode(plan.mode());
        interview.setRoomType(plan.roomType());
        interview.setStatus(STATUS_ROOM_PENDING);
        interview.setNote(plan.note());
        interview.setVersion(0);
        return interview;
    }

    private void insertInterview(Interview interview) {
        try {
            interviewMapper.insert(interview);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "该投递本轮面试已存在");
        }
    }

    private void insertSlots(long interviewId, List<ScheduleParticipant> participants,
                             List<ScheduleSlotKey> slots) {
        for (ScheduleParticipant participant : participants) {
            for (ScheduleSlotKey key : slots) {
                ScheduleSlot slot = new ScheduleSlot();
                slot.setParticipantType(participant.participantType());
                slot.setParticipantId(participant.participantId());
                slot.setSlotDate(key.slotDate());
                slot.setSlotNo(key.slotNo());
                slot.setInterviewId(interviewId);
                slotMapper.insert(slot);
            }
        }
    }

    private void publishScheduled(Interview interview, Plan plan) {
        outboxPublisher.publish(EventTypes.INTERVIEW_SCHEDULED, String.valueOf(interview.getId()),
                "interview", new InterviewScheduledPayload(
                        String.valueOf(interview.getId()),
                        String.valueOf(interview.getApplicationId()),
                        interview.getRoundNo(),
                        plan.interviewerIds().stream().map(String::valueOf).toList(),
                        String.valueOf(plan.candidateId()),
                        plan.application().getPersonName(),
                        toInstant(interview.getStartTime()),
                        toInstant(interview.getEndTime()),
                        interview.getMode(),
                        interview.getRoomType(),
                        IdSupport.asString(interview.getRoomId()),
                        plan.job().getTitle()));
    }

    private List<ConflictItemView> detect(List<ScheduleSlotKey> slots,
                                          List<ScheduleParticipant> participants,
                                          List<Long> excludeInterviewIds,
                                          String candidateName) {
        List<ScheduleConflictRow> rows = slotMapper.selectConflicts(slots, participants, excludeInterviewIds);
        List<ConflictItemView> conflicts = new ArrayList<>(rows.size());
        for (ScheduleConflictRow row : rows) {
            conflicts.add(new ConflictItemView(
                    row.getParticipantType(),
                    IdSupport.asString(row.getParticipantId()),
                    participantName(row.getParticipantType(), row.getParticipantId(), candidateName),
                    row.getSlotDate(),
                    row.getSlotNo(),
                    IdSupport.asString(row.getInterviewId()),
                    toInstant(row.getStartTime()),
                    toInstant(row.getEndTime())));
        }
        return conflicts;
    }

    private InterviewRow loadVisibleRow(long id) {
        InterviewRow row = interviewMapper.selectRowById(id);
        if (row == null) {
            throw new BizException(ErrorCode.APP_NOT_FOUND, "面试安排不存在");
        }
        DataScopeService.DataScope scope = dataScopeService.current();
        if (!scope.canSee(row.getDeptId(), row.getOwnerHrId())
                && !readInterviewerIds(row.getInterviewerIds()).contains(String.valueOf(scope.userId()))) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无权访问该面试安排");
        }
        return row;
    }

    private void requireVisible(Job job, List<Long> interviewerIds) {
        DataScopeService.DataScope scope = dataScopeService.current();
        if (scope.canSee(job.getDeptId(), job.getOwnerHrId())
                || interviewerIds.contains(scope.userId())) {
            return;
        }
        throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无权访问该岗位排期");
    }

    private Long resolveResourceId(String value, long interviewId) {
        if (hasText(value)) {
            return IdSupport.requireLong(value, "resourceId");
        }
        for (ScheduleSlot slot : slotMapper.selectByInterviewId(interviewId)) {
            if (ScheduleParticipant.RESOURCE.equals(slot.getParticipantType())) {
                return slot.getParticipantId();
            }
        }
        return null;
    }

    private ScheduleView toView(InterviewRow row) {
        return new ScheduleView(
                IdSupport.asString(row.getId()),
                IdSupport.asString(row.getApplicationId()),
                IdSupport.asString(row.getJobId()),
                row.getJobTitle(),
                IdSupport.asString(row.getPersonId()),
                row.getPersonName(),
                row.getRoundNo(),
                readInterviewerIds(row.getInterviewerIds()),
                toInstant(row.getStartTime()),
                toInstant(row.getEndTime()),
                row.getMode(),
                row.getRoomType(),
                row.getStatus(),
                IdSupport.asString(row.getRoomId()),
                row.getCancelReason(),
                row.getNote(),
                row.getVersion(),
                toInstant(row.getCreatedAt()),
                toInstant(row.getUpdatedAt()));
    }

    private ScheduleView toView(Interview interview, Job job, Application application) {
        return new ScheduleView(
                IdSupport.asString(interview.getId()),
                IdSupport.asString(interview.getApplicationId()),
                IdSupport.asString(job.getId()),
                job.getTitle(),
                IdSupport.asString(application.getPersonId()),
                application.getPersonName(),
                interview.getRoundNo(),
                readInterviewerIds(interview.getInterviewerIds()),
                toInstant(interview.getStartTime()),
                toInstant(interview.getEndTime()),
                interview.getMode(),
                interview.getRoomType(),
                interview.getStatus(),
                IdSupport.asString(interview.getRoomId()),
                interview.getCancelReason(),
                interview.getNote(),
                interview.getVersion(),
                toInstant(interview.getCreatedAt()),
                toInstant(interview.getUpdatedAt()));
    }

    private List<String> readInterviewerIds(String json) {
        if (!hasText(json)) {
            return List.of();
        }
        try {
            List<String> ids = objectMapper.readValue(json, STRING_LIST);
            return ids == null ? List.of() : List.copyOf(ids);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String writeInterviewerIds(List<Long> interviewerIds) {
        List<String> values = interviewerIds.stream().map(String::valueOf).toList();
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYS_ERROR, "面试官序列化失败", e);
        }
    }

    private static List<Long> parseInterviewerIds(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "面试官不能为空");
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (String value : values) {
            ids.add(IdSupport.requireLong(value, "interviewerIds"));
        }
        if (ids.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "面试官不能为空");
        }
        return List.copyOf(ids);
    }

    private static List<ScheduleParticipant> participants(List<Long> interviewerIds,
                                                          long candidateId, Long resourceId) {
        List<ScheduleParticipant> participants = new ArrayList<>(interviewerIds.size() + 2);
        for (Long interviewerId : interviewerIds) {
            participants.add(new ScheduleParticipant(ScheduleParticipant.INTERVIEWER, interviewerId));
        }
        participants.add(new ScheduleParticipant(ScheduleParticipant.CANDIDATE, candidateId));
        if (resourceId != null) {
            participants.add(new ScheduleParticipant(ScheduleParticipant.RESOURCE, resourceId));
        }
        return participants;
    }

    private static String participantName(String type, Long id, String candidateName) {
        if (ScheduleParticipant.CANDIDATE.equals(type)) {
            return hasText(candidateName) ? candidateName : "候选人-" + id;
        }
        if (ScheduleParticipant.INTERVIEWER.equals(type)) {
            return "面试官-" + id;
        }
        return "资源-" + id;
    }

    private static String requireStatus(String value) {
        String status = value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "面试状态非法: " + value);
        }
        return status;
    }

    private static String requireMode(String value) {
        String mode = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!MODES.contains(mode)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "排期仅支持 ONLINE 面试，异步面试请使用异步试卷接口");
        }
        return mode;
    }

    private static String requireRoomType(String value) {
        String roomType = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!ROOM_TYPES.contains(roomType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "房间类型非法: " + value);
        }
        return roomType;
    }

    private static LocalDateTime toUtc(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static LocalDateTime utcStartOfDay(LocalDate date) {
        return LocalDateTime.ofInstant(ScheduleSlotWindow.startOfDay(date), ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 单条排期的写入计划：入参 + 投递/岗位快照 + 计算好的槽位与参与者。
     */
    private record Plan(
            Application application,
            Job job,
            int roundNo,
            long candidateId,
            Long resourceId,
            List<Long> interviewerIds,
            List<ScheduleSlotKey> slots,
            List<ScheduleParticipant> participants,
            Instant formStart,
            Instant formEnd,
            String mode,
            String roomType,
            String note) {
    }
}
