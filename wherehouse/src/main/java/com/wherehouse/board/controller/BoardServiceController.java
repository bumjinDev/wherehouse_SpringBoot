package com.wherehouse.board.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import com.wherehouse.board.service.BoardChoiceService;
import com.wherehouse.board.service.BoardDeleteService;
import com.wherehouse.board.service.BoardListService;
import com.wherehouse.board.service.BoardModifyService;
import com.wherehouse.board.service.BoardWriteCommand;
import com.wherehouse.board.service.BoardWriteService;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class BoardServiceController {
	
	@Autowired
	BoardListService boardListService;
	@Autowired
	BoardWriteService boardWriteService;
	@Autowired
	BoardChoiceService boardChoiceService;
	@Autowired
	BoardDeleteService boardDeleteService;
	@Autowired
	BoardModifyService boardModifyService;
	@Autowired
	BoardWriteCommand boardWriteCommand;
	
	/* 사용자가 게시글 요청 시 번호 버튼 클릭하여 요청 시 응답. */
	@GetMapping("/list/{pnIndex}")
	public String pageListPn(@PathVariable int pnIndex, Model model) {
		System.out.println("pageListPn 메소드 실행!");
		
		Map<String, Object> listView = boardListService.searchBoard(pnIndex);
		
		model.addAttribute("pnSize", listView.get("pnSize"));
		model.addAttribute("boardList", listView.get("boardList"));
		model.addAttribute("members", listView.get("members"));
		
		return "board/list";
	}
	
	/* write.jsp 요청 시 해당 페이지 응답. */
	@GetMapping("/writepage")
	public String WritePage() {
		System.out.println("Writepage() 메소드 실행");
		
		return "board/writepage";		// 글 작성 후 게시글 목록 확인.
	}
	
	/* 글 작성 요청, write.jsp 의 게시글 작성 요청 처리. */
	@PostMapping("/writeboard")
	public String WritePage(HttpServletRequest httpRequest, Model model) {
		System.out.println("Writepage() 메소드 실행");
		
		boardWriteService.boardWrite(httpRequest);
		
		return "redirect:/page/list";		// 글 작성 후 게시글 목록 확인.
	}
	
	/* 게시글 목록(list.jsp)에서 특정 게시글을 선택. */
	@GetMapping("/writeboard/{contentnum}")
	public String writePage(@PathVariable int contentnum, Model model) {
		System.out.println("writeboard() 메소드 실행");
		
		Map<String, Object> contentView = boardChoiceService.sarchView(contentnum);
		
		model.addAttribute("content_view", contentView.get("content_view"));
		model.addAttribute("comments", contentView.get("comments"));
		model.addAttribute("nickname", contentView.get("nickname"));
		
		return "board/contentview";
	}
	
	@GetMapping("/delete/{contentnum}")
	public String deletePage(@PathVariable String contentnum) {
		
		boardDeleteService.deleteBoard(contentnum);
		
		return "redirect:/page/list";		// 글 작성 후 게시글 목록 확인.
	}
	
	/* contentview.jsp 에서 수정 페이지 요청 처리. */
	@PostMapping("/modifypage")
	public String modifiyPageRequest(HttpServletRequest httpRequest, Model model) {
		
		model.addAttribute("bId", httpRequest.getParameter("bId"));
		model.addAttribute("title", httpRequest.getParameter("title"));
		model.addAttribute("boardDate", httpRequest.getParameter("boardDate"));
		model.addAttribute("boardHit", httpRequest.getParameter("boardHit"));
		model.addAttribute("boardContent", httpRequest.getParameter("boardContent"));
		
		return "board/contentedit";		// 글 작성 후 게시글 목록 확인.
	}
	
	/* contentedit.jsp 페이지에서의 게시글 수정 요청 처리. */
	@PostMapping("/modify")
	public String modifyPage(HttpServletRequest httpRequest) {
		System.out.println("modifiy() 메소드 실행!");
		
		boardModifyService.modifyBoard(httpRequest);
		
		return "redirect:/page/list";
	}
	
	/* contentedit.jsp 페이지에서의 댓글 작성 요청. */
	@PostMapping("/replyWrite")
	public String replyWrite(HttpServletRequest httpRequest) {
		System.out.println("replyWrite() 메소드 실행!");
		
		boardWriteCommand.writeReply(httpRequest);
		
		String redir = "redirect:/writeboard/" + httpRequest.getParameter("bId");
		
		return redir;
	}
}
