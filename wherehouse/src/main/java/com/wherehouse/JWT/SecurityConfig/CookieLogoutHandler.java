package com.wherehouse.JWT.SecurityConfig;
import com.wherehouse.board.dao.BoardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import com.wherehouse.redis.config.RedisConfig;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CookieLogoutHandler implements LogoutHandler {

    private static final Logger logger = LoggerFactory.getLogger(CookieLogoutHandler.class);

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
        
        /* 서버 측에서 쿠키를 보관중인 dbms 에 접근해서 삭제.. */
        /* jwtTokenRepository.deleteById(Authorization); */
        
        Cookie cookie = new Cookie("Authorization", null);
        cookie.setPath("/");      // Path 설정
        cookie.setMaxAge(0);      // 즉시 만료
        response.addCookie(cookie);
        System.out.println("HostOnly 쿠키 삭제 완료");
        
        if (authentication != null) {
            logger.info("로그아웃 사용자: " + authentication.getName()); }
    }
}
