package com.recruit.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.idempotency.IdempotencyService;
import com.recruit.common.result.ErrorCode;
import com.recruit.interview.dto.ProcessTemplateCreateForm;
import com.recruit.interview.dto.ProcessTemplateUpdateForm;
import com.recruit.interview.dto.ProcessTemplateView;
import com.recruit.interview.dto.ScoreDim;
import com.recruit.interview.dto.StageDef;
import com.recruit.interview.entity.EvaluationDimension;
import com.recruit.interview.entity.Job;
import com.recruit.interview.entity.JobStageTemplate;
import com.recruit.interview.mapper.ApplicationMapper;
import com.recruit.interview.mapper.EvaluationDimensionMapper;
import com.recruit.interview.mapper.JobMapper;
import com.recruit.interview.mapper.JobStageTemplateMapper;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程模板版本化（P4-3）。
 */
@ExtendWith(MockitoExtension.class)
class ProcessTemplateServiceTest {

    @Mock
    private JobMapper jobMapper;
    @Mock
    private JobStageTemplateMapper templateMapper;
    @Mock
    private EvaluationDimensionMapper dimensionMapper;
    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private IdempotencyService idempotencyService;

    private ProcessTemplateService service;

    @BeforeEach
    void setUp() {
        service = new ProcessTemplateService(jobMapper, templateMapper, dimensionMapper,
                applicationMapper, new ObjectMapper(), dataScopeService, idempotencyService);
        UserContext.set("10001", List.of("HR_ADMIN"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createAssignsNextVersionAndPersistsDimensions() {
        when(jobMapper.selectByIdForUpdate(50001L)).thenReturn(job());
        when(dataScopeService.current()).thenReturn(allScope());
        when(templateMapper.selectMaxVersion(50001L)).thenReturn(0);
        when(templateMapper.insert(any(JobStageTemplate.class))).thenAnswer(invocation -> {
            JobStageTemplate template = invocation.getArgument(0);
            template.setId(90001L);
            return 1;
        });

        ProcessTemplateView view = service.create(form(0.6, 0.4), null);

        assertThat(view.id()).isEqualTo("90001");
        assertThat(view.templateVer()).isEqualTo(1);
        assertThat(view.status()).isEqualTo("ACTIVE");
        verify(templateMapper).deactivateActive(50001L);
        ArgumentCaptor<EvaluationDimension> dims = ArgumentCaptor.forClass(EvaluationDimension.class);
        verify(dimensionMapper, org.mockito.Mockito.times(2)).insert(dims.capture());
        assertThat(dims.getAllValues()).extracting(EvaluationDimension::getSort).containsExactly(0, 1);
    }

    @Test
    void createRejectsWeightSumOutsideTolerance() {
        when(jobMapper.selectByIdForUpdate(50001L)).thenReturn(job());
        when(dataScopeService.current()).thenReturn(allScope());

        assertThatThrownBy(() -> service.create(form(0.5, 0.3), null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
        verify(templateMapper, never()).insert(any(JobStageTemplate.class));
    }

    @Test
    void createRejectsDuplicateStageCode() {
        when(jobMapper.selectByIdForUpdate(50001L)).thenReturn(job());
        when(dataScopeService.current()).thenReturn(allScope());

        ProcessTemplateCreateForm form = form(0.6, 0.4);
        form.getStageFlow().get(1).setStageNo("SCREEN");

        assertThatThrownBy(() -> service.create(form, null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void updateRejectsHistoricalVersion() {
        JobStageTemplate template = template("INACTIVE", 1, 0);
        when(templateMapper.selectById(90001L)).thenReturn(template);
        when(jobMapper.selectById(50001L)).thenReturn(job());
        when(jobMapper.selectByIdForUpdate(50001L)).thenReturn(job());
        when(dataScopeService.current()).thenReturn(allScope());

        assertThatThrownBy(() -> service.update(90001L, updateForm(0)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void updateRejectsStaleVersion() {
        JobStageTemplate template = template("ACTIVE", 1, 0);
        when(templateMapper.selectById(90001L)).thenReturn(template);
        when(jobMapper.selectById(50001L)).thenReturn(job());
        when(jobMapper.selectByIdForUpdate(50001L)).thenReturn(job());
        when(dataScopeService.current()).thenReturn(allScope());

        assertThatThrownBy(() -> service.update(90001L, updateForm(2)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ILLEGAL_TRANSITION);
    }

    @Test
    void updateCreatesNewActiveVersion() {
        JobStageTemplate template = template("ACTIVE", 1, 0);
        when(templateMapper.selectById(90001L)).thenReturn(template);
        when(jobMapper.selectById(50001L)).thenReturn(job());
        when(dataScopeService.current()).thenReturn(allScope());
        when(jobMapper.selectByIdForUpdate(50001L)).thenReturn(job());
        when(templateMapper.selectMaxVersion(50001L)).thenReturn(1);
        when(templateMapper.insert(any(JobStageTemplate.class))).thenAnswer(invocation -> {
            JobStageTemplate inserted = invocation.getArgument(0);
            inserted.setId(90002L);
            return 1;
        });

        ProcessTemplateView view = service.update(90001L, updateForm(0));

        assertThat(view.id()).isEqualTo("90002");
        assertThat(view.templateVer()).isEqualTo(2);
        verify(templateMapper).deactivateActive(50001L);
    }

    @Test
    void deleteRejectsWhenJobHasApplications() {
        when(templateMapper.selectById(90001L)).thenReturn(template("ACTIVE", 1, 0));
        when(jobMapper.selectById(50001L)).thenReturn(job());
        when(jobMapper.selectByIdForUpdate(50001L)).thenReturn(job());
        when(dataScopeService.current()).thenReturn(allScope());
        when(applicationMapper.countByJob(50001L)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(90001L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void deleteActiveFallsBackToLatestRemainingVersion() {
        when(templateMapper.selectById(90001L)).thenReturn(template("ACTIVE", 2, 0));
        when(jobMapper.selectById(50001L)).thenReturn(job());
        when(jobMapper.selectByIdForUpdate(50001L)).thenReturn(job());
        when(dataScopeService.current()).thenReturn(allScope());
        when(applicationMapper.countByJob(50001L)).thenReturn(0L);
        JobStageTemplate remaining = template("INACTIVE", 1, 0);
        remaining.setId(90000L);
        when(templateMapper.selectLatestByJobId(50001L)).thenReturn(remaining);

        service.delete(90001L);

        ArgumentCaptor<JobStageTemplate> update = ArgumentCaptor.forClass(JobStageTemplate.class);
        verify(templateMapper).updateById(update.capture());
        assertThat(update.getValue().getId()).isEqualTo(90000L);
        assertThat(update.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    private static ProcessTemplateCreateForm form(double firstWeight, double secondWeight) {
        ProcessTemplateCreateForm form = new ProcessTemplateCreateForm();
        form.setJobId("50001");
        form.setStageFlow(List.of(stage("SCREEN"), stage("FIRST")));
        form.setScoreDims(List.of(
                dim("PROFESSIONAL", firstWeight),
                dim("COMMUNICATION", secondWeight)));
        return form;
    }

    private static ProcessTemplateUpdateForm updateForm(int version) {
        ProcessTemplateUpdateForm form = new ProcessTemplateUpdateForm();
        form.setStageFlow(List.of(stage("SCREEN"), stage("FIRST")));
        form.setScoreDims(List.of(dim("PROFESSIONAL", 0.6), dim("COMMUNICATION", 0.4)));
        form.setVersion(version);
        return form;
    }

    private static StageDef stage(String code) {
        StageDef stage = new StageDef();
        stage.setStageNo(code);
        stage.setStageName(code);
        stage.setMode("ONLINE");
        stage.setRounds(1);
        return stage;
    }

    private static ScoreDim dim(String code, double weight) {
        ScoreDim dim = new ScoreDim();
        dim.setDimCode(code);
        dim.setDimName(code);
        dim.setWeight(BigDecimal.valueOf(weight));
        dim.setFullScore(BigDecimal.valueOf(100));
        return dim;
    }

    private static Job job() {
        Job job = new Job();
        job.setId(50001L);
        job.setStatus(JobService.STATUS_PUBLISHED);
        job.setOwnerHrId(10001L);
        return job;
    }

    private static JobStageTemplate template(String status, int templateVer, int version) {
        JobStageTemplate template = new JobStageTemplate();
        template.setId(90001L);
        template.setJobId(50001L);
        template.setTemplateVer(templateVer);
        template.setStatus(status);
        template.setStageFlow("[{\"stageNo\":\"SCREEN\",\"stageName\":\"初筛\",\"mode\":\"ONLINE\",\"rounds\":1}]");
        template.setScoreDims("[{\"dimCode\":\"PROFESSIONAL\",\"dimName\":\"专业\",\"weight\":1.0,\"fullScore\":100}]");
        template.setVersion(version);
        return template;
    }

    private static DataScopeService.DataScope allScope() {
        return new DataScopeService.DataScope("ALL", null, List.of(), 10001L);
    }
}
