package com.wherehouse.members.controller;



import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.wherehouse.members.service.MemberLoginService;
import com.wherehouse.members.service.MemberLogoutService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/* 로그인 요청 페이지 "login.jsp"에서 로그인 요청을 Service 빈즈로 전달하는 컨트롤러. */
@Controller
public class MembersLoginController {

	@Autowired
	MemberLoginService memberLoginService;
	@Autowired
	MemberLogoutService memberLogoutService;
	
	/* MemberLoginService로 분기하여 실제 사용자인지에 대한 조회 결과 데이터를 loginOk.jsp로 포함하여 전달,
	 *	이후 loginOk.jsp 에서 결과에 따라 사용자에게 alert 또는 페이지 분기. */
	@GetMapping("/loginOk")
	//public String checkLogin(@RequestParam String id, @RequestParam String pw, Model model, HttpServletRequest httpRequest) {				// @RequestPram : GET 방식의 요청에 대해서 HTTP 메시지 헤더 부분 내 파라메터를 파싱하여 매개변수와 동일한 이름의 파라메터라면 자동으로 데이터 입력.
	public String checkLogin(@RequestParam("id") String id, @RequestParam("pw") String pw, Model model, HttpServletRequest httpRequest) {				// @RequestPram : GET 방식의 요청에 대해서 HTTP 메시지 헤더 부분 내 파라메터를 파싱하여 매개변수와 동일한 이름의 파라메터라면 자동으로 데이터 입력.	
		
		System.out.println("checkLogin 메소드 실행.");
		
		model.addAttribute("loginRes", memberLoginService.ValidLoginCheck(id, pw, httpRequest));
		
		System.out.println("loginRes : " + model.getAttribute("loginRes"));
		
		return "members/loginOk";
	}
	
	/* 로그인 요청(login.jsp) 정상으로 확인 시 페이지 변경. */
	@GetMapping("/loginSuccess")
	public String redirectLoginSueccess() {
		
		System.out.println("redirectLoginSueccess 메소드 실행.");
		return "members/loginSuccess"; 
	}
	
	/* 로그 아웃 요청 */
	@PostMapping("/logout")
	public String pageLogout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		
		System.out.println("pageLogout 메소드 실행!");
		memberLogoutService.executeLogout(httpRequest);
		
		return "members/logout";
	}
}