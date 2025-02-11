package com.wherehouse.JWT.Filter;

import java.io.IOException;
import java.security.Key;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import com.wherehouse.JWT.Filter.Util.CookieUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * RequestAuthenticationFilter는 인증이 필요한 리소스에 대한 JWT 검증 및 인증 상태 관리.
 */
public class RequestAuthenticationFilter extends OncePerRequestFilter {

    private CookieUtil cookieUtil;
    private JwtComponent jwtComponent;
	
    public RequestAuthenticationFilter(CookieUtil cookieUtil, JwtComponent jwtComponent) {
    	
    	this.cookieUtil = cookieUtil;
    	this.jwtComponent = jwtComponent;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        System.out.println("RequestAuthenticationFilter.doFilterInternal()");
        System.out.printf("HTTP Method: %s, URL: %s%n", request.getMethod(), request.getRequestURL());

        // 쿠키에서 JWT 토큰 추출
        String token = cookieUtil.extractJwtFromCookies(request.getCookies(), "Authorization");
        
        if (token == null) {
            handleInvalidToken(request, response, "JWT 토큰이 존재하지 않음!");
            return;
        }
        
        try {
            // JWT 서명 키 가져오기
            Key signingKey = jwtComponent.getSigningKey(token);

            // JWT 토큰 검증 및 사용자 정보 설정
            jwtComponent.validateToken(token, signingKey, request);

            // 사용자 권한 가져오기
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) request.getAttribute("roles");

            // 인증 객체 생성 및 SecurityContextHolder 설정
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                request.getAttribute("userId"), // 사용자 ID
                null, // 비밀번호는 필요 없음
                roles.stream().map(SimpleGrantedAuthority::new).toList()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception e) {
            handleInvalidToken(request, response, "JWT 검증 실패!");
            return;
        }

        // 필터 체인 계속 실행
        filterChain.doFilter(request, response);
    }

    /**
     * 유효하지 않은 JWT 토큰 처리: 쿠키 삭제 및 JS 응답 반환
     */
    private void handleInvalidToken(HttpServletRequest httpRequest, HttpServletResponse response, String errorMessage) throws IOException {
        System.out.println(errorMessage);

        // Authorization 쿠키 삭제
        Cookie cookie = new Cookie("Authorization", null);
        cookie.setPath("/");
        cookie.setMaxAge(0); // 즉시 만료
        response.addCookie(cookie);

        // 이전 페이지로 리다이렉트 (Referer 헤더 사용)
        String referer = httpRequest.getHeader("Referer");
        if (referer == null || referer.isEmpty()) {
            referer = "/"; // Referer가 없으면 기본 경로로 리다이렉트
        }

        System.out.println("referer : " + referer);
        // JavaScript 응답 작성
        response.setContentType("text/html; charset=UTF-8");
        response.getWriter().write(
            "<script>" +
            "alert('권한이 없습니다.');" +
            "window.location.href = '" + referer + "';" +
            "</script>"
        );
    }
}

