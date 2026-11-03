package com.recruit.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.interview.entity.InterviewMinutes;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 会议纪要 Mapper（P6-5 只读转写与 AI 要点）。
 */
public interface InterviewMinutesMapper extends BaseMapper<InterviewMinutes> {

    /**
     * 按面试查纪要。
     *
     * @param interviewId 面试 ID
     * @return 纪要；不存在返回 null
     */
    @Select("SELECT * FROM interview_minutes WHERE interview_id = #{interviewId}")
    InterviewMinutes selectByInterviewId(@Param("interviewId") long interviewId);

    /**
     * 按 AI 任务 ID 查纪要（回调幂等）。
     *
     * @param aiTaskId AI 任务 ID
     * @return 纪要；不存在返回 null
     */
    @Select("SELECT * FROM interview_minutes WHERE ai_task_id = #{aiTaskId}")
    InterviewMinutes selectByAiTaskId(@Param("aiTaskId") String aiTaskId);

    /**
     * AI 回写/重跑覆盖草稿：已确认/驳回的纪要不可覆盖。
     */
    @Update("UPDATE interview_minutes SET ai_task_id = #{aiTaskId}, "
            + "transcript_object_key = #{transcriptObjectKey}, "
            + "transcript_segment_count = #{transcriptSegmentCount}, minutes_md = #{minutesMd}, "
            + "ai_evaluation = #{aiEvaluation}, review_status = #{reviewStatus}, "
            + "model = #{model}, prompt_version = #{promptVersion}, version = version + 1, "
            + "updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{id} AND version = #{version} "
            + "AND review_status IN ('PENDING','INSUFFICIENT_TRANSCRIPT')")
    int updateFromAi(@Param("id") long id,
                     @Param("aiTaskId") String aiTaskId,
                     @Param("transcriptObjectKey") String transcriptObjectKey,
                     @Param("transcriptSegmentCount") int transcriptSegmentCount,
                     @Param("minutesMd") String minutesMd,
                     @Param("aiEvaluation") String aiEvaluation,
                     @Param("reviewStatus") String reviewStatus,
                     @Param("model") String model,
                     @Param("promptVersion") int promptVersion,
                     @Param("version") int version);

    /**
     * 人工确认/驳回（CAP：仅待确认/转写不足可操作）。
     */
    @Update("UPDATE interview_minutes SET review_status = #{reviewStatus}, "
            + "review_comment = #{comment}, reviewer_id = #{reviewerId}, "
            + "reviewed_at = #{reviewedAt}, version = version + 1, "
            + "updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{id} AND version = #{version} "
            + "AND review_status IN ('PENDING','INSUFFICIENT_TRANSCRIPT')")
    int review(@Param("id") long id,
               @Param("reviewStatus") String reviewStatus,
               @Param("comment") String comment,
               @Param("reviewerId") long reviewerId,
               @Param("reviewedAt") LocalDateTime reviewedAt,
               @Param("version") int version);
}
