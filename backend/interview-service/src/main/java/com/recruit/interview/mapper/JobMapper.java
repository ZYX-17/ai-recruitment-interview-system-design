package com.recruit.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.interview.entity.Job;
import com.recruit.interview.mapper.row.JobRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 岗位 Mapper。
 *
 * <p>数据范围通过显式参数拼进 WHERE：DEPARTMENT 用部门子树 ID 列表，
 * SELF/ASSIGNED 用负责人；不依赖全局 SQL 改写，避免拦截器缺失时越权。</p>
 */
public interface JobMapper extends BaseMapper<Job> {

    /**
     * 统计岗位下的投递数（删除前校验）。
     *
     * @param jobId 岗位 ID
     * @return 投递数
     */
    @Select("SELECT COUNT(*) FROM application WHERE job_id = #{jobId}")
    long countApplications(@Param("jobId") Long jobId);

    /**
     * 按编码查岗位（唯一键前置校验）。
     *
     * @param jobCode 岗位编码
     * @return 岗位；不存在返回 null
     */
    @Select("SELECT * FROM job WHERE job_code = #{jobCode} LIMIT 1")
    Job selectByJobCode(@Param("jobCode") String jobCode);

    /**
     * 加行锁读取岗位（模板版本化/发布流转串行化）。
     *
     * @param id 岗位 ID
     * @return 岗位
     */
    @Select("SELECT * FROM job WHERE id = #{id} FOR UPDATE")
    Job selectByIdForUpdate(@Param("id") Long id);

    /**
     * 按版本更新可编辑字段，版本号自增。
     *
     * @param job 待更新岗位，{@code version} 为客户端期望版本
     * @return 影响行数；0 表示版本冲突或不存在
     */
    @Update("UPDATE job SET title = #{title}, dept_id = #{deptId}, dept_name = #{deptName}, "
            + "jd_text = #{jdText}, requirements_json = #{requirementsJson}, headcount = #{headcount}, "
            + "salary_range = #{salaryRange}, owner_hr_id = #{ownerHrId}, "
            + "version = version + 1, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND version = #{version}")
    int updateWithVersion(Job job);

    /**
     * 按版本更新状态，版本号自增。
     *
     * @param id          岗位 ID
     * @param status      目标状态
     * @param publishedAt 发布时间；为 null 时不覆盖
     * @param version     客户端期望版本
     * @return 影响行数；0 表示版本冲突或不存在
     */
    @Update("<script>UPDATE job SET status = #{status}, version = version + 1, "
            + "updated_at = CURRENT_TIMESTAMP(3)"
            + "<if test=\"publishedAt != null\">, published_at = #{publishedAt}</if> "
            + "WHERE id = #{id} AND version = #{version}</script>")
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("publishedAt") java.time.LocalDateTime publishedAt,
                     @Param("version") Integer version);

    /**
     * 岗位分页查询。
     *
     * @param keyword     编码/名称模糊词
     * @param status      状态
     * @param deptId      部门过滤
     * @param scope       ALL/DEPARTMENT/ASSIGNED/SELF
     * @param deptIds     DEPARTMENT 范围可见部门
     * @param userId      SELF/ASSIGNED 的当前用户
     * @param limit       每页条数
     * @param offset      偏移
     * @return 岗位行
     */
    @Select("<script>SELECT j.*, (SELECT COUNT(*) FROM application a WHERE a.job_id = j.id) AS application_count "
            + "FROM job j WHERE 1 = 1"
            + "<if test=\"keyword != null and keyword != ''\"> AND (j.job_code LIKE CONCAT('%', #{keyword}, '%') "
            + "OR j.title LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + "<if test=\"status != null and status != ''\"> AND j.status = #{status}</if>"
            + "<if test=\"deptId != null\"> AND j.dept_id = #{deptId}</if>"
            + "<if test=\"scope == 'DEPARTMENT'\"><choose><when test=\"deptIds != null and deptIds.size() > 0\">"
            + " AND j.dept_id IN <foreach collection=\"deptIds\" item=\"d\" open=\"(\" separator=\",\" close=\")\">#{d}</foreach>"
            + "</when><otherwise> AND 1 = 0</otherwise></choose></if>"
            + "<if test=\"scope == 'SELF' or scope == 'ASSIGNED'\"> AND j.owner_hr_id = #{userId}</if> "
            + "ORDER BY j.created_at DESC, j.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<JobRow> selectJobPage(@Param("keyword") String keyword,
                               @Param("status") String status,
                               @Param("deptId") Long deptId,
                               @Param("scope") String scope,
                               @Param("deptIds") List<Long> deptIds,
                               @Param("userId") long userId,
                               @Param("limit") int limit,
                               @Param("offset") long offset);

    /**
     * 岗位分页总数。
     *
     * @param keyword  编码/名称模糊词
     * @param status   状态
     * @param deptId   部门过滤
     * @param scope    ALL/DEPARTMENT/ASSIGNED/SELF
     * @param deptIds  DEPARTMENT 范围可见部门
     * @param userId   SELF/ASSIGNED 的当前用户
     * @return 总记录数
     */
    @Select("<script>SELECT COUNT(*) FROM job j WHERE 1 = 1"
            + "<if test=\"keyword != null and keyword != ''\"> AND (j.job_code LIKE CONCAT('%', #{keyword}, '%') "
            + "OR j.title LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + "<if test=\"status != null and status != ''\"> AND j.status = #{status}</if>"
            + "<if test=\"deptId != null\"> AND j.dept_id = #{deptId}</if>"
            + "<if test=\"scope == 'DEPARTMENT'\"><choose><when test=\"deptIds != null and deptIds.size() > 0\">"
            + " AND j.dept_id IN <foreach collection=\"deptIds\" item=\"d\" open=\"(\" separator=\",\" close=\")\">#{d}</foreach>"
            + "</when><otherwise> AND 1 = 0</otherwise></choose></if>"
            + "<if test=\"scope == 'SELF' or scope == 'ASSIGNED'\"> AND j.owner_hr_id = #{userId}</if></script>")
    long countJobPage(@Param("keyword") String keyword,
                      @Param("status") String status,
                      @Param("deptId") Long deptId,
                      @Param("scope") String scope,
                      @Param("deptIds") List<Long> deptIds,
                      @Param("userId") long userId);
}
