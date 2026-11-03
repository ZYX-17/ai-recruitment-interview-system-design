package com.recruit.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.idempotency.IdempotencyService;
import com.recruit.common.result.ErrorCode;
import com.recruit.contracts.event.EventTypes;
import com.recruit.contracts.event.payload.InterviewResultSubmittedPayload;
import com.recruit.interview.dto.AiDraftView;
import com.recruit.interview.dto.EvaluationComparisonView;
import com.recruit.interview.dto.EvaluationReopenForm;
import com.recruit.interview.dto.EvaluationSubmitForm;
import com.recruit.interview.dto.EvaluationView;
import com.recruit.interview.dto.MyEvaluationTaskView;
import com.recruit.interview.entity.Evaluation;
import com.recruit.interview.entity.EvaluationDimension;
import com.recruit.interview.entity.InterviewMinutes;
import com.recruit.interview.entity.JobStageTemplate;
import com.recruit.interview.event.OutboxPublisher;
import com.recruit.interview.mapper.EvaluationDimensionMapper;
import com.recruit.interview.mapper.EvaluationMapper;
import com.recruit.interview.mapper.InterviewMapper;
import com.recruit.interview.mapper.InterviewMinutesMapper;
import com.recruit.interview.mapper.JobStageTemplateMapper;
import com.recruit.interview.mapper.row.InterviewRow;
import com.recruit.interview.mapper.row.MyEvaluationRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评价提交/对比/AI 草稿/重开（P6-2~P6-7）。
 */
@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @Mock
    private EvaluationMapper evaluationMapper;
    @Mock
    private InterviewMapper interviewMapper;
    @Mock
    private InterviewMinutesMapper minutesMapper;
    @Mock
    private JobStageTemplateMapper templateMapper;
    @Mock
    private EvaluationDimensionMapper dimensionMapper;
    @Mock
    private OutboxPublisher outboxPublisher;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private IdempotencyService idempotencyService;

    private EvaluationService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationService(evaluationMapper, interviewMapper, minutesMapper,
                templateMapper, dimensionMapper, outboxPublisher, new ObjectMapper(),
                dataScopeService, idempotencyService);
        UserContext.set("10002", List.of("INTERVIEWER"), "面试官", "trace");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void submitComputesWeightedTotalAndStoresSubmitted() {
        stubInterview("[\"10002\",\"10003\"]");
        stubTemplate();
        when(evaluationMapper.selectByInterviewAndInterviewer(90001L, 1, 10002L)).thenReturn(null);
        when(evaluationMapper.insert(any(Evaluation.class))).thenAnswer(invocation -> {
            Evaluation evaluation = invocation.getArgument(0);
            evaluation.setId(91001L);
            return 1;
        });
        when(evaluationMapper.selectByInterviewId(90001L)).thenReturn(List.of());

        EvaluationView view = service.submit(90001L, form("PASS", "90", "80"), null);

        assertThat(view.totalScore()).isEqualByComparingTo("86.0");
        assertThat(view.result()).isEqualTo("PASS");
        assertThat(view.status()).isEqualTo("SUBMITTED");
        assertThat(view.templateVersion()).isEqualTo(2);
        ArgumentCaptor<Evaluation> captor = ArgumentCaptor.forClass(Evaluation.class);
        verify(evaluationMapper).insert(captor.capture());
        assertThat(captor.getValue().getInterviewerId()).isEqualTo(10002L);
        assertThat(captor.getValue().getRoundNo()).isEqualTo(1);
        assertThat(captor.getValue().getDimensionScores()).contains("PROFESSIONAL").contains("90");
        verify(outboxPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    void submitForcesFailWhenVetoThresholdHit() {
        stubInterview("[\"10002\",\"10003\"]");
        stubTemplate();
        when(evaluationMapper.selectByInterviewAndInterviewer(90001L, 1, 10002L)).thenReturn(null);
        when(evaluationMapper.insert(any(Evaluation.class))).thenAnswer(invocation -> {
            Evaluation evaluation = invocation.getArgument(0);
            evaluation.setId(91001L);
            return 1;
        });
        when(evaluationMapper.selectByInterviewId(90001L)).thenReturn(List.of());

        EvaluationView view = service.submit(90001L, form("PASS", "50", "90"), null);

        assertThat(view.result()).isEqualTo("FAIL");
    }

    @Test
    void submitRejectsNonInterviewer() {
        stubInterview("[\"10003\"]");

        assertThatThrownBy(() -> service.submit(90001L, form("PASS", "90", "80"), null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_FORBIDDEN);
        verify(evaluationMapper, never()).insert(any(Evaluation.class));
    }

    @Test
    void submitRejectsDuplicateSubmission() {
        stubInterview("[\"10002\"]");
        stubTemplate();
        when(evaluationMapper.selectByInterviewAndInterviewer(90001L, 1, 10002L))
                .thenReturn(submitted(91001L, "[\"10002\"]", 0, "PASS"));

        assertThatThrownBy(() -> service.submit(90001L, form("PASS", "90", "80"), null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ILLEGAL_TRANSITION);
        verify(evaluationMapper, never()).insert(any(Evaluation.class));
    }

    @Test
    void submitRejectsUnknownDimension() {
        stubInterview("[\"10002\"]");
        stubTemplate();

        EvaluationSubmitForm form = form("PASS", "90", "80");
        form.getDimensionScores().put("UNKNOWN", BigDecimal.valueOf(10));

        assertThatThrownBy(() -> service.submit(90001L, form, null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void submitRejectsScoreAboveFullScore() {
        stubInterview("[\"10002\"]");
        stubTemplate();

        assertThatThrownBy(() -> service.submit(90001L, form("PASS", "120", "80"), null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void submitRejectsStaleVersionOnReopenedEvaluation() {
        stubInterview("[\"10002\"]");
        stubTemplate();
        when(evaluationMapper.selectByInterviewAndInterviewer(90001L, 1, 10002L))
                .thenReturn(reopened(91001L, 3));

        assertThatThrownBy(() -> service.submit(90001L, form("PASS", "90", "80"), null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ILLEGAL_TRANSITION);
    }

    @Test
    void submitResubmitsReopenedEvaluation() {
        stubInterview("[\"10002\",\"10003\"]");
        stubTemplate();
        when(evaluationMapper.selectByInterviewAndInterviewer(90001L, 1, 10002L))
                .thenReturn(reopened(91001L, 0));
        when(evaluationMapper.resubmit(any(Evaluation.class))).thenReturn(1);
        when(evaluationMapper.selectById(91001L))
                .thenReturn(submitted(91001L, "[\"10002\"]", 1, "PASS"));
        when(evaluationMapper.selectByInterviewId(90001L)).thenReturn(List.of());

        EvaluationView view = service.submit(90001L, form("PASS", "90", "80"), null);

        verify(evaluationMapper, never()).insert(any(Evaluation.class));
        verify(evaluationMapper).resubmit(any(Evaluation.class));
        assertThat(view.status()).isEqualTo("SUBMITTED");
        assertThat(view.version()).isEqualTo(1);
    }

    @Test
    void submitPublishesResultEventWhenAllInterviewersDone() {
        stubInterview("[\"10002\",\"10003\"]");
        stubTemplate();
        Evaluation first = submitted(91001L, "[\"10002\"]", 1, "PASS");
        first.setInterviewerId(10003L);
        first.setDimensionScores("{\"PROFESSIONAL\":90,\"COMMUNICATION\":70}");
        List<Evaluation> stored = new ArrayList<>();
        stored.add(first);
        when(evaluationMapper.selectByInterviewAndInterviewer(90001L, 1, 10002L)).thenReturn(null);
        when(evaluationMapper.insert(any(Evaluation.class))).thenAnswer(invocation -> {
            Evaluation evaluation = invocation.getArgument(0);
            evaluation.setId(91002L);
            stored.add(evaluation);
            return 1;
        });
        when(evaluationMapper.selectByInterviewId(90001L)).thenReturn(stored);

        EvaluationView view = service.submit(90001L, form("PASS", "90", "80"), null);

        assertThat(view.totalScore()).isEqualByComparingTo("86.0");
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(eq(EventTypes.INTERVIEW_RESULT_SUBMITTED),
                eq("90001"), eq("interview"), payload.capture());
        InterviewResultSubmittedPayload event = (InterviewResultSubmittedPayload) payload.getValue();
        assertThat(event.result()).isEqualTo("PASS");
        assertThat(event.totalScore()).isEqualByComparingTo("84.0");
        assertThat(event.roundNo()).isEqualTo(1);
    }

    @Test
    void myTasksMapsPendingAndSubmittedRows() {
        stubTemplate();
        when(evaluationMapper.selectMyTasks(eq(10002L), eq("10002"), any(), any()))
                .thenReturn(List.of(taskRow(null, null), taskRow("SUBMITTED", "86.0")));

        List<MyEvaluationTaskView> tasks = service.myTasks(LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31));

        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).status()).isEqualTo("PENDING");
        assertThat(tasks.get(0).totalScore()).isNull();
        assertThat(tasks.get(1).status()).isEqualTo("SUBMITTED");
        assertThat(tasks.get(1).totalScore()).isEqualByComparingTo("86.0");
    }

    @Test
    void comparisonComputesAverageDivergenceAndSuggestion() {
        stubInterview("[\"10002\",\"10003\",\"10004\"]");
        stubTemplate();
        Evaluation pass = submitted(91001L, "[\"10002\"]", 1, "PASS");
        pass.setInterviewerId(10002L);
        pass.setDimensionScores("{\"PROFESSIONAL\":90,\"COMMUNICATION\":90}");
        Evaluation pass2 = submitted(91002L, "[\"10003\"]", 1, "PASS");
        pass2.setInterviewerId(10003L);
        pass2.setDimensionScores("{\"PROFESSIONAL\":80,\"COMMUNICATION\":80}");
        Evaluation fail = submitted(91003L, "[\"10004\"]", 1, "FAIL");
        fail.setInterviewerId(10004L);
        fail.setDimensionScores("{\"PROFESSIONAL\":60,\"COMMUNICATION\":60}");
        when(evaluationMapper.selectByInterviewId(90001L)).thenReturn(List.of(pass, pass2, fail));

        EvaluationComparisonView view = service.comparison(90001L);

        assertThat(view.dimensions()).hasSize(2);
        assertThat(view.dimensions().get(0).average()).isEqualByComparingTo("76.7");
        assertThat(view.dimensions().get(0).scores()).containsExactly(
                BigDecimal.valueOf(90), BigDecimal.valueOf(80), BigDecimal.valueOf(60));
        assertThat(view.divergence()).isEqualByComparingTo("30.0");
        assertThat(view.suggestion()).isEqualTo("PASS");
        assertThat(view.evaluations()).hasSize(3);
    }

    @Test
    void comparisonRejectsWhenNoSubmittedEvaluation() {
        stubInterview("[\"10002\"]");
        when(evaluationMapper.selectByInterviewId(90001L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.comparison(90001L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void aiDraftRejectsInsufficientTranscript() {
        stubInterview("[\"10002\"]");
        InterviewMinutes minutes = new InterviewMinutes();
        minutes.setInterviewId(90001L);
        minutes.setTranscriptSegmentCount(0);
        when(minutesMapper.selectByInterviewId(90001L)).thenReturn(minutes);

        assertThatThrownBy(() -> service.aiDraft(90001L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_TRANSCRIPT);
    }

    @Test
    void aiDraftReturnsSuggestionsEvidenceAndRisks() {
        stubInterview("[\"10002\"]");
        InterviewMinutes minutes = new InterviewMinutes();
        minutes.setInterviewId(90001L);
        minutes.setTranscriptSegmentCount(12);
        minutes.setModel("mock-model");
        minutes.setUpdatedAt(LocalDateTime.of(2026, 10, 20, 3, 0));
        minutes.setAiEvaluation("{\"dimensionSuggestions\":[{\"dimCode\":\"PROFESSIONAL\","
                + "\"dimName\":\"专业能力\",\"suggestedScore\":85,\"reason\":\"基础扎实\"}],"
                + "\"evidence\":[\"项目经历完整\"],\"risks\":[\"稳定性待确认\"]}");
        when(minutesMapper.selectByInterviewId(90001L)).thenReturn(minutes);

        AiDraftView view = service.aiDraft(90001L);

        assertThat(view.dimensionSuggestions()).hasSize(1);
        assertThat(view.dimensionSuggestions().get(0).suggestedScore()).isEqualByComparingTo("85");
        assertThat(view.evidence()).containsExactly("项目经历完整");
        assertThat(view.risks()).containsExactly("稳定性待确认");
        assertThat(view.transcriptSegmentCount()).isEqualTo(12);
        assertThat(view.generatedAt()).isNotNull();
    }

    @Test
    void reopenRejectsNonHrAdmin() {
        UserContext.set("10002", List.of("INTERVIEWER"));

        EvaluationReopenForm form = new EvaluationReopenForm();
        form.setReason("材料有误");
        form.setVersion(0);

        assertThatThrownBy(() -> service.reopen(91001L, form))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_FORBIDDEN);
        verify(evaluationMapper, never()).reopen(anyLong(), anyLong(), any(), anyInt());
    }

    @Test
    void reopenRecordsReasonAndReturnsNewVersion() {
        UserContext.set("10001", List.of("HR_ADMIN"));
        stubInterview("[\"10002\"]");
        stubTemplate();
        Evaluation current = submitted(91001L, "[\"10002\"]", 0, "PASS");
        Evaluation reopened = submitted(91001L, "[\"10002\"]", 1, "PASS");
        reopened.setStatus("REOPENED");
        reopened.setReopenedBy(10001L);
        reopened.setReopenReason("材料有误");
        when(evaluationMapper.selectById(91001L)).thenReturn(current, reopened);
        when(evaluationMapper.reopen(91001L, 10001L, "材料有误", 0)).thenReturn(1);

        EvaluationReopenForm form = new EvaluationReopenForm();
        form.setReason(" 材料有误 ");
        form.setVersion(0);

        EvaluationView view = service.reopen(91001L, form);

        assertThat(view.status()).isEqualTo("REOPENED");
        assertThat(view.version()).isEqualTo(1);
        assertThat(view.reopenedBy()).isEqualTo("10001");
        verify(evaluationMapper).reopen(91001L, 10001L, "材料有误", 0);
    }

    private void stubInterview(String interviewerIds) {
        InterviewRow row = new InterviewRow();
        row.setId(90001L);
        row.setApplicationId(51001L);
        row.setRoundNo(1);
        row.setInterviewerIds(interviewerIds);
        row.setJobId(50001L);
        row.setJobTitle("Java 开发工程师");
        row.setPersonName("张三");
        row.setStatus("SCHEDULED");
        row.setStartTime(LocalDateTime.of(2026, 10, 20, 1, 0));
        row.setEndTime(LocalDateTime.of(2026, 10, 20, 2, 0));
        row.setDeptId(30001L);
        row.setOwnerHrId(10001L);
        when(interviewMapper.selectRowById(90001L)).thenReturn(row);
        when(dataScopeService.current()).thenReturn(
                new DataScopeService.DataScope("ALL", null, List.of(), 10002L));
    }

    private void stubTemplate() {
        JobStageTemplate template = new JobStageTemplate();
        template.setId(80002L);
        template.setJobId(50001L);
        template.setTemplateVer(2);
        template.setStatus("ACTIVE");
        when(templateMapper.selectByJobIdAndVer(50001L, 2)).thenReturn(template);
        when(dimensionMapper.selectByTemplateId(80002L)).thenReturn(List.of(
                dimension("PROFESSIONAL", "专业能力", "0.6", "100", "60"),
                dimension("COMMUNICATION", "沟通表达", "0.4", "100", "60")));
    }

    private static EvaluationSubmitForm form(String result, String professional, String communication) {
        EvaluationSubmitForm form = new EvaluationSubmitForm();
        Map<String, BigDecimal> scores = new LinkedHashMap<>();
        scores.put("PROFESSIONAL", new BigDecimal(professional));
        scores.put("COMMUNICATION", new BigDecimal(communication));
        form.setDimensionScores(scores);
        form.setComment("技术基础扎实");
        form.setResult(result);
        form.setTemplateVersion(2);
        form.setVersion(0);
        return form;
    }

    private static EvaluationDimension dimension(String code, String name, String weight,
                                                 String fullScore, String veto) {
        EvaluationDimension dim = new EvaluationDimension();
        dim.setDimCode(code);
        dim.setDimName(name);
        dim.setWeight(new BigDecimal(weight));
        dim.setFullScore(new BigDecimal(fullScore));
        dim.setVetoThreshold(new BigDecimal(veto));
        return dim;
    }

    private static Evaluation submitted(long id, String interviewerIds, int version, String result) {
        Evaluation evaluation = new Evaluation();
        evaluation.setId(id);
        evaluation.setInterviewId(90001L);
        evaluation.setRoundNo(1);
        evaluation.setInterviewerId(10002L);
        evaluation.setDimensionScores("{\"PROFESSIONAL\":90,\"COMMUNICATION\":80}");
        evaluation.setResult(result);
        evaluation.setStatus("SUBMITTED");
        evaluation.setTemplateVersion(2);
        evaluation.setSubmittedAt(LocalDateTime.of(2026, 10, 20, 3, 0));
        evaluation.setVersion(version);
        return evaluation;
    }

    private static Evaluation reopened(long id, int version) {
        Evaluation evaluation = submitted(id, "[\"10002\"]", version, "PASS");
        evaluation.setStatus("REOPENED");
        evaluation.setReopenedBy(10001L);
        evaluation.setReopenReason("材料有误");
        return evaluation;
    }

    private static MyEvaluationRow taskRow(String status, String total) {
        MyEvaluationRow row = new MyEvaluationRow();
        row.setInterviewId(90001L);
        row.setApplicationId(51001L);
        row.setRoundNo(1);
        row.setJobId(50001L);
        row.setJobTitle("Java 开发工程师");
        row.setPersonName("张三");
        row.setStartTime(LocalDateTime.of(2026, 10, 20, 1, 0));
        row.setEndTime(LocalDateTime.of(2026, 10, 20, 2, 0));
        row.setInterviewStatus("SCHEDULED");
        row.setEvaluationId(status == null ? null : 91001L);
        row.setEvaluationStatus(status);
        row.setTemplateVersion(2);
        row.setDimensionScores(total == null ? null : "{\"PROFESSIONAL\":90,\"COMMUNICATION\":80}");
        return row;
    }
}
