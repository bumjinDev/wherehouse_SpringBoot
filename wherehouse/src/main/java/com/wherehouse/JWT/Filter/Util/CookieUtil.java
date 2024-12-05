package com.wherehouse.JWT.Filter.Util;

import jakarta.servlet.http.Cookie;

// @Component // 스프링 컨텍스트 내에서의 di 구현이 아닌 스프링 시큐리티 컨텍스트에서 @Bean 으로 등록될 것이기 때문에 별도의 시작과 동시에 스캔되는 기능을 사용할 필요가 없음.
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
