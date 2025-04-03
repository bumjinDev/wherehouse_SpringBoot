package com.wherehouse.members.controller;

import java.util.Map;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.wherehouse.members.service.IMemberService;
import org.slf4j.Logger;

/**
 * MemberMembersController는 회원 관련 요청을 처리하는 컨트롤러입니다.
 * - 로그인 성공, 회원 정보 수정, 회원 가입 등 다양한 회원 관련 작업을 처리합니다.
 * - 각 요청은 적절한 서비스 레이어로 위임되어 로직을 수행합니다.
 */

//@ControllerAdvice
@Controller
public class MembersViewController {

	private static final Logger logger = LoggerFactory.getLogger(MembersViewController.class);
	
	IMemberService memberService;
	
	public MembersViewController(IMemberService memberService) {
		this.memberService = memberService;
	}
	/* login.jsp : 로그인 요청 페이지 (실제 로그인 요청은 POST 요청으로써 필터 클래스에 요청됨.)*/
	@GetMapping("/login")
	public String pageLogin() {
		
		System.out.println("pageLogin 메소드 실행!");
		return "members/login";
	}
	
	 /**
     * 로그인 성공 후 사용자 정보를 화면에 전달합니다, 로그인 과정은 필터에서 진행 하므로 현재 컨트롤러 에 없음.
     * 
     * - 사용자의 ID와 닉네임을 모델에 추가하여 뷰에 전달합니다.
     * - 이전 요청에서 HttpServletRequest를 통해 설정된 사용자 정보를 사용합니다.
     *
     * @param httpRequest 사용자 요청 객체 (사용자 ID와 닉네임 포함)
     * @param model       뷰로 전달할 데이터 추가
     * @return 로그인 성공 페이지 경로
     */
	
	/* join.jsp : 회원가입 요청 페이지 */
	@GetMapping("/members/join")
	public String pageJoin() {
		
		logger.info("MembersController.pageJoin!");  // 로그 추가
		return "members/join";
	}
	
	@GetMapping("/loginSuccess")
	public String loginSueccess(
			@CookieValue(name = "Authorization", required = true) String jwt,
			Model model) {
		
	    logger.info("MembersController.loginSuccess()!");  // 로그 추가
	    logger.info("JWT: {} : ", jwt);
	    
	    Map<String, String> sessionInfo = memberService.validLoginSuccess(jwt);
	    
	    model.addAttribute("userId", sessionInfo.get("userId"));
	    model.addAttribute("userName", sessionInfo.get("userName"));
	    
	    return "members/loginSuccess";
	}
	

	
	 /**
     * 회원 정보 수정 페이지 요청 처리.
     * 
     * - 현재 로그인한 회원의 정보를 가져와 수정 페이지에 전달합니다.
     * - MembersEditPageService를 호출하여 필요한 데이터를 조회합니다.
     *
     * @param httpRequest 사용자 요청 객체 (로그인된 사용자 정보 포함)
     * @param model       뷰로 전달할 데이터 추가
     * @return 회원 정보 수정 페이지 경로
    
     */
    @GetMapping("/members/edit")
    public String modifiMember(
    		@RequestParam("editid") String editId,
    		Model model ) {

        logger.info("MembersController.modifiMmeber()!");
        model.addAttribute("MembersVO", memberService.searchEditMember(editId));
        
        return "members/modify";
    }
}
