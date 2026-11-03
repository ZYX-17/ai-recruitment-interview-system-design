package com.recruit.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.exception.BizException;
import com.recruit.common.idempotency.IdempotencyService;
import com.recruit.common.result.ErrorCode;
import com.recruit.interview.dto.PortalAsyncAnswerForm;
import com.recruit.interview.dto.PortalAsyncSubmitForm;
import com.recruit.interview.dto.PortalAsyncView;
import com.recruit.interview.entity.AsyncAnswer;
import com.recruit.interview.entity.AsyncQuestion;
import com.recruit.interview.mapper.AsyncAnswerMapper;
import com.recruit.interview.mapper.AsyncPaperMapper;
import com.recruit.interview.mapper.AsyncQuestionMapper;
import com.recruit.interview.mapper.row.AsyncPaperRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 门户邀请查看/开始/提交（P9-3）。
 */
@ExtendWith(MockitoExtension.class)
class AsyncPortalServiceTest {

    @Mock
    private AsyncPaperMapper paperMapper;
    @Mock
    private AsyncQuestionMapper questionMapper;
    @Mock
    private AsyncAnswerMapper answerMapper;
    @Mock
    private IdempotencyService idempotencyService;

    private AsyncPortalService service;

    @BeforeEach
    void setUp() {
        service = new AsyncPortalService(paperMapper, questionMapper, answerMapper,
                idempotencyService, new ObjectMapper());
    }

    @Test
    void detailRejectsUnknownToken() {
        when(paperMapper.selectRowByTokenHash(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.detail("bad-token"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APP_NOT_FOUND);
    }

    @Test
    void detailRejectsExpiredInvite() {
        AsyncPaperRow row = paperRow("RELEASED", 0);
        row.setInviteExpireAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(paperMapper.selectRowByTokenHash(anyString())).thenReturn(row);

        assertThatThrownBy(() -> service.detail("token"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APP_NOT_FOUND);
    }

    @Test
    void startMovesReleasedToInProgress() {
        when(paperMapper.selectRowByTokenHash(anyString())).thenReturn(paperRow("RELEASED", 0));
        when(paperMapper.markStarted(eq(93001L), any(LocalDateTime.class), eq(0))).thenReturn(1);
        when(paperMapper.selectRowById(93001L)).thenReturn(paperRow("IN_PROGRESS", 1));
        when(questionMapper.selectByPaperId(93001L)).thenReturn(List.of(question(95001L)));

        PortalAsyncView view = service.start("token", null);

        assertThat(view.status()).isEqualTo("IN_PROGRESS");
        assertThat(view.questions()).hasSize(1);
    }

    @Test
    void startIsIdempotentWhenAlreadyInProgress() {
        when(paperMapper.selectRowByTokenHash(anyString())).thenReturn(paperRow("IN_PROGRESS", 1));
        when(questionMapper.selectByPaperId(93001L)).thenReturn(List.of(question(95001L)));

        PortalAsyncView view = service.start("token", null);

        assertThat(view.status()).isEqualTo("IN_PROGRESS");
        verify(paperMapper, never()).markStarted(anyLong(), any(), anyInt());
    }

    @Test
    void startRejectsSubmittedPaperWithAis5102() {
        when(paperMapper.selectRowByTokenHash(anyString())).thenReturn(paperRow("SUBMITTED", 2));

        assertThatThrownBy(() -> service.start("token", null))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ANSWER_SUBMITTED);
    }

    @Test
    void submitRejectsIncompleteAnswers() {
        when(paperMapper.selectRowByTokenHash(anyString())).thenReturn(paperRow("IN_PROGRESS", 1));
        when(questionMapper.selectByPaperId(93001L))
                .thenReturn(List.of(question(95001L), question(95002L)));

        PortalAsyncSubmitForm form = new PortalAsyncSubmitForm();
        form.setAnswers(List.of(answer("95001", "70001")));

        assertThatThrownBy(() -> service.submit("token", form, null))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARAM_INVALID);
        verify(answerMapper, never()).insert(any(AsyncAnswer.class));
    }

    @Test
    void submitPersistsAnswersAndMarksSubmitted() {
        when(paperMapper.selectRowByTokenHash(anyString())).thenReturn(paperRow("IN_PROGRESS", 1));
        when(questionMapper.selectByPaperId(93001L))
                .thenReturn(List.of(question(95001L), question(95002L)));
        when(answerMapper.insert(any(AsyncAnswer.class))).thenReturn(1);
        when(paperMapper.markSubmitted(eq(93001L), any(LocalDateTime.class), eq(1))).thenReturn(1);
        when(paperMapper.selectRowById(93001L)).thenReturn(paperRow("SUBMITTED", 2));

        PortalAsyncSubmitForm form = new PortalAsyncSubmitForm();
        form.setAnswers(List.of(answer("95001", "70001"), answer("95002", "70002")));

        PortalAsyncView view = service.submit("token", form, null);

        ArgumentCaptor<AsyncAnswer> captor = ArgumentCaptor.forClass(AsyncAnswer.class);
        verify(answerMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(answer -> {
            assertThat(answer.getCandidateId()).isEqualTo(30001L);
            assertThat(answer.getStatus()).isEqualTo("SUBMITTED");
            assertThat(answer.getVideoFileId()).isNotNull();
        });
        assertThat(view.status()).isEqualTo("SUBMITTED");
        assertThat(view.questions()).isEmpty();
    }

    @Test
    void submitTwiceReturnsAis5102() {
        when(paperMapper.selectRowByTokenHash(anyString())).thenReturn(paperRow("SUBMITTED", 2));

        PortalAsyncSubmitForm form = new PortalAsyncSubmitForm();
        form.setAnswers(List.of(answer("95001", "70001")));

        assertThatThrownBy(() -> service.submit("token", form, null))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ANSWER_SUBMITTED);
    }

    @Test
    void submitDuplicateAnswerReturnsAis5102() {
        when(paperMapper.selectRowByTokenHash(anyString())).thenReturn(paperRow("IN_PROGRESS", 1));
        when(questionMapper.selectByPaperId(93001L)).thenReturn(List.of(question(95001L)));
        when(answerMapper.insert(any(AsyncAnswer.class)))
                .thenThrow(new DuplicateKeyException("uk_question"));

        PortalAsyncSubmitForm form = new PortalAsyncSubmitForm();
        form.setAnswers(List.of(answer("95001", "70001")));

        assertThatThrownBy(() -> service.submit("token", form, null))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ANSWER_SUBMITTED);
    }

    @Test
    void submitRejectsAnswerWithoutMedia() {
        when(paperMapper.selectRowByTokenHash(anyString())).thenReturn(paperRow("IN_PROGRESS", 1));
        when(questionMapper.selectByPaperId(93001L)).thenReturn(List.of(question(95001L)));

        PortalAsyncSubmitForm form = new PortalAsyncSubmitForm();
        form.setAnswers(List.of(answer("95001", null)));

        assertThatThrownBy(() -> service.submit("token", form, null))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARAM_INVALID);
    }

    private static PortalAsyncAnswerForm answer(String questionId, String videoFileId) {
        PortalAsyncAnswerForm form = new PortalAsyncAnswerForm();
        form.setQuestionId(questionId);
        form.setVideoFileId(videoFileId);
        form.setAnswerSec(120);
        return form;
    }

    private static AsyncPaperRow paperRow(String status, int version) {
        AsyncPaperRow row = new AsyncPaperRow();
        row.setId(93001L);
        row.setPaperNo("ASYNC-20261103-ABC123");
        row.setApplicationId(51001L);
        row.setJobId(50001L);
        row.setJobTitle("Java 工程师");
        row.setPersonId(30001L);
        row.setPersonName("张三");
        row.setStatus(status);
        row.setQuestionCount(1);
        row.setDurationLimitSec(1800);
        row.setInviteExpireAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(7));
        row.setVersion(version);
        return row;
    }

    private static AsyncQuestion question(long id) {
        AsyncQuestion question = new AsyncQuestion();
        question.setId(id);
        question.setPaperId(93001L);
        question.setSeqNo(1);
        question.setQuestionText("题目");
        question.setPrepareSec(60);
        question.setAnswerSec(180);
        question.setWeight(BigDecimal.ONE);
        return question;
    }
}
