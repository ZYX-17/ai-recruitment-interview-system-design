package com.recruit.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 候选人/人才主档（recruit_resume.person）。
 *
 * <p>person 是 B 域的人才唯一档案；interview/room 只保存 person_id 与快照，
 * 不跨库建外键。合并后 status=MERGED，merged_to 指向保留方。</p>
 */
@Data
@TableName("person")
public class Person {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 关联 iam_user（候选人注册后绑定）；HR 导入的候选人可为空。 */
    private Long userId;

    private String name;

    /** 手机号密文；加密方案在 P7 去重阶段落地，当前只存 hash。 */
    private String phoneEnc;

    /** 手机号 HMAC-SHA256，用于查重，不存明文。 */
    private String phoneHash;

    private String email;

    private String city;

    /** 博士/硕士/本科/大专/其他。 */
    private String education;

    private Integer workYears;

    /** ACTIVE / MERGED / BLACKLIST。 */
    private String status;

    /** 合并后指向保留方 person.id。 */
    private Long mergedTo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Integer version;
}
