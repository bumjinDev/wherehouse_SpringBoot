package com.wherehouse.JWT.Filter;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherehouse.JWT.Filter.Util.JWTUtil;
import com.wherehouse.redis.handler.RedisHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.IOException;
import java.security.Key;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 로그인 요청을 처리하는 필터 (UsernamePasswordAuthenticationFilter 상속)
 * - 사용자가 아이디/비밀번호로 로그인하면 인증을 처리
 * - JWT 토큰을 생성하여 응답 쿠키에 저장
 */
/*
 * "LoginFilter 클래스의 AuthenticationManager는 Provider를 필요로 하므로, 순환 참조 문제로 인해
 * LoginFilter를 @Component로 등록할 수 없다. 따라서 모든 필터 클래스를 SecurityConfig에서 @Bean으로
 * 등록하여 일관성을 유지한다."
 */
public class LoginFilter extends UsernamePasswordAuthenticationFilter {

	private static final Logger logger = LoggerFactory.getLogger(LoginFilter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
	
	private final RedisHandler redisHandler;
    private final JWTUtil jwtUtil;
    
    
    public LoginFilter(AuthenticationManager authenticationManager, RedisHandler redisHandler, JWTUtil jwtUtil) {
        
    	super.setAuthenticationManager(authenticationManager);
    	
        this.redisHandler = redisHandler;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 사용자의 인증 요청을 처리
     * @return Authentication 인증 결과 객체 반환
     */
    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {

    	logger.info("LoginFilter: 로그인 요청 받음");
    	 
        LoginRequest loginRequest = null;
        
		try {	loginRequest = objectMapper.readValue(request.getInputStream(), LoginRequest.class); }
		catch (IOException e) {	 logger.warn("LoginFilter.attemptAuthentication - Login 인증 정보 Json 파싱 에러 : {}", e.getMessage());	}
       
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                		loginRequest.getUsername(),
                		loginRequest.getPassword());

        return getAuthenticationManager().authenticate(authToken);
    }

    /**
     * 인증 성공 시 JWT 생성 후 응답 쿠키에 추가
     */
    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain, Authentication authResult) {

    	logger.info("LoginFilter.successfulAuthentication - 사용자 : {}", authResult.getName());
    	
        // JWT 생성 후 브러우저에 반환할 HostOnly 쿠키에 추가
        addJwtToCookie(response, generateAndStoreJwt(authResult));

        // 로그인 성공 후 페이지 이동
        try { response.sendRedirect("/wherehouse/loginSuccess"); }
        catch (IOException e) { e.printStackTrace(); }
    }

    /**
     * 인증 실패 시 예외 처리 (에러 페이지로 이동)
     */
    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                              AuthenticationException failed) {

    	logger.warn("LoginFilter: 로그인 실패 - 이유: {}", failed.getMessage());

        request.setAttribute("exceptionType", failed.getClass().getSimpleName());

        try { request.getRequestDispatcher("/loginException").forward(request, response); }
        catch (ServletException | IOException e) { e.printStackTrace(); }
    }

    /**
     * JWT 생성 후 Redis에 저장
     * @return 생성된 JWT 토큰 문자열
     */
    private String generateAndStoreJwt(Authentication authResult) {
    	
        String username = authResult.getName();
        String userId = (String) authResult.getDetails();

        // 사용자 역할(Authorities) 리스트
        List<String> roles = authResult.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // JWT 서명 키 생성하기.
        Key key = jwtUtil.generateSigningKey();

        // JWT 토큰 생성
        String jwtToken = jwtUtil.generateToken(username, userId, roles, key);

        // JWT 서명 키를 Redis에 저장 (JWT 만료 시간과 동일한 TTL 적용)
        redisHandler.getValueOperations().set(jwtToken,
                jwtUtil.encodeKeyToBase64(key));

        return jwtToken;
    }

    /**
     * JWT 토큰을 응답 쿠키에 추가
     */
    private void addJwtToCookie(HttpServletResponse response, String jwtToken) {
        Cookie cookie = new Cookie("Authorization", jwtToken);
        cookie.setSecure(false);  // HTTPS에서만 사용
        cookie.setHttpOnly(true); // JavaScript 접근 방지
        cookie.setPath("/");      // 전체 경로에서 유효

        response.addCookie(cookie);
    }
    
    
    @Data
    @AllArgsConstructor
    class LoginRequest {
    	private String username;
    	private String password;
    }
}
