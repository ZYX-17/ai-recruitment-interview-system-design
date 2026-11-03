package com.recruit.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.interview.entity.AsyncQuestion;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 异步题目 Mapper。
 */
public interface AsyncQuestionMapper extends BaseMapper<AsyncQuestion> {

    /**
     * 按试卷取题目（按序号）。
     *
     * @param paperId 试卷 ID
     * @return 题目列表
     */
    @Select("SELECT * FROM async_question WHERE paper_id = #{paperId} ORDER BY seq_no")
    List<AsyncQuestion> selectByPaperId(@Param("paperId") long paperId);

    /**
     * 题目数。
     *
     * @param paperId 试卷 ID
     * @return 题目数
     */
    @Select("SELECT COUNT(*) FROM async_question WHERE paper_id = #{paperId}")
    long countByPaperId(@Param("paperId") long paperId);
}
