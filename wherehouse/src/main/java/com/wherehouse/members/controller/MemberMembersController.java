package com.wherehouse.members.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import com.wherehouse.members.service.IMemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * MemberMembersController는 회원 관련 요청을 처리하는 컨트롤러입니다.
 * - 로그인 성공, 회원 정보 수정, 회원 가입 등 다양한 회원 관련 작업을 처리합니다.
 * - 각 요청은 적절한 서비스 레이어로 위임되어 로직을 수행합니다.
 */
@Controller
public class MemberMembersController {


    @Autowired
    IMemberService memberService;
    
    /**
     * 로그인 성공 후 사용자 정보를 화면에 전달합니다.
     * 
     * - 사용자의 ID와 닉네임을 모델에 추가하여 뷰에 전달합니다.
     * - 이전 요청에서 HttpServletRequest를 통해 설정된 사용자 정보를 사용합니다.
     *
     * @param httpRequest 사용자 요청 객체 (사용자 ID와 닉네임 포함)
     * @param model       뷰로 전달할 데이터 추가
     * @return 로그인 성공 페이지 경로
     */
    @GetMapping("/loginSuccess")
    public String redirectLoginSueccess(HttpServletRequest httpRequest, HttpServletResponse httpResponse, Model model) {

        System.out.println("/LoginSuccess 메소드 실행.");

        /* 쿠키 내 JWT 추출하여 userId 와 userName 가져와서 loginSuccess.jsp 랜더링. */
        Map<String, String> sessionInfo = memberService.validLogin(httpRequest);
        
        model.addAttribute("userId", sessionInfo.get("userId"));
        model.addAttribute("userName", sessionInfo.get("userName"));
        
        return "members/loginSuccess";
    }
    
    @PostMapping("/loginException")
    public String handleAuthenticationError(HttpServletRequest request, Model model) {
        
    	System.out.println("handleAuthenticationError()!");
    	
        model.addAttribute("exceptionType", (String) request.getAttribute("exceptionType"));
        
        return "members/authenticationError"; // JSP 또는 Thymeleaf 뷰 반환
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
    public String modifiMember(HttpServletRequest httpRequest, Model model) {

        System.out.println("modifyPage 메소드 실행.");
        model.addAttribute("MembersVO", memberService.searchEditMember(httpRequest));
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
    public String editMember(HttpServletRequest httpRequest, HttpServletResponse httpResponse,Model model) {

        System.out.println("editMember 메소드 실행.");
        model.addAttribute("ri", memberService.editMember(httpRequest, httpResponse));

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
     */
    @PostMapping("/joinOk")
    public String joinRequest(HttpServletRequest httpRequest, Model model) {

        System.out.println("joinOk 메소드 실행.");
        String resInt = String.valueOf(memberService.validJoin(httpRequest));
      
        model.addAttribute("resInt", resInt); // 회원 가입 요청 결과 전달

        return "members/joinOk";
    }
}
