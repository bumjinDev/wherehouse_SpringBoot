package com.wherehouse.page;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
/* main.jsp 및 회원가입과 로그인 페이지, 로그 아웃 요청 그리고 분석 상세 내용을 처리하는 컨트롤러. */

@Controller
public class PageResponseController  {

	@Value("${kakao.api.sdk-key}")
	private String kakaoSdkKey;

	@Value("${kakao.api.javascript-key}")
	private String kakaoJavascriptKey;

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
	public String pageHouserec(Model model) {
		System.out.println(("pageHouserec() 실행!"));
		model.addAttribute("kakaoSdkKey", kakaoSdkKey);
		model.addAttribute("kakaoJavascriptKey", kakaoJavascriptKey);
		return "recommand/house_rec";
	}
	
	/* gu_map.jsp 페이지 요청 처리 */
	@GetMapping("/gumap")
	public String pageGumap(Model model) {
		System.out.println(("pageGumap 메소드 실행!"));
		model.addAttribute("kakaoSdkKey", kakaoSdkKey);
		model.addAttribute("kakaoJavascriptKey", kakaoJavascriptKey);
		return "recommand/gu_map";
	}
	
	/* informationPage.jsp : 분석 상세 내용 확인 페이지 */
	@GetMapping("/reinfo")
	public String pagereinfo() {

		System.out.println("컨트롤러 /page의 reinfo 메소드 실행!");
		return "recommand/description";
	}

	// ========================================================================
	// 방문 예약 회원 페이지 4종 (설계 명세서 섹션 6.8, 7.9~7.13)
	// SecurityConfig.visitReservationPageFilterChain 이 /visit/me/** 에 JWT 인증을 강제하므로
	// 본 컨트롤러는 @AuthenticationPrincipal 만으로 안전하게 currentUserId 를 추출한다.
	// ========================================================================

	@GetMapping("/visit/me/reservations")
	public String visitMeReservations(@AuthenticationPrincipal String userId, Model model) {
		model.addAttribute("currentUserId", normalizeUserId(userId));
		return "visit/me_reservations";
	}

	@GetMapping("/visit/me/subscriptions")
	public String visitMeSubscriptions(@AuthenticationPrincipal String userId, Model model) {
		model.addAttribute("currentUserId", normalizeUserId(userId));
		return "visit/me_subscriptions";
	}

	@GetMapping("/visit/me/slots")
	public String visitMeSlots(@AuthenticationPrincipal String userId, Model model) {
		model.addAttribute("currentUserId", normalizeUserId(userId));
		return "visit/me_slots";
	}

	@GetMapping("/visit/me/notifications")
	public String visitMeNotifications(@AuthenticationPrincipal String userId, Model model) {
		model.addAttribute("currentUserId", normalizeUserId(userId));
		return "visit/me_notifications";
	}

	/** AnonymousAuthenticationFilter 가 주입하는 "anonymousUser" 를 null 로 정규화. */
	private String normalizeUserId(String userId) {
		if (userId == null || "anonymousUser".equals(userId)) return null;
		return userId;
	}
}