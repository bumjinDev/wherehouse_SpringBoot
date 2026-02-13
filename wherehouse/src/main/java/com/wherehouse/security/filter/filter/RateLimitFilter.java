package com.wherehouse.security.filter.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherehouse.JWT.Filter.Util.CookieUtil;
import com.wherehouse.JWT.Filter.Util.JWTUtil;

import com.wherehouse.security.filter.service.BanService;
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
 * Redis 기반 Rate Limiting + IP 밴 필터 (DoS 방어)
 *
 * ============================================================================
 * [동작 위치]
 * ============================================================================
 * @Order(Ordered.HIGHEST_PRECEDENCE)로 등록되어 Spring Security Filter Chain보다
 * 먼저 실행된다. SecurityConfig.java 수정 없이 전체 API에 적용된다.
 *
 * [처리 흐름]
 * ============================================================================
 * 1. 정적 리소스 → 무조건 통과
 * 2. IP 밴 여부 확인 → 밴 상태면 403 즉시 반환
 * 3. 식별키 결정 (JWT userId 우선, IP 폴백)
 * 4. Redis INCR로 요청 카운트 증가
 * 5. 카운트 > 허용 횟수 → 429 반환 + 위반 카운트 증가
 * 6. 위반 카운트 >= 3 → IP 자동 밴 (7일)
 *
 * [Rate Limit 차등 제한]
 * ============================================================================
 * - POST /login            : 5회/60초
 * - POST, PUT, DELETE      : 20회/60초
 * - GET                    : 60회/60초
 *
 * [밴 정책]
 * ============================================================================
 * - 60초 윈도우 내에서 429 응답을 3회 누적하면 IP를 7일간 밴한다.
 * - 밴 상태 확인: Redis ban:ip:{ip} 키 존재 여부 (O(1))
 * - 밴 기록: Oracle BANNED_IP 테이블 + Redis 동시 기록
 * - 밴 해제: 관리자가 DB에서 DELETE 후 서버 재기동, 또는 7일 후 Redis TTL 자동 만료
 *
 * [장애 대응] fail-open
 * ============================================================================
 * Redis 장애 시 Rate Limiting과 밴 체크 모두 건너뛰고 요청을 통과시킨다.
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
    private final BanService banService;

    private static final String AUTH_COOKIE_NAME = "Authorization";

    // =========================================================================
    // [윈도우 설정]
    // =========================================================================
    private static final long WINDOW_SECONDS = 60;

    // =========================================================================
    // [카테고리별 허용 횟수]
    // =========================================================================
    private static final int LIMIT_LOGIN = 5;
    private static final int LIMIT_WRITE = 20;
    private static final int LIMIT_READ = 60;

    // =========================================================================
    // [밴 임계치] 60초 윈도우 내 429 응답 누적 횟수
    // =========================================================================
    private static final int BAN_THRESHOLD = 3;
    private static final long VIOLATION_WINDOW_SECONDS = 60;

    // =========================================================================
    // [제외 대상] 정적 리소스
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
        // Step 1: 정적 리소스 제외
        // =====================================================================
        if (isExcluded(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // =====================================================================
        // Step 2: IP 밴 여부 확인
        // =====================================================================
        String clientIp = request.getRemoteAddr();

        if (banService.isBanned(clientIp)) {
            log.warn("[BANNED] 밴 IP 요청 차단: ip={}, uri={}", clientIp, uri);
            sendForbiddenResponse(response);
            return;
        }

        // =====================================================================
        // Step 3: 식별키 결정 (JWT userId 우선, IP 폴백)
        // =====================================================================
        String identifier = resolveIdentifier(request, clientIp);

        // =====================================================================
        // Step 4: 카테고리 및 허용 횟수 결정
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
        // Step 5: Redis INCR + EXPIRE (Rate Limiting)
        // =====================================================================
        String redisKey = "rate:" + category + ":" + identifier;

        try {
            Long currentCount = redisTemplate.opsForValue().increment(redisKey);

            if (currentCount == null) {
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

                // =============================================================
                // Step 6: 위반 카운트 증가 + 밴 판정
                // =============================================================
                checkAndBan(clientIp);

                sendTooManyRequestsResponse(response, retryAfter);
                return;
            }

        } catch (Exception e) {
            log.error("[RATE_LIMIT] Redis 연산 실패, 요청 통과: key={}, error={}", redisKey, e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    // =========================================================================
    // [위반 카운트 증가 + 밴 판정]
    //
    // 429 응답이 발생할 때마다 IP 기반 위반 카운터를 증가시킨다.
    // 위반 카운터 키: rate:violation:{ip}
    // TTL: 60초 (위반 윈도우)
    //
    // 60초 내 위반 3회 누적 시 BanService.banIp()를 호출하여
    // Oracle BANNED_IP 테이블 + Redis ban:ip:{ip} 키에 동시 기록한다.
    // =========================================================================
    private void checkAndBan(String clientIp) {
        try {
            String violationKey = "rate:violation:" + clientIp;
            Long violations = redisTemplate.opsForValue().increment(violationKey);

            if (violations != null && violations == 1) {
                redisTemplate.expire(violationKey, VIOLATION_WINDOW_SECONDS, TimeUnit.SECONDS);
            }

            if (violations != null && violations >= BAN_THRESHOLD) {
                banService.banIp(clientIp,
                        "Rate limit 위반 " + violations + "회 누적 (60초 윈도우)");
                // 밴 등록 후 위반 카운터 삭제
                redisTemplate.delete(violationKey);
            }
        } catch (Exception e) {
            log.error("[BAN_CHECK] 위반 카운트 처리 실패: ip={}, error={}", clientIp, e.getMessage());
        }
    }

    // =========================================================================
    // [식별키 결정] JWT userId 추출 시도 → 실패 시 IP 폴백
    // =========================================================================
    private String resolveIdentifier(HttpServletRequest request, String clientIp) {
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

        return "ip:" + clientIp;
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
    // [429 Too Many Requests 응답]
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

    // =========================================================================
    // [403 Forbidden 응답 — 밴 IP]
    // =========================================================================
    private void sendForbiddenResponse(HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new HashMap<>();
        body.put("message", "접근이 차단된 IP입니다.");

        objectMapper.writeValue(response.getWriter(), body);
    }
}