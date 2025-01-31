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

/* "/loginSucces" 를 위한 JWT 처리 */
/* "/loginSuccess" 요청에 대한 JWT 인증 필터 */
public class JWTAuthenticationFilter extends OncePerRequestFilter {

    private CookieUtil cookieUtil;
    private JwtComponent jwtComponent;

	public JWTAuthenticationFilter(CookieUtil cookieUtil, JwtComponent jwtComponent) {
	
		this.cookieUtil = cookieUtil;
		this.jwtComponent = jwtComponent;
	}
	
    @Override
    protected void doFilterInternal(HttpServletRequest httpRequest, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        System.out.println("JWTAuthenticationFilter.doFilterInternal()! ");
        System.out.printf("HTTP Method: %s, URL: %s%n", httpRequest.getMethod(), httpRequest.getRequestURL());

        String token = cookieUtil.extractJwtFromCookies(httpRequest.getCookies(), "Authorization");

        System.out.println("token : " + token);
        
        // 로그인 시 JWT 토큰이 없을 경우 처리
        if (token == null) {
            handleInvalidToken(response, "JWT 토큰이 쿠키에 존재하지 않음!");
            return;
        }

        // 데이터베이스에서 JWT 토큰의 HMAC 서명 키 가져오기
        Key key = null;
        
        try { key = jwtComponent.getSigningKey(token); }
        catch (Exception e) {
        	
        	handleInvalidToken(response, "JWT 서명 키 없음.");
            return;
        }

        // Key 객체를 사용해서 서명을 검증하고 JWT 토큰 유효성을 확인, 이때 유효기간은 검증을 하지 않음.
        try {
        	
            if (!jwtComponent.validateToken(token, key, httpRequest)) {
                throw new SecurityException("검증 결과 토큰이 유효하지 않음");
            }

            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) httpRequest.getAttribute("roles");
            
            System.out.printf("JWT Claims: UserId=%s, UserName=%s, Roles=%s%n",
            		(String) httpRequest.getAttribute("userId"),
            		(String) httpRequest.getAttribute("userName"),
            		roles.get(0),
            		(String) httpRequest.getAttribute("jwtToken"));
            
            // 사용자 인증 객체 생성
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            	(String) httpRequest.getAttribute("userId"), // 사용자 ID
                null,   // 자격 증명 (패스워드)
                roles.stream().map(SimpleGrantedAuthority::new).toList() // 권한
            );

            // SecurityContextHolder에 인증 객체 저장
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
        } catch (Exception e) {
        	
            handleInvalidToken(response, "JWT 토큰 검증 실패!");
            return;
        }

        System.out.println("jwtToken Authentication success! \n");
        filterChain.doFilter(httpRequest, response);
    }

    /* handleInvalidToken() : 만약 JWT (로그인 상태) 요청이 들어올 시 내부 쿠키 값이 잘못된 것일 경우 해당 쿠키 값을 부러우저에서 아에 없애버리도록 response 객체에 전달. */ 
    private void handleInvalidToken(HttpServletResponse response, String message) throws IOException {
    	
        System.out.println(message);
        
        // Authorization 쿠키 삭제 (쿠키 만료 처리)
        Cookie cookie = new Cookie("Authorization", null);
        cookie.setPath("/"); // 쿠키 경로 설정
        cookie.setMaxAge(0); // 즉시 만료
        response.addCookie(cookie);

        // 메인 페이지로 리다이렉트
        response.sendRedirect("/wherehouse/");
    }
}
