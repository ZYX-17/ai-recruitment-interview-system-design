package com.recruit.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.interview.entity.AsyncAnswer;
import com.recruit.interview.mapper.row.AsyncAnswerRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 异步作答 Mapper。
 *
 * <p>作答与题目通过 {@code question_id} 关联；回看/复核一次 JOIN 出题目文本与权重。
 * 人工/AI 评分更新都用 {@code version} CAS，避免并发覆盖。</p>
 */
public interface AsyncAnswerMapper extends BaseMapper<AsyncAnswer> {

    /**
     * 按试卷取作答行（含题目快照）。
     *
     * @param paperId 试卷 ID
     * @return 作答行
     */
    @Select("SELECT a.*, q.paper_id, q.seq_no, q.question_text, q.weight FROM async_answer a "
            + "JOIN async_question q ON q.id = a.question_id "
            + "WHERE q.paper_id = #{paperId} ORDER BY q.seq_no")
    List<AsyncAnswerRow> selectByPaperId(@Param("paperId") long paperId);

    /**
     * 按作答 ID 取行（含题目快照）。
     *
     * @param id 作答 ID
     * @return 行；不存在返回 null
     */
    @Select("SELECT a.*, q.paper_id, q.seq_no, q.question_text, q.weight FROM async_answer a "
            + "JOIN async_question q ON q.id = a.question_id WHERE a.id = #{id}")
    AsyncAnswerRow selectRowById(@Param("id") long id);

    /**
     * 按题目取作答。
     *
     * @param questionId 题目 ID
     * @return 作答；不存在返回 null
     */
    @Select("SELECT * FROM async_answer WHERE question_id = #{questionId}")
    AsyncAnswer selectByQuestionId(@Param("questionId") long questionId);

    /**
     * 写入 AI 转写与 AI 分。
     */
    @Update("UPDATE async_answer SET transcript_text = #{transcriptText}, ai_score = #{aiScore}, "
            + "status = #{status}, version = version + 1 "
            + "WHERE id = #{id} AND version = #{version}")
    int updateAiResult(@Param("id") long id,
                       @Param("transcriptText") String transcriptText,
                       @Param("aiScore") BigDecimal aiScore,
                       @Param("status") String status,
                       @Param("version") int version);

    /**
     * 写入人工复核分与评语。
     */
    @Update("UPDATE async_answer SET manual_score = #{manualScore}, manual_comment = #{manualComment}, "
            + "manual_scored_by = #{scoredBy}, manual_scored_at = #{scoredAt}, status = 'REVIEWED', "
            + "version = version + 1 WHERE id = #{id} AND version = #{version}")
    int updateManual(@Param("id") long id,
                     @Param("manualScore") BigDecimal manualScore,
                     @Param("manualComment") String manualComment,
                     @Param("scoredBy") long scoredBy,
                     @Param("scoredAt") LocalDateTime scoredAt,
                     @Param("version") int version);

    /**
     * 试卷作答数。
     */
    @Select("SELECT COUNT(*) FROM async_answer a JOIN async_question q ON q.id = a.question_id "
            + "WHERE q.paper_id = #{paperId}")
    long countByPaperId(@Param("paperId") long paperId);

    /**
     * 已有评分（AI 或人工）的作答数。
     */
    @Select("SELECT COUNT(*) FROM async_answer a JOIN async_question q ON q.id = a.question_id "
            + "WHERE q.paper_id = #{paperId} AND (a.ai_score IS NOT NULL OR a.manual_score IS NOT NULL)")
    long countScoredByPaperId(@Param("paperId") long paperId);

    /**
     * 删除试卷下的全部作答（HR 重置）。
     */
    @Delete("DELETE a FROM async_answer a JOIN async_question q ON q.id = a.question_id "
            + "WHERE q.paper_id = #{paperId}")
    int deleteByPaperId(@Param("paperId") long paperId);
}
