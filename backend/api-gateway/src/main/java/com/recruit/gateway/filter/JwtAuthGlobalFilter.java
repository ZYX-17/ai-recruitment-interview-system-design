package com.recruit.gateway.filter;

import com.recruit.common.result.ErrorCode;
import com.recruit.gateway.security.GatewayJwtVerifier;
import com.recruit.gateway.security.GatewayUser;
import com.recruit.gateway.security.TokenStateChecker;
import com.recruit.gateway.support.GatewayAttributes;
import com.recruit.gateway.support.GatewayErrorWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 网关 JWT 前置校验（docs/4 §3.1.4 第 3 位）。
 *
 * <p>流程：白名单放行 → 取 Bearer → RS256 验签 → 查黑名单/token_version。
 * 验签失败 401 {@code GW9001}；Redis 异常 503 {@code GW9002}（fail-closed，
 * 绝不因为缓存不可用而放行旧令牌）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final PathPatternParser PATTERN_PARSER = new PathPatternParser();

    private static final List<WhitelistRule> WHITELIST = List.of(
            new WhitelistRule(HttpMethod.POST, "/api/auth/login"),
            new WhitelistRule(HttpMethod.POST, "/api/auth/register"),
            new WhitelistRule(HttpMethod.POST, "/api/auth/refresh"),
            new WhitelistRule(HttpMethod.POST, "/api/auth/logout"),
            new WhitelistRule(null, "/actuator/health"),
            new WhitelistRule(null, "/actuator/health/**"),
            new WhitelistRule(HttpMethod.GET, "/api/interview/portal/jobs"),
            new WhitelistRule(HttpMethod.GET, "/api/interview/portal/jobs/**"),
            new WhitelistRule(null, "/api/interview/portal/offers/*"),
            new WhitelistRule(null, "/api/interview/portal/offers/*/**"),
            new WhitelistRule(null, "/api/interview/portal/async-invites/*"),
            new WhitelistRule(null, "/api/interview/portal/async-invites/*/**")
    );

    private final GatewayJwtVerifier jwtVerifier;
    private final TokenStateChecker tokenStateChecker;
    private final GatewayErrorWriter errorWriter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (isWhitelisted(request.getMethod(), request.getPath().pathWithinApplication())) {
            return chain.filter(exchange);
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return errorWriter.write(exchange, ErrorCode.GW_UNAUTHENTICATED,
                    ErrorCode.GW_UNAUTHENTICATED.getDefaultMessage());
        }

        GatewayUser user;
        try {
            user = jwtVerifier.verifyAccessToken(authorization.substring(BEARER_PREFIX.length()).trim());
        } catch (RuntimeException e) {
            log.debug("Access Token 校验失败: {}", e.getMessage());
            return errorWriter.write(exchange, ErrorCode.GW_UNAUTHENTICATED,
                    ErrorCode.GW_UNAUTHENTICATED.getDefaultMessage());
        }

        return tokenStateChecker.isActive(user)
                .flatMap(active -> {
                    if (!active) {
                        return errorWriter.write(exchange, ErrorCode.GW_UNAUTHENTICATED,
                                "登录状态已失效，请重新登录");
                    }
                    exchange.getAttributes().put(GatewayAttributes.USER, user);
                    return chain.filter(exchange);
                })
                .onErrorResume(e -> {
                    log.error("令牌状态校验失败（Redis 不可用），按 fail-closed 拒绝", e);
                    return errorWriter.write(exchange, ErrorCode.GW_SERVICE_UNAVAILABLE,
                            ErrorCode.GW_SERVICE_UNAVAILABLE.getDefaultMessage());
                });
    }

    private static boolean isWhitelisted(HttpMethod method, PathContainer path) {
        for (WhitelistRule rule : WHITELIST) {
            if (rule.matches(method, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    /**
     * 白名单规则：方法为 null 表示不限制方法。
     */
    private record WhitelistRule(HttpMethod method, PathPattern pattern) {

        WhitelistRule(HttpMethod method, String pattern) {
            this(method, PATTERN_PARSER.parse(pattern));
        }

        boolean matches(HttpMethod requestMethod, PathContainer path) {
            if (method != null && !method.equals(requestMethod)) {
                return false;
            }
            return pattern.matches(path);
        }
    }
}
