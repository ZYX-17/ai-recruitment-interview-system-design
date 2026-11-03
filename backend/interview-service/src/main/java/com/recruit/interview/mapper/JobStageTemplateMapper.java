package com.recruit.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.interview.entity.JobStageTemplate;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 流程模板 Mapper。
 */
public interface JobStageTemplateMapper extends BaseMapper<JobStageTemplate> {

    /**
     * 当前最大版本号。
     *
     * @param jobId 岗位 ID
     * @return 最大版本；没有模板时 0
     */
    @Select("SELECT COALESCE(MAX(template_ver), 0) FROM job_stage_template WHERE job_id = #{jobId}")
    int selectMaxVersion(@Param("jobId") Long jobId);

    /**
     * 停用当前 ACTIVE 模板（激活新版本时调用）。
     *
     * @param jobId 岗位 ID
     * @return 影响行数
     */
    @Update("UPDATE job_stage_template SET status = 'INACTIVE' WHERE job_id = #{jobId} AND status = 'ACTIVE'")
    int deactivateActive(@Param("jobId") Long jobId);

    /**
     * 岗位当前 ACTIVE 模板。
     *
     * @param jobId 岗位 ID
     * @return 模板；不存在返回 null
     */
    @Select("SELECT * FROM job_stage_template WHERE job_id = #{jobId} AND status = 'ACTIVE' "
            + "ORDER BY template_ver DESC LIMIT 1")
    JobStageTemplate selectActiveByJobId(@Param("jobId") Long jobId);

    /**
     * 按岗位 + 版本取模板（评价提交校验历史版本）。
     *
     * @param jobId       岗位 ID
     * @param templateVer 模板版本
     * @return 模板；不存在返回 null
     */
    @Select("SELECT * FROM job_stage_template WHERE job_id = #{jobId} AND template_ver = #{templateVer}")
    JobStageTemplate selectByJobIdAndVer(@Param("jobId") Long jobId,
                                         @Param("templateVer") Integer templateVer);

    /**
     * 岗位最新版本模板（删除 ACTIVE 后回退使用）。
     *
     * @param jobId 岗位 ID
     * @return 模板；不存在返回 null
     */
    @Select("SELECT * FROM job_stage_template WHERE job_id = #{jobId} ORDER BY template_ver DESC LIMIT 1")
    JobStageTemplate selectLatestByJobId(@Param("jobId") Long jobId);

    /**
     * 岗位全部模板版本（删除岗位时级联清理）。
     *
     * @param jobId 岗位 ID
     * @return 模板行
     */
    @Select("SELECT * FROM job_stage_template WHERE job_id = #{jobId} ORDER BY template_ver DESC")
    List<JobStageTemplate> selectByJobId(@Param("jobId") Long jobId);

    /**
     * 模板分页查询（按岗位数据范围过滤）。
     */
    @Select("<script>SELECT t.* FROM job_stage_template t JOIN job j ON j.id = t.job_id WHERE 1 = 1"
            + "<if test=\"jobId != null\"> AND t.job_id = #{jobId}</if>"
            + "<if test=\"status != null and status != ''\"> AND t.status = #{status}</if>"
            + "<if test=\"scope == 'DEPARTMENT'\"><choose><when test=\"deptIds != null and deptIds.size() > 0\">"
            + " AND j.dept_id IN <foreach collection=\"deptIds\" item=\"d\" open=\"(\" separator=\",\" close=\")\">#{d}</foreach>"
            + "</when><otherwise> AND 1 = 0</otherwise></choose></if>"
            + "<if test=\"scope == 'SELF' or scope == 'ASSIGNED'\"> AND j.owner_hr_id = #{userId}</if> "
            + "ORDER BY t.job_id DESC, t.template_ver DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<JobStageTemplate> selectTemplatePage(@Param("jobId") Long jobId,
                                              @Param("status") String status,
                                              @Param("scope") String scope,
                                              @Param("deptIds") List<Long> deptIds,
                                              @Param("userId") long userId,
                                              @Param("limit") int limit,
                                              @Param("offset") long offset);

    /**
     * 模板分页总数。
     */
    @Select("<script>SELECT COUNT(*) FROM job_stage_template t JOIN job j ON j.id = t.job_id WHERE 1 = 1"
            + "<if test=\"jobId != null\"> AND t.job_id = #{jobId}</if>"
            + "<if test=\"status != null and status != ''\"> AND t.status = #{status}</if>"
            + "<if test=\"scope == 'DEPARTMENT'\"><choose><when test=\"deptIds != null and deptIds.size() > 0\">"
            + " AND j.dept_id IN <foreach collection=\"deptIds\" item=\"d\" open=\"(\" separator=\",\" close=\")\">#{d}</foreach>"
            + "</when><otherwise> AND 1 = 0</otherwise></choose></if>"
            + "<if test=\"scope == 'SELF' or scope == 'ASSIGNED'\"> AND j.owner_hr_id = #{userId}</if></script>")
    long countTemplatePage(@Param("jobId") Long jobId,
                           @Param("status") String status,
                           @Param("scope") String scope,
                           @Param("deptIds") List<Long> deptIds,
                           @Param("userId") long userId);
}
