package com.recruit.gateway.filter;

import com.recruit.common.constant.RedisKeys;
import com.recruit.common.result.ErrorCode;
import com.recruit.gateway.config.GatewaySecurityProperties;
import com.recruit.gateway.ratelimit.RedisTokenBucketRateLimiter;
import com.recruit.gateway.support.GatewayErrorWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * 网关三层限流（docs/4 §10.3 / S9）：全局 200 QPS、单 IP 50 QPS、登录 100/min。
 *
 * <p>按顺序消耗三个桶，任一不足即 429 {@code GW9003} 并返回 {@code Retry-After}。
 * Redis 异常时 503 {@code GW9002} fail-closed，不因限流依赖故障而无限放行。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String ACTUATOR_PREFIX = "/actuator/";

    private final RedisTokenBucketRateLimiter rateLimiter;
    private final GatewaySecurityProperties properties;
    private final GatewayErrorWriter errorWriter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        if (path.startsWith(ACTUATOR_PREFIX)) {
            return chain.filter(exchange);
        }

        GatewaySecurityProperties.RateLimit limits = properties.getRateLimit();
        String ip = clientIp(request);
        List<Bucket> buckets = new ArrayList<>();
        buckets.add(new Bucket(RedisKeys.authRate("gw-global", "bucket"),
                limits.getGlobalQps(), limits.getGlobalQps()));
        buckets.add(new Bucket(RedisKeys.authRate("gw-ip", ip),
                limits.getIpQps(), limits.getIpQps()));
        if (HttpMethod.POST.equals(request.getMethod()) && LOGIN_PATH.equals(path)) {
            buckets.add(new Bucket(RedisKeys.authRate("login", ip),
                    limits.getLoginPerMinute(), limits.getLoginPerMinute() / 60.0));
        }
        return acquire(buckets, 0, exchange, chain);
    }

    private Mono<Void> acquire(List<Bucket> buckets, int index,
                               ServerWebExchange exchange, GatewayFilterChain chain) {
        if (index >= buckets.size()) {
            return chain.filter(exchange);
        }
        Bucket bucket = buckets.get(index);
        return rateLimiter.tryAcquire(bucket.key(), bucket.capacity(), bucket.ratePerSecond())
                .flatMap(decision -> {
                    if (decision.allowed()) {
                        return acquire(buckets, index + 1, exchange, chain);
                    }
                    return errorWriter.write(exchange, ErrorCode.GW_RATE_LIMITED,
                            ErrorCode.GW_RATE_LIMITED.getDefaultMessage(),
                            headers -> headers.set(HttpHeaders.RETRY_AFTER,
                                    String.valueOf(decision.retryAfterSeconds())));
                })
                .onErrorResume(e -> {
                    log.error("限流 Redis 不可用，按 fail-closed 拒绝", e);
                    return errorWriter.write(exchange, ErrorCode.GW_SERVICE_UNAVAILABLE,
                            ErrorCode.GW_SERVICE_UNAVAILABLE.getDefaultMessage());
                });
    }

    /**
     * 只使用 TCP 对端地址，不信任客户端自带的 {@code X-Forwarded-For}，避免伪造 IP 绕过限流。
     */
    private static String clientIp(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 40;
    }

    /**
     * 一个限流桶。
     */
    private record Bucket(String key, int capacity, double ratePerSecond) {
    }
}
