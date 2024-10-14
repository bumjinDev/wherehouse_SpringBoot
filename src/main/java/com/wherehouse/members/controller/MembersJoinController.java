package com.wherehouse.members.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import com.wherehouse.members.service.MemberJoinService;

import jakarta.servlet.http.HttpServletRequest;

/* 회원 가입 요청 받는 컨트롤러 */
@Controller
public class MembersJoinController {
	
	@Autowired
	MemberJoinService memberjoinService;
	
	@PostMapping("/joinOk")
	public String joinRequest(HttpServletRequest httpRequest, Model model) {
		
		System.out.println("joinOk 메소드 실행.");
		String resInt = String.valueOf(memberjoinService.ValidJoin(httpRequest));
		model.addAttribute("resInt", resInt);			/* 회원 가입 요청에 필요한 데이터를 전달. */
		
		return "members/joinOk";
	}
}