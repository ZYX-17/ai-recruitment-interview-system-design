package com.recruit.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.interview.entity.AsyncAnswerScore;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 异步作答 AI 评分明细 Mapper。
 */
public interface AsyncAnswerScoreMapper extends BaseMapper<AsyncAnswerScore> {

    /**
     * 按作答取评分明细。
     *
     * @param answerId 作答 ID
     * @return 明细；不存在返回 null
     */
    @Select("SELECT * FROM async_answer_score WHERE answer_id = #{answerId}")
    AsyncAnswerScore selectByAnswerId(@Param("answerId") long answerId);

    /**
     * 覆盖评分明细（AI 重跑时保留唯一约束）。
     */
    @Update("UPDATE async_answer_score SET dimension_scores = #{dimensionScores}, "
            + "comment = #{comment}, model = #{model}, prompt_version = #{promptVersion} "
            + "WHERE answer_id = #{answerId}")
    int updateByAnswerId(@Param("answerId") long answerId,
                         @Param("dimensionScores") String dimensionScores,
                         @Param("comment") String comment,
                         @Param("model") String model,
                         @Param("promptVersion") int promptVersion);
}
