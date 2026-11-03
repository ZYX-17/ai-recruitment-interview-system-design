package com.recruit.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.interview.entity.Evaluation;
import com.recruit.interview.mapper.row.MyEvaluationRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面试评价 Mapper。
 */
public interface EvaluationMapper extends BaseMapper<Evaluation> {

    /**
     * 场次全部评价。
     *
     * @param interviewId 面试 ID
     * @return 评价列表
     */
    @Select("SELECT * FROM evaluation WHERE interview_id = #{interviewId} "
            + "ORDER BY round_no ASC, interviewer_id ASC")
    List<Evaluation> selectByInterviewId(@Param("interviewId") long interviewId);

    /**
     * 某面试官在某轮的评价。
     *
     * @param interviewId   面试 ID
     * @param roundNo       轮次
     * @param interviewerId 面试官
     * @return 评价；不存在返回 null
     */
    @Select("SELECT * FROM evaluation WHERE interview_id = #{interviewId} "
            + "AND round_no = #{roundNo} AND interviewer_id = #{interviewerId}")
    Evaluation selectByInterviewAndInterviewer(@Param("interviewId") long interviewId,
                                               @Param("roundNo") int roundNo,
                                               @Param("interviewerId") long interviewerId);

    /**
     * 已提交评价数（判断是否全部面试官完成）。
     *
     * @param interviewId 面试 ID
     * @param roundNo     轮次
     * @return 数量
     */
    @Select("SELECT COUNT(*) FROM evaluation WHERE interview_id = #{interviewId} "
            + "AND round_no = #{roundNo} AND status = 'SUBMITTED'")
    long countSubmitted(@Param("interviewId") long interviewId, @Param("roundNo") int roundNo);

    /**
     * 重开评价（仅 SUBMITTED，版本匹配时生效）。
     */
    @Update("UPDATE evaluation SET status = 'REOPENED', reopened_by = #{reopenedBy}, "
            + "reopened_at = CURRENT_TIMESTAMP(3), reopen_reason = #{reason}, "
            + "version = version + 1, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND version = #{version} AND status = 'SUBMITTED'")
    int reopen(@Param("id") long id, @Param("reopenedBy") long reopenedBy,
               @Param("reason") String reason, @Param("version") int version);

    /**
     * 重开后的重提（仅 REOPENED，版本匹配时生效）。
     */
    @Update("UPDATE evaluation SET dimension_scores = #{dimensionScores}, comment = #{comment}, "
            + "result = #{result}, template_version = #{templateVersion}, status = 'SUBMITTED', "
            + "submitted_at = #{submittedAt}, version = version + 1, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND version = #{version} AND status = 'REOPENED'")
    int resubmit(Evaluation evaluation);

    /**
     * 评价模板是否被历史评价引用。
     *
     * @param jobId       岗位 ID
     * @param templateVer 模板版本
     * @return 引用数
     */
    @Select("SELECT COUNT(*) FROM evaluation e JOIN interview i ON i.id = e.interview_id "
            + "JOIN application a ON a.id = i.application_id "
            + "WHERE a.job_id = #{jobId} AND e.template_version = #{templateVer}")
    long countByJobAndTemplateVer(@Param("jobId") long jobId,
                                  @Param("templateVer") int templateVer);

    /**
     * 我的评价任务：当前用户被安排的面试 + 自己在该轮的评价格子。
     */
    @Select("SELECT i.id AS interview_id, i.application_id, i.round_no, i.interviewer_ids, "
            + "i.start_time, i.end_time, i.status AS interview_status, "
            + "a.job_id, a.job_title, a.person_id, a.person_name, "
            + "e.id AS evaluation_id, e.status AS evaluation_status, e.result, e.submitted_at, "
            + "e.dimension_scores, e.template_version "
            + "FROM interview i JOIN application a ON a.id = i.application_id "
            + "LEFT JOIN evaluation e ON e.interview_id = i.id AND e.round_no = i.round_no "
            + "AND e.interviewer_id = #{interviewerId} "
            + "WHERE JSON_CONTAINS(i.interviewer_ids, JSON_QUOTE(#{userIdText})) "
            + "AND i.start_time >= #{from} AND i.start_time < #{to} "
            + "AND i.status <> 'CANCELLED' "
            + "ORDER BY i.start_time DESC, i.id DESC")
    List<MyEvaluationRow> selectMyTasks(@Param("interviewerId") long interviewerId,
                                        @Param("userIdText") String userIdText,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);
}
