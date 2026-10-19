package com.recruit.room;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 在线面试子系统（Owner：C）。库：recruit_room。
 * 核心：房间状态机（CREATED→WAITING→LIVE→FINISHED）、LiveKit 令牌签发（名额校验
 * ONE_TO_ONE=2/ONE_TO_MANY=6）、Webhook（HMAC 验签+幂等）、WS 聊天/字幕/系统事件
 * （/ws/room/{roomId}，JWT 首帧鉴权）、Egress 录制编排、防作弊事件采集。
 * 媒体信令走 LiveKit 直连，不经过本服务。
 */
@EnableFeignClients
@EnableDiscoveryClient
@MapperScan("com.recruit.room.mapper")
@SpringBootApplication
public class RoomServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoomServiceApplication.class, args);
    }
}
