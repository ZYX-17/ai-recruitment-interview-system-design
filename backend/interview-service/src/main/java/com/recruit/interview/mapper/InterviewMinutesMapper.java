package com.recruit.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.interview.entity.InterviewMinutes;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
