package com.wherehouse.JWT.exceptionHandler;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.*;

/* Spring Security 사용자 로그인 인증 절차 중 에러 발생 시 필터에 등록해서 예외 처리하는 것.  */
public class LoginAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private static final Logger logger = LoggerFactory.getLogger(LoginAuthenticationEntryPoint.class);
	
	@Override
	public void commence(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticationException authException) throws IOException, ServletException {
		// TODO Auto-generated method stub
		
		logger.error("Unauthorized request: {}", authException.getMessage());
		
		 response.setContentType("application/json");
	     response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
	     response.getWriter().write("{ \"error\": \"Unauthorized\", \"message\": \"" + authException.getMessage() + ", 인증 과정 중 오류 발생." + "\" }");
	}

}
