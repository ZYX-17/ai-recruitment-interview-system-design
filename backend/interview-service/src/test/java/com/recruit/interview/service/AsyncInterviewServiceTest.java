package com.recruit.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.idempotency.IdempotencyService;
import com.recruit.common.result.ErrorCode;
import com.recruit.interview.dto.AsyncAiResultForm;
import com.recruit.interview.dto.AsyncPaperCreateForm;
import com.recruit.interview.dto.AsyncPaperUpdateForm;
import com.recruit.interview.dto.AsyncPaperView;
import com.recruit.interview.dto.AsyncPublishView;
import com.recruit.interview.dto.AsyncQuestionCreateForm;
import com.recruit.interview.dto.AsyncReviewForm;
import com.recruit.interview.entity.AsyncAnswerScore;
import com.recruit.interview.entity.AsyncPaper;
import com.recruit.interview.entity.AsyncQuestion;
import com.recruit.interview.entity.Application;
import com.recruit.interview.entity.Job;
import com.recruit.interview.mapper.ApplicationMapper;
import com.recruit.interview.mapper.AsyncAnswerMapper;
import com.recruit.interview.mapper.AsyncAnswerScoreMapper;
import com.recruit.interview.mapper.AsyncPaperMapper;
import com.recruit.interview.mapper.AsyncQuestionMapper;
import com.recruit.interview.mapper.JobMapper;
import com.recruit.interview.mapper.row.AsyncAnswerRow;
import com.recruit.interview.mapper.row.AsyncPaperRow;
import com.recruit.interview.support.SecretToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 异步试卷创建/发布/复核/重置与 AI 回写（P9-1~P9-4、P9-7）。
 */
@ExtendWith(MockitoExtension.class)
class AsyncInterviewServiceTest {

    @Mock
    private AsyncPaperMapper paperMapper;
    @Mock
    private AsyncQuestionMapper questionMapper;
    @Mock
    private AsyncAnswerMapper answerMapper;
    @Mock
    private AsyncAnswerScoreMapper scoreMapper;
    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private AsyncInterviewService service;

    @BeforeEach
    void setUp() {
        service = new AsyncInterviewService(paperMapper, questionMapper, answerMapper, scoreMapper,
                applicationMapper, jobMapper, dataScopeService, idempotencyService,
                redisTemplate, new ObjectMapper());
        UserContext.set("10001", List.of("HR_ADMIN"), "HR", "trace");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createPersistsDraftPaper() {
        Application application = new Application();
        application.setId(51001L);
        application.setJobId(50001L);
        when(applicationMapper.selectById(51001L)).thenReturn(application);
        Job job = new Job();
        job.setId(50001L);
        job.setDeptId(20001L);
        job.setOwnerHrId(10001L);
        when(jobMapper.selectById(50001L)).thenReturn(job);
        when(dataScopeService.current()).thenReturn(allScope());
        when(paperMapper.selectRowByApplicationId(51001L)).thenReturn(null);
        when(paperMapper.insert(any(AsyncPaper.class))).thenAnswer(invocation -> {
            AsyncPaper paper = invocation.getArgument(0);
            paper.setId(93001L);
            return 1;
        });
        when(paperMapper.selectRowById(93001L)).thenReturn(paperRow(93001L, "DRAFT", 0));
        when(questionMapper.selectByPaperId(93001L)).thenReturn(List.of());

        AsyncPaperView view = service.create(createForm(), null);

        ArgumentCaptor<AsyncPaper> captor = ArgumentCaptor.forClass(AsyncPaper.class);
        verify(paperMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("DRAFT");
        assertThat(captor.getValue().getPaperNo()).startsWith("ASYNC-");
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(10001L);
        assertThat(view.status()).isEqualTo("DRAFT");
    }

    @Test
    void createRejectsDuplicateApplicationPaper() {
        Application application = new Application();
        application.setId(51001L);
        application.setJobId(50001L);
        when(applicationMapper.selectById(51001L)).thenReturn(application);
        Job job = new Job();
        job.setId(50001L);
        job.setDeptId(20001L);
        job.setOwnerHrId(10001L);
        when(jobMapper.selectById(50001L)).thenReturn(job);
        when(dataScopeService.current()).thenReturn(allScope());
        when(paperMapper.selectRowByApplicationId(51001L)).thenReturn(paperRow(93002L, "DRAFT", 0));

        assertThatThrownBy(() -> service.create(createForm(), null))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARAM_INVALID);
        verify(paperMapper, never()).insert(any(AsyncPaper.class));
    }

    @Test
    void updateRejectsReleasedPaper() {
        when(paperMapper.selectRowById(93001L)).thenReturn(paperRow(93001L, "RELEASED", 1));
        when(dataScopeService.current()).thenReturn(allScope());
        AsyncPaperUpdateForm form = new AsyncPaperUpdateForm();
        form.setVersion(1);

        assertThatThrownBy(() -> service.update(93001L, form))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ILLEGAL_TRANSITION);
    }

    @Test
    void addQuestionRejectsDuplicateSeq() {
        when(paperMapper.selectRowById(93001L)).thenReturn(paperRow(93001L, "DRAFT", 0));
        when(dataScopeService.current()).thenReturn(allScope());
        when(questionMapper.insert(any(AsyncQuestion.class)))
                .thenThrow(new DuplicateKeyException("uk_paper_seq"));
        AsyncQuestionCreateForm form = new AsyncQuestionCreateForm();
        form.setSeqNo(1);
        form.setQuestionText("请介绍一次性能优化。");

        assertThatThrownBy(() -> service.addQuestion(93001L, form, null))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ILLEGAL_TRANSITION);
    }

    @Test
    void publishGeneratesHashedInviteTokenAndCachesPlainUrl() {
        AsyncPaperRow draft = paperRow(93001L, "DRAFT", 0);
        AsyncPaperRow released = paperRow(93001L, "RELEASED", 1);
        released.setInviteExpireAt(LocalDateTime.now().plusDays(7));
        when(paperMapper.selectRowById(93001L)).thenReturn(draft, released);
        when(dataScopeService.current()).thenReturn(allScope());
        when(questionMapper.countByPaperId(93001L)).thenReturn(2L);
        when(questionMapper.selectByPaperId(93001L)).thenReturn(List.of());
        when(paperMapper.publish(eq(93001L), anyString(), any(LocalDateTime.class), eq(0))).thenReturn(1);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doNothing().when(valueOperations).set(anyString(), anyString(), any());

        AsyncPublishView view = service.publish(93001L, 0);

        assertThat(view.inviteUrl()).startsWith("http://localhost:5173/portal/async-interview?token=");
        String token = view.inviteUrl().substring(view.inviteUrl().indexOf("token=") + 6);
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(paperMapper).publish(eq(93001L), hashCaptor.capture(), any(LocalDateTime.class), eq(0));
        assertThat(hashCaptor.getValue()).isEqualTo(SecretToken.sha256Hex(token)).hasSize(64);
        verify(valueOperations).set(anyString(), eq(view.inviteUrl()), any());
    }

    @Test
    void publishRejectsPaperWithoutQuestions() {
        when(paperMapper.selectRowById(93001L)).thenReturn(paperRow(93001L, "DRAFT", 0));
        when(dataScopeService.current()).thenReturn(allScope());
        when(questionMapper.countByPaperId(93001L)).thenReturn(0L);

        assertThatThrownBy(() -> service.publish(93001L, 0))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARAM_INVALID);
        verify(paperMapper, never()).publish(anyLong(), anyString(), any(), anyInt());
    }

    @Test
    void reviewUsesManualScoreAndMarksPaperScored() {
        AsyncPaperRow paper = paperRow(93001L, "SUBMITTED", 3);
        when(paperMapper.selectRowById(93001L)).thenReturn(paper);
        when(dataScopeService.current()).thenReturn(allScope());
        AsyncAnswerRow answer = answerRow(94001L, 93001L, 95001L, "SUBMITTED", 0);
        AsyncAnswerRow reviewed = answerRow(94001L, 93001L, 95001L, "REVIEWED", 1);
        reviewed.setManualScore(new BigDecimal("90.00"));
        when(answerMapper.selectRowById(94001L)).thenReturn(answer, reviewed);
        when(answerMapper.updateManual(eq(94001L), any(BigDecimal.class), any(), eq(10001L),
                any(LocalDateTime.class), eq(0))).thenReturn(1);

        AsyncQuestion q1 = question(95001L, new BigDecimal("1.00"));
        AsyncQuestion q2 = question(95002L, new BigDecimal("3.00"));
        when(questionMapper.selectByPaperId(93001L)).thenReturn(List.of(q1, q2));
        AsyncAnswerRow a1 = answerRow(94001L, 93001L, 95001L, "REVIEWED", 1);
        a1.setManualScore(new BigDecimal("90.00"));
        AsyncAnswerRow a2 = answerRow(94002L, 93001L, 95002L, "AI_SCORED", 0);
        a2.setAiScore(new BigDecimal("70.00"));
        when(answerMapper.selectByPaperId(93001L)).thenReturn(List.of(a1, a2));
        when(paperMapper.markScored(eq(93001L), any(BigDecimal.class), eq(10001L),
                any(LocalDateTime.class), eq(3))).thenReturn(1);
        AsyncReviewForm form = new AsyncReviewForm();
        form.setManualScore(new BigDecimal("90.00"));
        form.setVersion(0);
        service.review(93001L, 94001L, form);

        ArgumentCaptor<BigDecimal> total = ArgumentCaptor.forClass(BigDecimal.class);
        verify(paperMapper).markScored(eq(93001L), total.capture(), eq(10001L),
                any(LocalDateTime.class), eq(3));
        assertThat(total.getValue()).isEqualByComparingTo("75.00");
    }

    @Test
    void resetDeletesAnswersAndReturnsReleasedPaper() {
        when(dataScopeService.current()).thenReturn(allScope());
        when(paperMapper.reset(93001L, 5)).thenReturn(1);
        when(paperMapper.selectRowById(93001L)).thenReturn(paperRow(93001L, "SCORED", 5),
                paperRow(93001L, "RELEASED", 6));

        AsyncPaperView view = service.reset(93001L, 5);

        verify(answerMapper).deleteByPaperId(93001L);
        assertThat(view.status()).isEqualTo("RELEASED");
    }

    @Test
    void aiResultWritesTranscriptAndScore() {
        AsyncAnswerRow answer = answerRow(94001L, 93001L, 95001L, "SUBMITTED", 0);
        AsyncAnswerRow scored = answerRow(94001L, 93001L, 95001L, "AI_SCORED", 1);
        scored.setAiScore(new BigDecimal("88.00"));
        when(answerMapper.selectRowById(94001L)).thenReturn(answer, scored);
        when(paperMapper.selectRowById(93001L)).thenReturn(paperRow(93001L, "SUBMITTED", 2));
        when(scoreMapper.selectByAnswerId(94001L)).thenReturn(null);
        when(scoreMapper.insert(any(AsyncAnswerScore.class))).thenReturn(1);
        when(answerMapper.updateAiResult(eq(94001L), anyString(), any(BigDecimal.class),
                eq("AI_SCORED"), eq(0))).thenReturn(1);
        when(questionMapper.selectByPaperId(93001L)).thenReturn(List.of(question(95001L, BigDecimal.ONE)));
        when(answerMapper.selectByPaperId(93001L)).thenReturn(List.of(scored));
        when(paperMapper.markScored(eq(93001L), any(BigDecimal.class), isNull(),
                any(LocalDateTime.class), eq(2))).thenReturn(1);

        AsyncAiResultForm form = new AsyncAiResultForm();
        form.setTranscriptText("转写内容");
        form.setAiScore(new BigDecimal("88.00"));
        form.setModel("mock-asr");
        assertThat(service.applyAiResult(94001L, form)).isNotNull();
        verify(scoreMapper).insert(any(AsyncAnswerScore.class));
        verify(paperMapper).markScored(eq(93001L), any(BigDecimal.class), isNull(),
                any(LocalDateTime.class), eq(2));
    }

    private static AsyncPaperCreateForm createForm() {
        AsyncPaperCreateForm form = new AsyncPaperCreateForm();
        form.setApplicationId("51001");
        form.setQuestionCount(2);
        form.setDurationLimitSec(1800);
        form.setSettings(Map.of("recordVideo", true));
        return form;
    }

    private static DataScopeService.DataScope allScope() {
        return new DataScopeService.DataScope("ALL", null, List.of(), 10001L);
    }

    private static AsyncPaperRow paperRow(long id, String status, int version) {
        AsyncPaperRow row = new AsyncPaperRow();
        row.setId(id);
        row.setPaperNo("ASYNC-20261103-ABC123");
        row.setApplicationId(51001L);
        row.setJobId(50001L);
        row.setJobTitle("Java 工程师");
        row.setPersonId(30001L);
        row.setPersonName("张三");
        row.setStatus(status);
        row.setQuestionCount(1);
        row.setDurationLimitSec(1800);
        row.setDeptId(20001L);
        row.setOwnerHrId(10001L);
        row.setVersion(version);
        row.setCreatedAt(LocalDateTime.now());
        return row;
    }

    private static AsyncAnswerRow answerRow(long id, long paperId, long questionId,
                                            String status, int version) {
        AsyncAnswerRow row = new AsyncAnswerRow();
        row.setId(id);
        row.setPaperId(paperId);
        row.setQuestionId(questionId);
        row.setSeqNo(1);
        row.setQuestionText("题目");
        row.setWeight(BigDecimal.ONE);
        row.setStatus(status);
        row.setVersion(version);
        row.setCandidateId(30001L);
        return row;
    }

    private static AsyncQuestion question(long id, BigDecimal weight) {
        AsyncQuestion question = new AsyncQuestion();
        question.setId(id);
        question.setPaperId(93001L);
        question.setSeqNo(1);
        question.setQuestionText("题目");
        question.setPrepareSec(60);
        question.setAnswerSec(180);
        question.setWeight(weight);
        return question;
    }
}
