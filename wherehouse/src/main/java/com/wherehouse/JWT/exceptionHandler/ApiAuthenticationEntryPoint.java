package com.wherehouse.JWT.exceptionHandler;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * API 전용 JWT 인증 실패 핸들러
 *
 * /api/ 경로의 SecurityFilterChain에 등록되어,
 * fetch/AJAX 기반 API 호출에서 인증 실패 시 JSON 형태로 401 응답을 반환한다.
 *
 * View 기반 요청(members, boards)은 기존 JwtAuthenticationFailureHandler가 처리하며,
 * 이 핸들러는 API 체인에서만 동작한다.
 *
 * 분리 이유:
 *   JwtAuthenticationFailureHandler는 text/html로 <script>alert(...)를 반환하는데,
 *   JS의 fetch()에서 res.json() 파싱 시 이 HTML 응답이 파싱 실패를 유발한다.
 *   SecurityFilterChain별로 별도의 AuthenticationEntryPoint를 등록하는 것이
 *   Spring Security의 표준적인 확장 방식이다.
 */
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger logger = LoggerFactory.getLogger(ApiAuthenticationEntryPoint.class);
    private static final String AUTH_COOKIE_NAME = "Authorization";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {

        logger.warn("ApiAuthenticationEntryPoint - API 인증 실패 : {}", authException.getMessage());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // Authorization 쿠키 삭제
        Cookie expiredCookie = new Cookie(AUTH_COOKIE_NAME, null);
        expiredCookie.setPath("/");
        expiredCookie.setMaxAge(0);
        response.addCookie(expiredCookie);

        // JSON 에러 응답 반환
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write(
            "{\"code\": 401, \"status\": \"Unauthorized\", \"message\": \"로그인이 필요합니다.\"}"
        );
    }
}
