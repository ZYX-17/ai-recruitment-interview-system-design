package com.recruit.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.auth.entity.IamRefreshSession;

/**
 * 刷新会话 Mapper。
 *
 * <p>常规单表读写走 {@link BaseMapper}；复杂查询集中在本接口，避免 SQL 散落在 Service。</p>
 */
public interface IamRefreshSessionMapper extends BaseMapper<IamRefreshSession> {
}
