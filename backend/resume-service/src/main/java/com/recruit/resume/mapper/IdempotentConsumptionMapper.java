package com.recruit.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.resume.entity.IdempotentConsumption;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IdempotentConsumptionMapper extends BaseMapper<IdempotentConsumption> {

    /**
     * 依赖唯一键 (consumer_group, event_id) 做幂等占位。
     *
     * <p>用 INSERT IGNORE 而不是先查后插：并发消费同一事件时只有一条能插入成功，
     * 返回值 0 表示重复事件。避免“查不到再插”之间的竞态。</p>
     *
     * @return 1=首次消费，0=重复事件
     */
    @Insert("INSERT IGNORE INTO idempotent_consumption (id, consumer_group, event_id) "
            + "VALUES (#{id}, #{consumerGroup}, #{eventId})")
    int insertIgnore(@Param("id") long id,
                     @Param("consumerGroup") String consumerGroup,
                     @Param("eventId") String eventId);
}
