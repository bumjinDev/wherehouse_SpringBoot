package com.wherehouse.members.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;

import com.wherehouse.members.service.MembersEditPageService;
import com.wherehouse.members.service.MembersEditService;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class MembersEditController {

	@Autowired
	MembersEditPageService membersEditPageService;
	@Autowired
	MembersEditService membersEditService;
	
	/* 현재 로그인한 회원에 대한 회원 정보 수정 페이지 요청.(loginSuccess.jsp에서의 요청 내용) */
	@PostMapping("/membermodifypage")
	public String modifiMember(HttpServletRequest httpRequest, Model model) {
		
		System.out.println("modifipag 메소드 실행.");
		model.addAttribute("MembersVO", membersEditPageService.searchEditMember(httpRequest));
		return "members/modify";
	}
	
	/* modify.jsp 페이지의 회원 정보 수정 및 결과를 사용자 브라우저로 알리는 "modifyok.jsp" 호출. */
	@PostMapping("/membermodifyok")
	public String editMember(HttpServletRequest httpRequest, Model model) {
		
		System.out.println("editMember 메소드 실행.");		
		model.addAttribute("ri", membersEditService.editMember(httpRequest));
		
		return "members/modifyOk";
	}
	
	
}