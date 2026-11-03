package com.recruit.interview.service;

import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import com.recruit.interview.dto.PortalInterviewJoinView;
import com.recruit.interview.dto.PortalInterviewPageQuery;
import com.recruit.interview.dto.PortalInterviewView;
import com.recruit.interview.mapper.InterviewMapper;
import com.recruit.interview.mapper.row.InterviewRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 门户“我的面试”与进房校验。
 */
@ExtendWith(MockitoExtension.class)
class PortalInterviewServiceTest {

    @Mock
    private InterviewMapper interviewMapper;

    private PortalInterviewService service;

    @BeforeEach
    void setUp() {
        service = new PortalInterviewService(interviewMapper, 10, 15, "http://localhost:5173/room/");
        UserContext.set("10006", List.of("CANDIDATE"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void pageMapsOwnInterviews() {
        when(interviewMapper.countPortalPage(10006L, "SCHEDULED")).thenReturn(1L);
        when(interviewMapper.selectPortalPage(10006L, "SCHEDULED", 20, 0L))
                .thenReturn(List.of(row("SCHEDULED", 70001L,
                        LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5))));
        PortalInterviewPageQuery query = new PortalInterviewPageQuery();
        query.setStatus("scheduled");

        PageResult<PortalInterviewView> page = service.page(query);

        assertThat(page.getTotal()).isEqualTo(1);
        PortalInterviewView view = page.getList().get(0);
        assertThat(view.id()).isEqualTo("90001");
        assertThat(view.roomId()).isEqualTo("70001");
        assertThat(view.joinable()).isTrue();
    }

    @Test
    void detailRejectsInterviewOfAnotherCandidate() {
        when(interviewMapper.selectPortalRowById(90001L, 10006L)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(90001L))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void joinReturnsRoomWhenWithinWindow() {
        when(interviewMapper.selectPortalRowById(90001L, 10006L)).thenReturn(row("SCHEDULED", 70001L,
                LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5)));

        PortalInterviewJoinView join = service.join(90001L);

        assertThat(join.roomId()).isEqualTo("70001");
        assertThat(join.joinUrl()).isEqualTo("http://localhost:5173/room/70001");
    }

    @Test
    void joinRejectsWhenRoomNotCreated() {
        when(interviewMapper.selectPortalRowById(90001L, 10006L)).thenReturn(row("ROOM_PENDING", null,
                LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5)));

        assertThatThrownBy(() -> service.join(90001L))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ILLEGAL_TRANSITION));
    }

    @Test
    void joinRejectsBeforeWindow() {
        when(interviewMapper.selectPortalRowById(90001L, 10006L)).thenReturn(row("SCHEDULED", 70001L,
                LocalDateTime.now(ZoneOffset.UTC).plusHours(1)));

        assertThatThrownBy(() -> service.join(90001L))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_OPEN));
    }

    @Test
    void joinRejectsCompletedInterview() {
        when(interviewMapper.selectPortalRowById(90001L, 10006L)).thenReturn(row("COMPLETED", 70001L,
                LocalDateTime.now(ZoneOffset.UTC).minusHours(2)));

        assertThatThrownBy(() -> service.join(90001L))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ILLEGAL_TRANSITION));
    }

    @Test
    void nonCandidateCannotJoin() {
        UserContext.set("10001", List.of("HR_ADMIN"));

        assertThatThrownBy(() -> service.join(90001L))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_FORBIDDEN));
        verify(interviewMapper, never()).selectPortalRowById(anyLong(), anyLong());
    }

    private static InterviewRow row(String status, Long roomId, LocalDateTime start) {
        InterviewRow row = new InterviewRow();
        row.setId(90001L);
        row.setApplicationId(51001L);
        row.setJobId(50001L);
        row.setJobTitle("Java 开发工程师");
        row.setPersonId(10006L);
        row.setRoundNo(1);
        row.setStatus(status);
        row.setRoomId(roomId);
        row.setMode("ONLINE");
        row.setRoomType("ONE_TO_ONE");
        row.setStartTime(start);
        row.setEndTime(start.plusHours(1));
        return row;
    }
}
