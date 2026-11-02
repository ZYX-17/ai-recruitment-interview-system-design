package com.recruit.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.auth.entity.IamDataScope;

/**
 * 数据范围 Mapper。
 *
 * <p>常规单表读写走 {@link BaseMapper}；复杂查询集中在本接口，避免 SQL 散落在 Service。</p>
 */
public interface IamDataScopeMapper extends BaseMapper<IamDataScope> {
}
