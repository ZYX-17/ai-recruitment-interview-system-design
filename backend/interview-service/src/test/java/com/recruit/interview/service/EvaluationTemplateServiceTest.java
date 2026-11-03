package com.recruit.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.interview.dto.EvaluationTemplateCreateForm;
import com.recruit.interview.dto.EvaluationTemplateUpdateForm;
import com.recruit.interview.dto.EvaluationTemplateView;
import com.recruit.interview.dto.ProcessTemplateCreateForm;
import com.recruit.interview.dto.ProcessTemplateUpdateForm;
import com.recruit.interview.dto.ProcessTemplateView;
import com.recruit.interview.dto.ScoreDim;
import com.recruit.interview.dto.StageDef;
import com.recruit.interview.entity.EvaluationDimension;
import com.recruit.interview.entity.JobStageTemplate;
import com.recruit.interview.mapper.EvaluationDimensionMapper;
import com.recruit.interview.mapper.EvaluationMapper;
import com.recruit.interview.mapper.JobStageTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评价模板（P6-1）：维度以 evaluation_dimension 为准，版本化委托流程模板。
 */
@ExtendWith(MockitoExtension.class)
class EvaluationTemplateServiceTest {

    @Mock
    private ProcessTemplateService processTemplateService;
    @Mock
    private JobStageTemplateMapper templateMapper;
    @Mock
    private EvaluationDimensionMapper dimensionMapper;
    @Mock
    private EvaluationMapper evaluationMapper;

    private EvaluationTemplateService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationTemplateService(processTemplateService, templateMapper,
                dimensionMapper, evaluationMapper, new ObjectMapper());
    }

    @Test
    void detailReadsDimensionsFromEvaluationDimension() {
        when(processTemplateService.detail(90002L)).thenReturn(view());
        when(dimensionMapper.selectByTemplateId(90002L)).thenReturn(List.of(
                dimension("PROFESSIONAL", "专业能力", "0.6", "100"),
                dimension("COMMUNICATION", "沟通表达", "0.4", "100")));

        EvaluationTemplateView view = service.detail(90002L);

        assertThat(view.id()).isEqualTo("90002");
        assertThat(view.dimensions()).hasSize(2);
        assertThat(view.dimensions().get(0).getDimCode()).isEqualTo("PROFESSIONAL");
        assertThat(view.dimensions().get(0).getWeight()).isEqualByComparingTo("0.6");
        assertThat(view.templateVer()).isEqualTo(2);
    }

    @Test
    void createInheritsActiveStageFlowWhenOmitted() {
        JobStageTemplate active = new JobStageTemplate();
        active.setStageFlow("[{\"stageNo\":\"SCREEN\",\"stageName\":\"初筛\",\"mode\":\"ONLINE\",\"rounds\":1}]");
        when(templateMapper.selectActiveByJobId(50001L)).thenReturn(active);
        when(processTemplateService.create(any(ProcessTemplateCreateForm.class), eq("k1")))
                .thenReturn(view());
        when(dimensionMapper.selectByTemplateId(90002L)).thenReturn(List.of(
                dimension("PROFESSIONAL", "专业能力", "1.0", "100")));

        EvaluationTemplateCreateForm form = new EvaluationTemplateCreateForm();
        form.setJobId("50001");
        form.setScoreDims(List.of(scoreDim("PROFESSIONAL", "专业能力", "1.0")));

        EvaluationTemplateView created = service.create(form, "k1");

        ArgumentCaptor<ProcessTemplateCreateForm> captor =
                ArgumentCaptor.forClass(ProcessTemplateCreateForm.class);
        verify(processTemplateService).create(captor.capture(), eq("k1"));
        assertThat(captor.getValue().getStageFlow()).hasSize(1);
        assertThat(captor.getValue().getStageFlow().get(0).getStageNo()).isEqualTo("SCREEN");
        assertThat(created.id()).isEqualTo("90002");
    }

    @Test
    void createRejectsWhenJobHasNoActiveTemplate() {
        when(templateMapper.selectActiveByJobId(50001L)).thenReturn(null);

        EvaluationTemplateCreateForm form = new EvaluationTemplateCreateForm();
        form.setJobId("50001");
        form.setScoreDims(List.of(scoreDim("PROFESSIONAL", "专业能力", "1.0")));

        assertThatThrownBy(() -> service.create(form, null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
        verify(processTemplateService, never()).create(any(ProcessTemplateCreateForm.class), any());
    }

    @Test
    void updateKeepsCurrentStageFlowWhenOmitted() {
        when(processTemplateService.detail(90002L)).thenReturn(view());
        when(processTemplateService.update(eq(90002L), any(ProcessTemplateUpdateForm.class)))
                .thenReturn(view());
        when(dimensionMapper.selectByTemplateId(90002L)).thenReturn(List.of(
                dimension("PROFESSIONAL", "专业能力", "1.0", "100")));

        EvaluationTemplateUpdateForm form = new EvaluationTemplateUpdateForm();
        form.setScoreDims(List.of(scoreDim("PROFESSIONAL", "专业能力", "1.0")));
        form.setVersion(0);

        service.update(90002L, form);

        ArgumentCaptor<ProcessTemplateUpdateForm> captor =
                ArgumentCaptor.forClass(ProcessTemplateUpdateForm.class);
        verify(processTemplateService).update(eq(90002L), captor.capture());
        assertThat(captor.getValue().getStageFlow()).hasSize(1);
        assertThat(captor.getValue().getVersion()).isZero();
    }

    @Test
    void deleteRejectsWhenHistoricalEvaluationReferencesTemplate() {
        when(processTemplateService.detail(90002L)).thenReturn(view());
        when(evaluationMapper.countByJobAndTemplateVer(50001L, 2)).thenReturn(3L);

        assertThatThrownBy(() -> service.delete(90002L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
        verify(processTemplateService, never()).delete(90002L);
    }

    @Test
    void deleteDelegatesWhenTemplateNotReferenced() {
        when(processTemplateService.detail(90002L)).thenReturn(view());
        when(evaluationMapper.countByJobAndTemplateVer(50001L, 2)).thenReturn(0L);

        service.delete(90002L);

        verify(processTemplateService).delete(90002L);
    }

    private static ProcessTemplateView view() {
        StageDef stage = new StageDef();
        stage.setStageNo("SCREEN");
        stage.setStageName("初筛");
        stage.setMode("ONLINE");
        stage.setRounds(1);
        ScoreDim dim = scoreDim("PROFESSIONAL", "专业能力", "1.0");
        return new ProcessTemplateView("90002", "50001", 2, List.of(stage), List.of(dim),
                "ACTIVE", "10001", null, 0);
    }

    private static EvaluationDimension dimension(String code, String name, String weight,
                                                 String fullScore) {
        EvaluationDimension dim = new EvaluationDimension();
        dim.setDimCode(code);
        dim.setDimName(name);
        dim.setWeight(new BigDecimal(weight));
        dim.setFullScore(new BigDecimal(fullScore));
        return dim;
    }

    private static ScoreDim scoreDim(String code, String name, String weight) {
        ScoreDim dim = new ScoreDim();
        dim.setDimCode(code);
        dim.setDimName(name);
        dim.setWeight(new BigDecimal(weight));
        dim.setFullScore(BigDecimal.valueOf(100));
        return dim;
    }
}
