package com.wherehouse.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherehouse.JWT.Filter.Util.CookieUtil;
import com.wherehouse.JWT.Filter.Util.JWTUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 Rate Limiting 필터 (DoS 방어)
 *
 * ============================================================================
 * [동작 위치]
 * ============================================================================
 * 서블릿 컨테이너 레벨에서 Spring Security Filter Chain보다 먼저 실행된다.
 * @Order(Ordered.HIGHEST_PRECEDENCE)로 등록되어 모든 필터 중 가장 먼저 요청을 가로챈다.
 * 따라서 SecurityConfig.java의 6개 SecurityFilterChain을 개별 수정할 필요 없이
 * 전체 API에 대해 Rate Limiting이 적용된다.
 *
 * [알고리즘] Fixed Window Counter
 * ============================================================================
 * 요청 도착 시 Redis에 INCR 연산을 수행한다.
 * - 반환값이 1이면(윈도우 내 첫 요청) EXPIRE로 TTL을 설정한다.
 * - 반환값이 허용 횟수를 초과하면 429 Too Many Requests를 반환한다.
 * - Redis 연산: 최대 INCR 1회 + EXPIRE 1회 = 2 RTT
 *
 * [식별키 설계]
 * ============================================================================
 * 1차: Cookie에서 JWT를 추출하여 userId 기반 식별 시도
 * 2차: JWT가 없거나 파싱 실패 시 IP 주소(request.getRemoteAddr()) 기반 폴백
 * 키 형식: rate:{category}:{type}:{identifier}
 *   예: rate:write:user:testuser01, rate:read:ip:192.168.0.1
 *
 * [차등 제한]
 * ============================================================================
 * - POST /login            : 5회/60초  (Brute Force 방지)
 * - POST, PUT, DELETE      : 20회/60초 (상태 변경 API)
 * - GET                    : 60회/60초 (조회 API)
 * ============================================================================
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CookieUtil cookieUtil;
    private final JWTUtil jwtUtil;

    private static final String AUTH_COOKIE_NAME = "Authorization";

    // =========================================================================
    // [윈도우 설정] 60초 고정 윈도우
    // =========================================================================
    private static final long WINDOW_SECONDS = 60;

    // =========================================================================
    // [카테고리별 허용 횟수]
    // =========================================================================
    private static final int LIMIT_LOGIN = 5;
    private static final int LIMIT_WRITE = 20;
    private static final int LIMIT_READ = 60;

    // =========================================================================
    // [Rate Limiting 제외 대상] 정적 리소스 경로 접두사
    // =========================================================================
    private static final Set<String> EXCLUDED_PREFIXES = Set.of(
            "/js/", "/css/", "/images/", "/favicon.ico"
    );

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();

        // =====================================================================
        // 정적 리소스 제외
        // =====================================================================
        if (isExcluded(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // =====================================================================
        // 식별키 결정: JWT userId 우선, 실패 시 IP 폴백
        // =====================================================================
        String identifier = resolveIdentifier(request);

        // =====================================================================
        // 카테고리 및 허용 횟수 결정
        // =====================================================================
        String method = request.getMethod().toUpperCase();
        String category;
        int limit;

        if ("POST".equals(method) && "/login".equals(uri)) {
            category = "login";
            limit = LIMIT_LOGIN;
        } else if (Set.of("POST", "PUT", "DELETE").contains(method)) {
            category = "write";
            limit = LIMIT_WRITE;
        } else {
            category = "read";
            limit = LIMIT_READ;
        }

        // =====================================================================
        // Redis INCR + EXPIRE
        // =====================================================================
        String redisKey = "rate:" + category + ":" + identifier;

        try {
            Long currentCount = redisTemplate.opsForValue().increment(redisKey);

            if (currentCount == null) {
                // Redis 연결 실패 시 요청을 차단하지 않고 통과시킨다 (fail-open)
                log.warn("[RATE_LIMIT] Redis INCR 반환값 null, 요청 통과: key={}", redisKey);
                filterChain.doFilter(request, response);
                return;
            }

            // 첫 요청이면 TTL 설정
            if (currentCount == 1) {
                redisTemplate.expire(redisKey, WINDOW_SECONDS, TimeUnit.SECONDS);
            }

            // 허용 횟수 초과
            if (currentCount > limit) {
                Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
                long retryAfter = (ttl != null && ttl > 0) ? ttl : WINDOW_SECONDS;

                log.warn("[RATE_LIMIT] 요청 차단: key={}, count={}, limit={}, retryAfter={}s",
                        redisKey, currentCount, limit, retryAfter);

                sendTooManyRequestsResponse(response, retryAfter);
                return;
            }

        } catch (Exception e) {
            // Redis 장애 시 요청을 차단하지 않고 통과시킨다 (fail-open)
            log.error("[RATE_LIMIT] Redis 연산 실패, 요청 통과: key={}, error={}", redisKey, e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    // =========================================================================
    // [식별키 결정] JWT userId 추출 시도 → 실패 시 IP 폴백
    // =========================================================================
    private String resolveIdentifier(HttpServletRequest request) {
        try {
            Cookie[] cookies = request.getCookies();
            String token = cookieUtil.extractJwtFromCookies(cookies, AUTH_COOKIE_NAME);

            if (token != null) {
                String userId = jwtUtil.extractUserId(token);
                if (userId != null && !userId.isBlank()) {
                    return "user:" + userId;
                }
            }
        } catch (Exception e) {
            // JWT 파싱 실패는 정상 케이스 (비인증 요청)
        }

        return "ip:" + request.getRemoteAddr();
    }

    // =========================================================================
    // [정적 리소스 판별]
    // =========================================================================
    private boolean isExcluded(String uri) {
        for (String prefix : EXCLUDED_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // [429 응답 생성]
    // =========================================================================
    private void sendTooManyRequestsResponse(HttpServletResponse response, long retryAfter)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(retryAfter));

        Map<String, Object> body = new HashMap<>();
        body.put("message", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        body.put("retryAfter", retryAfter);

        objectMapper.writeValue(response.getWriter(), body);
    }
}
