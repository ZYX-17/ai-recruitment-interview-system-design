package com.recruit.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.interview.entity.EvaluationDimension;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 评分维度 Mapper。
 */
public interface EvaluationDimensionMapper extends BaseMapper<EvaluationDimension> {

    /**
     * 模板下的维度，按 sort 排序。
     *
     * @param templateId 模板 ID
     * @return 维度行
     */
    @Select("SELECT * FROM evaluation_dimension WHERE template_id = #{templateId} ORDER BY sort, id")
    List<EvaluationDimension> selectByTemplateId(@Param("templateId") Long templateId);

    /**
     * 删除模板下的全部维度。
     *
     * @param templateId 模板 ID
     * @return 影响行数
     */
    @Delete("DELETE FROM evaluation_dimension WHERE template_id = #{templateId}")
    int deleteByTemplateId(@Param("templateId") Long templateId);
}
