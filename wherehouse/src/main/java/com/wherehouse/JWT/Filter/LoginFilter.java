package com.wherehouse.JWT.Filter;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.wherehouse.JWT.Filter.Util.CookieUtil;
import com.wherehouse.JWT.Filter.Util.JWTUtil;
import com.wherehouse.redis.handler.RedisHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Key;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/* Security Filter 역할 : 
 * 	"DefaultSecurityFilterChain securityFilterChain()" 내 등록된 Filter Class 는 요청을 받으면 Authentication Manager 가 실질적으로 인증을 수행할 수 있게
 * 	Token 객체 를 만들어서 인증을 수행하도록 해야 한다. 그리고 동시에 Token 은 인증 매니저가 Provider 가 인증 완료한 정보를 담아서 반환하는 객체이기도 하므로 현재 인증이 미완료된 상태의
 * 	Token 으로서 생성 시 "AbstractAuthenticationToken.Collection<GrantedAuthority> authorities" 을 null 값으로 생성하여 인증 매니저에게 전달한다. 그리고 이후 인증이
 * 	완료 될 시 인증 매니저는 인증 프로바이더로써 전달 받은 Token 객체를 확인하여 인증 결과를 필터에 반환하고 필터는 해당 인증 매니저가 반환한 Token 내 포함된 결과 값인
 * 	UserDetails 를 사용하여 SecurityContext 에 인증 정보를 저장한다. 이후 요청이 처리될 때마다 SecurityContext 에서 사용자 인증 정보를 참조할 수 있다.
 *  */

/*  * 구체적으로, LoginFilter 는 사용자로부터 username 과 password 를 받아 UsernamePasswordAuthenticationToken 을 생성하고,
	 * 이 Token 을 AuthenticationManager 에 전달한다. AuthenticationManager 는 내부적으로 여러 AuthenticationProvider 를 사용하여
	 * 해당 Token 을 검증하고, 성공 시 인증된 Authentication 객체를 반환한다. 이 객체는 SecurityContext 에 저장되며,
	 * 이후 요청이 들어올 때 해당 인증 정보를 바탕으로 사용자 권한이 검증된다.
	 * 
	 * 또한 LoginFilter 에서 successfulAuthentication 메서드는 인증 성공 시 호출되며, JWT 토큰을 생성하여 응답으로 전달할 수 있다.
	 * 반면 unsuccessfulAuthentication 메서드는 인증 실패 시 호출되어 적절한 실패 처리를 수행할 수 있다.
 */
public class LoginFilter extends UsernamePasswordAuthenticationFilter{
	
	JWTUtil jwtUtil;
	CookieUtil cookieUtil;
	
	RedisHandler redisHandler;
	

	@Autowired
	private AuthenticationManager authenticationManager;
	
	public LoginFilter(AuthenticationManager authenticationManager, RedisHandler redisHandler, JWTUtil jwtUtil, CookieUtil cookieUtil) {
		
		this.authenticationManager = authenticationManager;
		this.redisHandler = redisHandler;
		this.jwtUtil = jwtUtil;
		this.cookieUtil = cookieUtil;
	}
	
	@Override
	public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException{
		
		System.out.println("LoginFilter.attemptAuthentication()");
		
		return authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken (
						request.getParameter("userid"),    	// 사용자 아이디 : private final Object principal;
						obtainPassword(request)    	// 비밀번호(자격 증명): private Object credentials;
						/* null */));
	}
	
	/* 인증 성공 시 스프링 시큐리티에 의해 자동으로 호출되는 메소드
	 * 	1. 인증 완료된 객체로부터 JWT을 생성해서 서버 내부 DB 내 저장한다.
	 * 	2. 로그인 요청에 대한 결과 페이지를 반환할 때 헤더 내부에 해당 JWT 토큰을 넣어서 반환한다. */
	@Override
	protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) {
	
		System.out.println("LoginFilter.successfulAuthentication()!");
	
		Cookie cookie = new Cookie("Authorization", createAndStoreJwt(authResult));
		cookie.setSecure(false);       // HTTPS에서만 작동
		cookie.setHttpOnly(true);     // JavaScript 접근 불가
		cookie.setPath("/");
		
		response.setCharacterEncoding("UTF-8");
		response.addCookie(cookie);
		
		try { response.sendRedirect("/wherehouse/loginSuccess"); }
		catch (IOException e) {	e.printStackTrace(); }
	}

	@Override
	protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) {
	    System.out.println("Authentication failed!");

	    request.setAttribute("exceptionType", failed.getClass().getSimpleName());
	    
	    RequestDispatcher dispatcher = request.getRequestDispatcher("/loginException");
	    
	    try { dispatcher.forward(request, response);
		} catch (ServletException | IOException e) { e.printStackTrace(); }
	}
	
	
	private String createAndStoreJwt(Authentication authResult) {
			
		System.out.println("LoginFilter.createAndStoreJwt()!");
		
		/* JWT Cliams */
		String username = authResult.getName();	
		String userId =  (String) authResult.getDetails();
		
		Collection<? extends GrantedAuthority> authorities = authResult.getAuthorities();
		List <String> roles = new ArrayList <String>();
		
		for (GrantedAuthority authority : authorities) {
		    roles.add(authority.getAuthority()); // 권한 이름을 roles 리스트에 추가
		}
		
		/* 랜덤 값으로 sha256 키 값을 만들고, 이 키 값과 "Authentication" 내 가져온 권한 목록들을 roles 값으로써 jwt 토큰을 생성 */
		Key key = jwtUtil.getSigningKey();
		String jwtToken = jwtUtil.generateToken(username, userId, roles, key);
		
		
		/* 생성된 토큰을 redis 서버에 저장, 이때 키 값을 jwtTolen 값, value 는 키 값을 인코딩 한 문자열 결과 값. */
		redisHandler.getValueOperations().set(jwtToken, jwtUtil.encodeKeyToBase64(key), jwtUtil.getRemainingDuration(jwtToken, key));
		
		/* 생성된 토큰을 jwt 에 저장 : 옛날 방식으로 더 이상 쓰지 않을 것이며 redis 구현 전까지만 유지!*/
		/*
		jwtTokenRepository.save(
				new JwtTokenEntity(
						jwtToken,
						jwtUtil.encodeKeyToBase64(key))
				);
		*/
		return jwtToken;
	}
}