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
 * JWT 인증 실패를 처리하는 핸들러
 *
 * 인증되지 않은 사용자가 보호된 리소스(`/list/**`, `/writepage` 등)에 접근했을 때 실행된다.
 * 
 * 실행 흐름:
 * 1. 사용자가 보호된 리소스에 접근을 시도.
 * 2. `RequestAuthenticationFilter`에서 요청을 가로채 JWT를 검증.
 *    - JWT가 없거나 유효하지 않으면 `SecurityContextHolder.clearContext();`를 실행하고 필터 체인을 계속 진행.
 * 3. SecurityContext가 비어 있기 때문에 `FilterSecurityInterceptor`가 접근을 거부함.
 * 4. `ExceptionTranslationFilter`가 이를 감지하고 `authenticationEntryPoint()`를 실행.
 * 5. `JwtAuthenticationFailureHandler`가 실행되어 적절한 응답을 반환.
 *
 * 주요 역할:
 * - 인증되지 않은 사용자가 보호된 리소스에 접근하면 `401 Unauthorized` 응답을 반환한다.
 * - 클라이언트가 로그인 페이지로 이동하도록 리다이렉트할 수도 있다.
 * - JWT가 만료된 경우 refresh token을 이용하여 새로운 access token을 발급할 수도 있다.
 * - API 요청이라면 JSON 형식의 에러 응답을 반환할 수도 있다.
 * 
 * 실무 활용 예:
 * - JWT가 유효하지 않은 경우 로그를 기록하고, 특정 패턴이 감지되면 보안 경고를 발생시킬 수 있다.
 * - 특정 리소스에서 인증 실패 시 추가적인 보안 검사를 수행할 수 있다.
 * - 만료된 JWT를 자동으로 갱신하는 기능을 추가할 수 있다.
 */
public class JwtAuthenticationFailureHandler implements AuthenticationEntryPoint {

	private final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFailureHandler.class);
	private final String AUTH_COOKIE_NAME = "Authorization";
	 
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException, ServletException {
		
		logger.warn("JwtAuthenticationFailureHandler - 인증 실패 : {}", authException.getMessage());
		
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // Authorization 쿠키 삭제
        Cookie expiredCookie = new Cookie(AUTH_COOKIE_NAME, null);
        expiredCookie.setPath("/");
        expiredCookie.setMaxAge(0);
        response.addCookie(expiredCookie);

        /**
         * 요청 URL 패턴에 따른 응답 형태 분기
         *
         * /api/ 경로 요청 (fetch/AJAX 기반 API 호출):
         *   → application/json 으로 에러 응답 반환.
         *     JS 에서 res.json() 파싱이 정상 동작하여 message 필드를 추출할 수 있다.
         *
         * 그 외 요청 (브라우저 View 기반 페이지 요청):
         *   → text/html 로 <script> alert 반환.
         *     브라우저가 직접 렌더링하여 사용자에게 안내 후 이전 페이지로 이동.
         */
        String requestUri = request.getRequestURI();

        if (requestUri.contains("/api/")) {
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write(
                "{\"code\": 401, \"status\": \"Unauthorized\", \"message\": \"로그인이 필요합니다.\"}"
            );
        } else {
            response.setContentType("text/html; charset=UTF-8");
            response.getWriter().write("<script>alert('올바른 로그인 사용자가 아닙니다.'); history.back();</script>");
        }
	}

}
