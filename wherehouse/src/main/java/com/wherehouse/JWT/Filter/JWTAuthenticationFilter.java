package com.wherehouse.JWT.Filter;

import java.io.IOException;
import java.security.Key;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import com.wherehouse.JWT.Filter.Util.CookieUtil;
import com.wherehouse.JWT.Filter.Util.JWTUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * JWT 인증 필터 - "/loginSuccess" 요청을 처리
 */

/*
 * "LoginFilter 클래스의 AuthenticationManager는 Provider를 필요로 하므로, 순환 참조 문제로 인해
 * LoginFilter를 @Component로 등록할 수 없다. 따라서 모든 필터 클래스를 SecurityConfig에서 @Bean으로
 * 등록하여 일관성을 유지한다."
 */

public class JWTAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JWTAuthenticationFilter.class);
    private static final String AUTH_COOKIE_NAME = "Authorization";

    private final CookieUtil cookieUtil;
    private final JWTUtil jwtUtil;

    public JWTAuthenticationFilter(CookieUtil cookieUtil, JWTUtil jwtUtil) {
        this.cookieUtil = cookieUtil;
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
                                    throws ServletException, IOException {

        logger.info("JWTAuthenticationFilter - 요청 처리 시작");
        logger.info("HTTP Method: {}, URL: {}", request.getMethod(), request.getRequestURL());

        // 1. 쿠키에서 JWT 추출
        Optional<String> tokenOpt = Optional.ofNullable(
            cookieUtil.extractJwtFromCookies(request.getCookies(), AUTH_COOKIE_NAME)
        );

        if (tokenOpt.isEmpty()) {
            logger.warn("JWT 토큰이 존재하지 않음");
            return;
        }
        String token = tokenOpt.get();

        // 2. JWT 서명 키 가져오기
        Optional<Key> keyOpt = jwtUtil.getSigningKeyFromToken(token);
        if (keyOpt.isEmpty()) {
            logger.warn("JWT 서명 키 없음");
            return;
        }
        Key key = keyOpt.get();

        // 3. JWT 검증
        if (!jwtUtil.isValidToken(token, key)) {
            logger.warn("JWT 토큰 검증 실패");
            return;
        }

        // 4. 사용자 ID 및 권한 추출
        String userId = jwtUtil.extractUserId(token, key);
        var authorities = jwtUtil.extractRoles(token, key).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        // 5. SecurityContext에 Authentication 설정
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);
        logger.info("JWT 인증 성공: 사용자 ID = {}", userId);
        // 필터 체인 계속 실행
        filterChain.doFilter(request, response);
    }
}
