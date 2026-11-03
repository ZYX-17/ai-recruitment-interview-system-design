package com.recruit.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.auth.entity.IamDict;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 字典表 Mapper。
 *
 * <p>分页由 {@code dictType/keyword/status} 组合过滤，关键字同时匹配编码与文案；
 * 更新只允许白名单字段，类型与编码保持不可变。</p>
 */
public interface IamDictMapper extends BaseMapper<IamDict> {

    /**
     * 按类型 + 编码查字典项（种子/新增前置查重）。
     *
     * @param dictType 字典类型
     * @param dictCode 枚举码
     * @return 字典项；不存在返回 null
     */
    @Select("SELECT * FROM iam_dict WHERE dict_type = #{dictType} AND dict_code = #{dictCode} LIMIT 1")
    IamDict selectByTypeAndCode(@Param("dictType") String dictType,
                                @Param("dictCode") String dictCode);

    /**
     * 字典分页。
     *
     * @param dictType 类型过滤，可为 null
     * @param keyword  编码/文案关键字，可为 null
     * @param status   状态过滤，可为 null
     * @param limit    每页条数
     * @param offset   偏移量
     * @return 当前页字典项
     */
    @Select("<script>SELECT * FROM iam_dict WHERE 1 = 1 "
            + "<if test=\"dictType != null and dictType != ''\"> AND dict_type = #{dictType}</if>"
            + "<if test=\"keyword != null and keyword != ''\">"
            + " AND (dict_code LIKE CONCAT('%', #{keyword}, '%') "
            + "OR dict_label LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + "<if test=\"status != null and status != ''\"> AND status = #{status}</if>"
            + " ORDER BY dict_type, sort, id LIMIT #{limit} OFFSET #{offset}</script>")
    List<IamDict> selectDictPage(@Param("dictType") String dictType,
                                 @Param("keyword") String keyword,
                                 @Param("status") String status,
                                 @Param("limit") long limit,
                                 @Param("offset") long offset);

    /**
     * 字典分页计数，条件与 {@link #selectDictPage} 一致。
     *
     * @param dictType 类型过滤，可为 null
     * @param keyword  编码/文案关键字，可为 null
     * @param status   状态过滤，可为 null
     * @return 总记录数
     */
    @Select("<script>SELECT COUNT(1) FROM iam_dict WHERE 1 = 1 "
            + "<if test=\"dictType != null and dictType != ''\"> AND dict_type = #{dictType}</if>"
            + "<if test=\"keyword != null and keyword != ''\">"
            + " AND (dict_code LIKE CONCAT('%', #{keyword}, '%') "
            + "OR dict_label LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + "<if test=\"status != null and status != ''\"> AND status = #{status}</if>"
            + "</script>")
    long countDictPage(@Param("dictType") String dictType,
                       @Param("keyword") String keyword,
                       @Param("status") String status);

    /**
     * 乐观锁更新字典白名单字段（类型/编码不可改）。
     *
     * @return 影响行数；0 表示版本冲突或字典不存在
     */
    @Update("UPDATE iam_dict SET dict_label = #{dictLabel}, sort = #{sort}, status = #{status}, "
            + "remark = #{remark}, version = version + 1, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND version = #{version}")
    int updateDict(@Param("id") Long id,
                   @Param("dictLabel") String dictLabel,
                   @Param("sort") Integer sort,
                   @Param("status") String status,
                   @Param("remark") String remark,
                   @Param("version") Integer version);
}
