package com.recruit.interview.service;

import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.interview.dto.StageDef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 投递状态机（P4-6）。
 */
class ApplicationStateMachineTest {

    private final ApplicationStateMachine stateMachine = new ApplicationStateMachine();

    private static List<StageDef> flow() {
        return List.of(stage("SCREEN"), stage("FIRST"), stage("OFFER"));
    }

    private static StageDef stage(String code) {
        StageDef stage = new StageDef();
        stage.setStageNo(code);
        stage.setStageName(code);
        stage.setMode("ONLINE");
        stage.setRounds(1);
        return stage;
    }

    @Test
    void advanceMovesToNextStage() {
        ApplicationStateMachine.TransitionResult result = stateMachine.apply(
                ApplicationStateMachine.IN_PROGRESS, "SCREEN", "ADVANCE", null, flow());

        assertThat(result.status()).isEqualTo(ApplicationStateMachine.IN_PROGRESS);
        assertThat(result.stageNo()).isEqualTo("FIRST");
        assertThat(result.result()).isEqualTo(ApplicationStateMachine.RESULT_PASS);
    }

    @Test
    void advanceOnLastStagePassesApplication() {
        ApplicationStateMachine.TransitionResult result = stateMachine.apply(
                ApplicationStateMachine.IN_PROGRESS, "OFFER", "PASS", null, flow());

        assertThat(result.status()).isEqualTo(ApplicationStateMachine.PASSED);
        assertThat(result.stageNo()).isEqualTo("OFFER");
    }

    @Test
    void advanceCanJumpToExplicitLaterStage() {
        ApplicationStateMachine.TransitionResult result = stateMachine.apply(
                ApplicationStateMachine.IN_PROGRESS, "SCREEN", "ADVANCE", "OFFER", flow());

        assertThat(result.stageNo()).isEqualTo("OFFER");
    }

    @Test
    void advanceRejectsTargetStageNotAfterCurrent() {
        assertThatThrownBy(() -> stateMachine.apply(
                ApplicationStateMachine.IN_PROGRESS, "FIRST", "ADVANCE", "SCREEN", flow()))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ILLEGAL_TRANSITION);
    }

    @Test
    void rejectAndHoldProduceExpectedResults() {
        ApplicationStateMachine.TransitionResult rejected = stateMachine.apply(
                ApplicationStateMachine.IN_PROGRESS, "FIRST", "REJECT", null, flow());
        assertThat(rejected.status()).isEqualTo(ApplicationStateMachine.REJECTED);
        assertThat(rejected.result()).isEqualTo(ApplicationStateMachine.RESULT_FAIL);

        ApplicationStateMachine.TransitionResult held = stateMachine.apply(
                ApplicationStateMachine.IN_PROGRESS, "FIRST", "HOLD", null, flow());
        assertThat(held.status()).isEqualTo(ApplicationStateMachine.IN_PROGRESS);
        assertThat(held.stageNo()).isEqualTo("FIRST");
        assertThat(held.result()).isEqualTo(ApplicationStateMachine.RESULT_PENDING);
    }

    @Test
    void terminalStatusRejectsFurtherActions() {
        assertThatThrownBy(() -> stateMachine.apply(
                ApplicationStateMachine.REJECTED, "FIRST", "ADVANCE", null, flow()))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ILLEGAL_TRANSITION);
        assertThat(stateMachine.isTerminal(ApplicationStateMachine.HIRED)).isTrue();
        assertThat(stateMachine.isTerminal(ApplicationStateMachine.IN_PROGRESS)).isFalse();
    }

    @Test
    void transferMarksApplicationTransferred() {
        ApplicationStateMachine.TransitionResult result = stateMachine.apply(
                ApplicationStateMachine.IN_PROGRESS, "FIRST", "TRANSFER", null, flow());

        assertThat(result.status()).isEqualTo(ApplicationStateMachine.TRANSFERRED);
    }

    @Test
    void hireFromPassedIsAllowed() {
        ApplicationStateMachine.TransitionResult result = stateMachine.apply(
                ApplicationStateMachine.PASSED, "OFFER", "HIRE", null, flow());

        assertThat(result.status()).isEqualTo(ApplicationStateMachine.HIRED);
    }

    @Test
    void advanceWithoutFlowFails() {
        assertThatThrownBy(() -> stateMachine.apply(
                ApplicationStateMachine.IN_PROGRESS, "SCREEN", "ADVANCE", null, List.of()))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ILLEGAL_TRANSITION);
    }
}
