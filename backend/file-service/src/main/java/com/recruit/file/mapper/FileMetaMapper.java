package com.recruit.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.file.entity.FileMeta;
import org.apache.ibatis.annotations.Mapper;

/**
 * file_meta 数据访问。复杂查询后续在 XML 中补；当前 CRUD 由 BaseMapper 提供。
 */
@Mapper
public interface FileMetaMapper extends BaseMapper<FileMeta> {
}
