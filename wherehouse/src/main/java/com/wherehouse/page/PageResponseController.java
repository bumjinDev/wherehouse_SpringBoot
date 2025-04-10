package com.wherehouse.page;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/* main.jsp 및 회원가입과 로그인 페이지, 로그 아웃 요청 그리고 분석 상세 내용을 처리하는 컨트롤러. */

@Controller
public class PageResponseController  {
	
	/* index.jsp 페이지 제공 : .....
	 * 	"JWTAuthenticationFilter" 에서 다른 요청과 마찬가지로 HTTPRequest 내 설정한 것으로
	 * 	Model 객체 삽입.*/
	 @GetMapping("/")
    public String pageIndex(Model model, HttpServletRequest httpRequest) {

        // 쿠키에서 Authorization 값 추출
        Cookie[] cookies = httpRequest.getCookies();
        String Authorization = null;

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("Authorization".equals(cookie.getName())) { // 쿠키 이름이 "Authorization"인지 확인
                	Authorization = cookie.getValue(); // 쿠키 값 가져오기
                    break;
                }
            }
        }

        model.addAttribute("Authorization", Authorization);
        
        return "recommand/index"; // JSP 또는 템플릿 엔진 경로 반환
    }
	
	/* main.jsp 페이지 제공 */
	@GetMapping("/main")
	public String pageMain() {
		
		System.out.println("pageMain  메소드 실행!");
		return "recommand/main";
	}
	
	/*  house_rec.jsp 페이지 요청 처리 */
	@GetMapping("/houserec")
	public String pageHouserec() {
		
		System.out.println(("pageHouserec() 실행!"));
		return "recommand/house_rec";
	}
	
	/* gu_map.jsp 페이지 요청 처리 */
	@GetMapping("/gumap")
	public String pageGumap() {
		
		System.out.println(("pageGumap 메소드 실행!"));
		return "recommand/gu_map";
	}
	
	/* informationPage.jsp : 분석 상세 내용 확인 페이지 */
	@GetMapping("/reinfo")
	public String pagereinfo() {
		
		System.out.println("컨트롤러 /page의 reinfo 메소드 실행!");
		return "recommand/description";
	}
}