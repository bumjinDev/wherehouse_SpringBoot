package com.wherehouse.JWT.Filter;

import java.io.IOException;
import java.security.Key;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import com.wherehouse.JWT.Filter.Util.CookieUtil;
import com.wherehouse.JWT.Filter.Util.JWTUtil;
import com.wherehouse.redis.handler.RedisHandler;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtAuthProcessorFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthProcessorFilter.class);
    private static final String AUTH_COOKIE_NAME = "Authorization";

    private final CookieUtil cookieUtil;
    private final JWTUtil jwtUtil;
    private final Environment env;

    public JwtAuthProcessorFilter(
    		CookieUtil cookieUtil,
    		JWTUtil jwtUtil,
            Environment env
            )
    {
        this.cookieUtil = cookieUtil;
        this.jwtUtil = jwtUtil;
        this.env = env;
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
            cookieUtil.extractJwtFromCookies(request.getCookies(), AUTH_COOKIE_NAME));

        // 2. Cookie 내 JWT 토큰이 포함 되어 있는 지 확인.
        if (tokenOpt.isEmpty()) {
            logger.warn("HTTPRequest Message 내 JWT 토큰이 존재하지 않음");
            filterChain.doFilter(request, response);
            return;
        }

        String token = tokenOpt.get();  // Optional 객체 로부터 Token 문자열 가져 오기.
        Key key = jwtUtil.getSigningKeyFromToken(env.getProperty("JWT_SECRET_KEY"));

        // 4. JWT 검증
        if (!jwtUtil.isValidToken(token, key)) {
            logger.warn("JWT 토큰 검증 실패");
            filterChain.doFilter(request, response);
            return;
        }
        // 4. 사용자 ID 및 권한 추출
        String userId = jwtUtil.extractUserId(token);
        
        var authorities = jwtUtil.extractRoles(token, key).stream()
                .map(SimpleGrantedAuthority::new).toList();
        // 5. SecurityContext에 Authentication 설정
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        logger.info("JWT 인증 성공: 사용자 ID = {}", userId);
        // 필터 체인 계속 실행
        filterChain.doFilter(request, response);
    }
}