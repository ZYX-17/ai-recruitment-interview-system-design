package com.recruit.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.interview.entity.ApplicationStageHistory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 阶段历史 Mapper（只增不改）。
 */
public interface ApplicationStageHistoryMapper extends BaseMapper<ApplicationStageHistory> {

    /**
     * 投递的完整时间线，按操作时间升序。
     *
     * @param applicationId 投递 ID
     * @return 历史行
     */
    @Select("SELECT * FROM application_stage_history WHERE application_id = #{applicationId} "
            + "ORDER BY operated_at, id")
    List<ApplicationStageHistory> selectByApplicationId(@Param("applicationId") Long applicationId);
}
