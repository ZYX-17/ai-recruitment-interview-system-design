package com.recruit.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.interview.entity.InterviewRecording;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 面试录像登记 Mapper。
 */
public interface InterviewRecordingMapper extends BaseMapper<InterviewRecording> {

    /**
     * 按面试取录像。
     *
     * @param interviewId 面试 ID
     * @return 录像列表
     */
    @Select("SELECT * FROM interview_recording WHERE interview_id = #{interviewId} "
            + "ORDER BY created_at DESC, id DESC")
    List<InterviewRecording> selectByInterviewId(@Param("interviewId") long interviewId);
}
