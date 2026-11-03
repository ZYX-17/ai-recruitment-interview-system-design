package com.recruit.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recruit.interview.entity.Application;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 投递 Mapper。
 *
 * <p>列表按岗位数据范围过滤：DEPARTMENT 看岗位部门子树，SELF/ASSIGNED 看岗位负责人。
 * 详情由 Service 单独做对象级校验。</p>
 */
public interface ApplicationMapper extends BaseMapper<Application> {

    /**
     * 同岗位同候选人投递数（唯一键前置校验）。
     *
     * @param jobId    岗位 ID
     * @param personId 候选人 ID
     * @return 已存在数量
     */
    @Select("SELECT COUNT(*) FROM application WHERE job_id = #{jobId} AND person_id = #{personId}")
    long countByJobAndPerson(@Param("jobId") Long jobId, @Param("personId") Long personId);

    /**
     * 岗位下投递总数。
     *
     * @param jobId 岗位 ID
     * @return 投递数
     */
    @Select("SELECT COUNT(*) FROM application WHERE job_id = #{jobId}")
    long countByJob(@Param("jobId") Long jobId);

    /**
     * 版本乐观锁更新状态与阶段。
     *
     * @param app 目标值；{@code id/version} 为定位条件
     * @return 影响行数；0 表示版本冲突或不存在
     */
    @Update("UPDATE application SET current_stage = #{currentStage}, round_no = #{roundNo}, "
            + "status = #{status}, reject_reason = #{rejectReason}, version = version + 1, "
            + "updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{id} AND version = #{version}")
    int updateStateWithVersion(Application app);

    /**
     * 投递分页查询。
     */
    @Select("<script>SELECT a.* FROM application a JOIN job j ON j.id = a.job_id WHERE 1 = 1"
            + "<if test=\"keyword != null and keyword != ''\"> AND (a.person_name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR a.job_title LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + "<if test=\"status != null and status != ''\"> AND a.status = #{status}</if>"
            + "<if test=\"jobId != null\"> AND a.job_id = #{jobId}</if>"
            + "<if test=\"personId != null\"> AND a.person_id = #{personId}</if>"
            + "<if test=\"scope == 'DEPARTMENT'\"><choose><when test=\"deptIds != null and deptIds.size() > 0\">"
            + " AND j.dept_id IN <foreach collection=\"deptIds\" item=\"d\" open=\"(\" separator=\",\" close=\")\">#{d}</foreach>"
            + "</when><otherwise> AND 1 = 0</otherwise></choose></if>"
            + "<if test=\"scope == 'SELF' or scope == 'ASSIGNED'\"> AND j.owner_hr_id = #{userId}</if> "
            + "ORDER BY a.created_at DESC, a.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Application> selectApplicationPage(@Param("keyword") String keyword,
                                            @Param("status") String status,
                                            @Param("jobId") Long jobId,
                                            @Param("personId") Long personId,
                                            @Param("scope") String scope,
                                            @Param("deptIds") List<Long> deptIds,
                                            @Param("userId") long userId,
                                            @Param("limit") int limit,
                                            @Param("offset") long offset);

    /**
     * 投递分页总数。
     */
    @Select("<script>SELECT COUNT(*) FROM application a JOIN job j ON j.id = a.job_id WHERE 1 = 1"
            + "<if test=\"keyword != null and keyword != ''\"> AND (a.person_name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR a.job_title LIKE CONCAT('%', #{keyword}, '%'))</if>"
            + "<if test=\"status != null and status != ''\"> AND a.status = #{status}</if>"
            + "<if test=\"jobId != null\"> AND a.job_id = #{jobId}</if>"
            + "<if test=\"personId != null\"> AND a.person_id = #{personId}</if>"
            + "<if test=\"scope == 'DEPARTMENT'\"><choose><when test=\"deptIds != null and deptIds.size() > 0\">"
            + " AND j.dept_id IN <foreach collection=\"deptIds\" item=\"d\" open=\"(\" separator=\",\" close=\")\">#{d}</foreach>"
            + "</when><otherwise> AND 1 = 0</otherwise></choose></if>"
            + "<if test=\"scope == 'SELF' or scope == 'ASSIGNED'\"> AND j.owner_hr_id = #{userId}</if></script>")
    long countApplicationPage(@Param("keyword") String keyword,
                              @Param("status") String status,
                              @Param("jobId") Long jobId,
                              @Param("personId") Long personId,
                              @Param("scope") String scope,
                              @Param("deptIds") List<Long> deptIds,
                              @Param("userId") long userId);
}
