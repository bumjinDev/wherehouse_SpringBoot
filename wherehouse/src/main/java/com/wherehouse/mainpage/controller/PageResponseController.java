package com.wherehouse.mainpage.controller;

import java.util.Map;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.wherehouse.board.service.IBoardListService;

import jakarta.annotation.PostConstruct;

/* main.jsp 및 회원가입과 로그인 페이지, 로그 아웃 요청 그리고 분석 상세 내용을 처리하는 컨트롤러. */

@Controller
@RequestMapping(value="/page")
public class PageResponseController  {
	
	@Autowired
	IBoardListService boardListService;
	
	@PostConstruct
	public void init() {
		
		System.out.println("컨트롤러 pageResponse 실행!!");
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
	
	/* information.jsp : 별도의 컨트롤러로 분기.  */
	
	/* 초기 list.jsp 페이지 요청 처리.(pnIndex 없음). */
	@GetMapping("/list")
	public String pageList(Model model) {
		
		System.out.println("pageList() 실행!");
		
		Map<String, Object> listView = boardListService.searchBoard(0);
		
		model.addAttribute("pnSize", listView.get("pnSize"));				// 페이지 선택이 아닌 단순 첫번째 페이지 요청.
		model.addAttribute("boardList", listView.get("boardList"));
		model.addAttribute("members", listView.get("members"));

		return "board/list";
	}
	
	/* login.jsp : 로그인 요청 페이지 */
	@GetMapping("/login")
	public String pageLogin() {
		
		System.out.println("pageLogin 메소드 실행!");
		return "members/login";
	}
	
	/* join.jsp : 회원가입 요청 페이지 */
	@GetMapping("/join")
	public String pageJoin() {
		
		System.out.println("pageJoin 메소드 실행!");
		return "members/join";
	}
	
	/* informationPage.jsp : 분석 상세 내용 확인 페이지 */
	@GetMapping("/reinfo")
	public String pagereinfo() {
		
		System.out.println("컨트롤러 /page의 reinfo 메소드 실행!");
		return "recommand/description";
	}
}