package com.wherehouse.JWT.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import com.wherehouse.JWT.Repository.JwtTokenRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CookieLogoutHandler implements LogoutHandler {

	@Autowired
	JwtTokenRepository jwtTokenRepository;
	
    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        // 로그아웃 수행 전 실행될 로직
        System.out.println("CustomLogoutHandler - 로그아웃 수행 전 작업 실행");

     // 쿠키에서 Authorization 값 추출
        Cookie[] cookies = request.getCookies();
        String Authorization = null;

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("Authorization".equals(cookie.getName())) { // 쿠키 이름이 "Authorization"인지 확인
                	Authorization = cookie.getValue(); // 쿠키 값 가져오기
                    break;
                }
            }
        }

        // 쿠키 값 출력
        System.out.println("Cookie Authorization: " + Authorization);
        
        jwtTokenRepository.deleteById(Authorization);
        
        Cookie cookie = new Cookie("Authorization", null);
        cookie.setPath("/");      // Path 설정
        cookie.setMaxAge(0);      // 즉시 만료
        response.addCookie(cookie);
        System.out.println("HostOnly 쿠키 삭제 완료");
        
        if (authentication != null) {
            System.out.println("로그아웃 사용자: " + authentication.getName());
        }
    }
}
