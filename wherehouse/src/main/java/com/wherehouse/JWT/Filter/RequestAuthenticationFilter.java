package com.wherehouse.JWT.Filter;

import java.io.IOException;
import java.security.Key;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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
 * RequestAuthenticationFilter는 JWT 기반 인증을 수행하는 필터
 */
/*
 * "LoginFilter 클래스의 AuthenticationManager는 Provider를 필요로 하므로, 순환 참조 문제로 인해
 * LoginFilter를 @Component로 등록할 수 없다. 따라서 모든 필터 클래스를 SecurityConfig에서 @Bean으로
 * 등록하여 일관성을 유지한다."
 */
public class RequestAuthenticationFilter extends OncePerRequestFilter {

    private final Logger logger = LoggerFactory.getLogger(RequestAuthenticationFilter.class);
    private final String AUTH_COOKIE_NAME = "Authorization";

    private final CookieUtil cookieUtil;
    private final JWTUtil jwtUtil;

    public RequestAuthenticationFilter(CookieUtil cookieUtil, JWTUtil jwtUtil) {
    	
    	this.cookieUtil = cookieUtil;
        this.jwtUtil = jwtUtil;
        
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
                                    throws ServletException, IOException {

        logger.info("RequestAuthenticationFilter - 요청 처리 시작");
        logger.info("HTTP Method: {}, URL: {}", request.getMethod(), request.getRequestURL());

        // 1. JWT 추출
        String token = extractToken(request);
        
        if (token == null) {
        	
            logger.warn("JWT 토큰이 존재하지 않음!");
            // 인증 정보 없이 필터 체인 진행 (SecurityContextHolder는 그대로 유지)
            filterChain.doFilter(request, response);
            return;
        }
        // 2. JWT Key(String)을 Redis 조회 후 검증 및 사용자 인증 처리
        Optional<Key> signingKeyOpt = jwtUtil.getSigningKeyFromToken(token);
        if (signingKeyOpt.isEmpty() || !jwtUtil.isValidToken(token, signingKeyOpt.get())) {
        	
            logger.warn("JWT 검증 실패!");
            // 인증 정보 없이 필터 체인 진행 (SecurityContextHolder는 그대로 유지)
            filterChain.doFilter(request, response);
            return;
        }
        // 3. SecurityContext에 인증 정보 설정
        authenticateUser(token, signingKeyOpt.get());
        // 4. 필터 체인으로 요청을 전달
        filterChain.doFilter(request, response);
    }


    /**
     * HTTP 요청에서 "Authorization" 쿠키를 통해 JWT 토큰을 추출
     */
    private String extractToken(HttpServletRequest request) {
        return cookieUtil.extractJwtFromCookies(request.getCookies(), AUTH_COOKIE_NAME);
    }

    /**
     * JWT 토큰에서 사용자 정보를 추출하여 SecurityContext에 설정
     */
    private void authenticateUser(String token, Key key) {
    	
        String userId = jwtUtil.extractUserId(token, key);
        List<String> roles = jwtUtil.extractRoles(token, key);

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                userId,
                null,
                roles.stream()
                     .map(SimpleGrantedAuthority::new)
                     .collect(Collectors.toList())
            );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        logger.info("JWT 인증 성공: 사용자 ID = {}", userId);
    }
}
