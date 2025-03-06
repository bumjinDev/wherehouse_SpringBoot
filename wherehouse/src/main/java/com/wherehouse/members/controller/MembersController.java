package com.wherehouse.members.controller;

import java.util.Map;

import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.wherehouse.members.model.MemberDTO;
import com.wherehouse.members.service.IMemberService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;

/**
 * MemberMembersController는 회원 관련 요청을 처리하는 컨트롤러입니다.
 * - 로그인 성공, 회원 정보 수정, 회원 가입 등 다양한 회원 관련 작업을 처리합니다.
 * - 각 요청은 적절한 서비스 레이어로 위임되어 로직을 수행합니다.
 */

//@ControllerAdvice
@Controller
public class MembersController {

	private static final Logger logger = LoggerFactory.getLogger(MembersController.class);
	
	IMemberService memberService;
	
	public MembersController(IMemberService memberService) {
		this.memberService = memberService;
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
    @GetMapping("/loginSuccess")
    public String loginSueccess(
    		
    		@CookieValue(name = "Authorization", required = true) String jwt,
    		Model model) {

    	logger.info("로그인 성공 요청 처리");

        /* 쿠키 내 JWT 추출하여 userId 와 userName 가져와서 loginSuccess.jsp 랜더링. */
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
    
    @PostMapping("/membermodifypage")
    public String modifiMember(
    		@RequestParam("editid") String editId,
    		Model model ) {

        logger.info("MembersController.modifiMmeber()!");
        
        model.addAttribute("MembersVO", memberService.searchEditMember(editId));
        return "members/modify";
    }

    /**
     * 회원 정보 수정 요청 처리.
     * 
     * - 사용자가 입력한 수정된 회원 정보를 처리하여 업데이트합니다.
     * - MembersEditService를 호출하여 수정 작업을 수행합니다.
     *
     * @param httpRequest 사용자 요청 객체 (수정된 회원 정보 포함)
     * @param model       뷰로 전달할 결과 데이터 추가
     * @return 회원 정보 수정 결과 페이지 경로
     */
    @PostMapping("/membermodifyok")
    public String editMember(
    		@CookieValue(name = "Authorization", required = true) String jwt,
    		@ModelAttribute MemberDTO editRequestDTO,
    		HttpServletResponse httpResponse,
    		Model model) {

        logger.info("MembersController.editMember()!");
        logger.info("editMember 요청: 사용자 ID = {}", editRequestDTO.getId());
        
        // 서비스에서 새 JWT를 반환하고, 응답 처리는 컨트롤러에서 수행
        String editToken = memberService.editMember(jwt, editRequestDTO);
        
        // 정상적으로 새로운 JWT 를 반환 시 이를 새로운 HostOnly Cookie 갱신. 
        if(!editToken.equals("2")) {
        	
        	Cookie cookie = new Cookie("Authorization", editToken);
        	cookie.setHttpOnly(true);
        	cookie.setPath("/");
        	cookie.setSecure(false);
        	httpResponse.addCookie(cookie);
        	
        	model.addAttribute("ri", 1); // 정상 처리 결과를 사용자에게 알림
        	
        } else if(editToken.equals("2")) {	// 닉네임 중복으로 인한 회원 가입 실패 결과를 사용자에게 알림.
        	
        	model.addAttribute("ri", 2);
        } else { model.addAttribute("ri", 3); }

        return "members/modifyOk";
    }

    /**
     * 회원 가입 요청 처리.
     * 
     * - 사용자가 입력한 회원 가입 정보를 처리하여 가입 여부를 결정합니다.
     * - MemberJoinService를 호출하여 유효성을 검증하고 결과를 반환합니다.
     *
     * @param httpRequest 사용자 요청 객체 (회원 가입 정보 포함)
     * @param model       뷰로 전달할 결과 데이터 추가
     * @return 회원 가입 결과 페이지 경로
     * 
     */
    
    @PostMapping("/joinOk")
    public String joinRequest(
    		
    		@ModelAttribute MemberDTO memberEditRequestDTO,
    		Model model) {

        logger.info("MemberController.joinRequest()!");

        model.addAttribute("resInt", String.valueOf(memberService.validJoin(memberEditRequestDTO))); // 회원 가입 요청 결과 전달

        return "members/joinOk";
    }
    
    
    /* == 모든 예외는 컨트롤러에서 담당해서 예외 처리 까지 응답 범위로써 포함하도록 작성! == */
    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<String> handleMissingCookieException(MissingRequestCookieException ex) {
    	
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("필수 쿠키가 존재하지 않습니다: " + ex.getCookieName());
    }
}
