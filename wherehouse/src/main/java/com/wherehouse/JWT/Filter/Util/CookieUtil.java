package com.wherehouse.JWT.Filter.Util;

import org.springframework.stereotype.Component;
import jakarta.servlet.http.Cookie;

/*
 * 사용 위치 :
 * [시큐리티 컨텍스트]
 * 1. Login 필터 체인
 * 2. 게시판 필터 체인
 * 3. 로그인 성공 펠터 체인
 * 
 * [Spring MVC 컨텍스트]
 * 1. BoardService.writeReply()
 * */
@Component 
public class CookieUtil {

    /**
     * 쿠키 배열에서 특정 이름의 JWT를 추출합니다.
     *
     * @param cookies    HTTP 요청 쿠키 배열
     * @param cookieName JWT가 저장된 쿠키 이름
     * @return JWT 문자열 (없으면 null)
     */
    public String extractJwtFromCookies(Cookie[] cookies, String cookieName) {
    	
    	System.out.println("CookieUtil.extractJwtFromCookies()");
    	
        if (cookies != null) {
            for (Cookie cookie : cookies) {
            	
            	System.out.println("cookie.getName() : " + cookie.getName());
            	
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue(); // JWT 토큰 추출
                }
            }
        } else 
        	System.out.println("cookie is null");
        return null;
    }
}
