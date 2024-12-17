package com.wherehouse.board.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import com.wherehouse.board.service.IBoardService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * BoardServiceController는 게시판 기능(글 목록 조회, 글 작성, 수정, 삭제 등)을 처리하는 컨트롤러입니다.
 * 각 메소드는 클라이언트의 요청에 따라 필요한 데이터를 처리하거나 서비스를 호출하여 로직을 수행합니다.
 */
@Controller
public class BoardServiceController {
    
    @Autowired
    IBoardService boardService;
    

    // ======== 조회 관련 메소드 ========

    /**
     * 특정 페이지 번호의 게시글 목록을 조회하여 반환합니다.
     *
     * @param pnIndex 요청 페이지 번호
     * @param model   Model 객체에 게시글 목록 정보를 추가
     * @return 게시글 목록 페이지 경로
     */
    @GetMapping("/list/{pnIndex}")
    public String pageListPn(@PathVariable("pnIndex") int pnIndex, Model model) {
        Map<String, Object> listView = boardService.searchBoard(pnIndex);

        model.addAttribute("pnSize", listView.get("pnSize"));
        model.addAttribute("boardList", listView.get("boardList"));
        model.addAttribute("members", listView.get("members"));

        return "board/BoardListPage";
    }

    /**
     * 특정 게시글의 상세 내용을 조회합니다.
     *
     * @param boardNumber 게시글 번호
     * @param model       Model 객체에 게시글 내용과 댓글 정보를 추가
     * @return 게시글 상세 페이지 경로
     */
    @GetMapping("/choiceboard/{boardNumber}")
    public String choiceboard(@PathVariable("boardNumber") int boardNumber, Model model) {
        Map<String, Object> contentView = boardService.sarchView(boardNumber);

        model.addAttribute("content_view", contentView.get("content_view"));
        model.addAttribute("comments", contentView.get("comments"));
        model.addAttribute("AuthorNickname", contentView.get("AuthorNickname"));

        return "board/ContentPage";
    }

    // ======== 작성 관련 메소드 ========

    /**
     * 글 작성 페이지를 반환합니다.
     *
     * @return 글 작성 페이지 경로
     */
    @GetMapping("/writepage")
    public String WritePage() {
        return "board/WritePage";
    }

    /**
     * 글 작성 요청을 처리합니다.
     *
     * @param httpRequest 현재 요청 객체
     * @param model       Model 객체 (에러 처리 시 사용)
     * @return 게시글 목록 페이지로 리다이렉트
     */
    @PostMapping("/boardwrite")
    public String WritePage(HttpServletRequest httpRequest, Model model) {
        try {
        	boardService.boardWrite(httpRequest);
        } catch (Exception e) {
            e.printStackTrace();
            return "error"; // 에러 페이지로 이동
        }

        return "redirect:/list/0";
    }

    // ======== 삭제 관련 메소드 ========

    /**
     * 게시글 삭제 요청을 처리합니다.
     *
     * @param boardId     삭제할 게시글 ID
     * @param httpRequest 현재 요청 객체 (사용자 정보 포함)
     * @param model       Model 객체에 리다이렉트 URL 추가
     * @return 삭제 결과 페이지 경로
     */
    @GetMapping("/delete/{boardId}")
    public String deletePage(@PathVariable("boardId") String boardId, HttpServletRequest httpRequest, Model model) {
        if (boardService.deleteBoard(boardId, httpRequest).equals("/wherehouse/list/0"))
            model.addAttribute("redirectUrl", "/wherehouse/list/0");
        else
            model.addAttribute("redirectUrl", "/wherehouse/choiceboard/" + boardId);

        return "board/DeletePage";
    }

    // ======== 수정 관련 메소드 ========

    /**
     * 게시글 수정 페이지 요청을 처리합니다.
     * 요청자의 ID와 작성자의 ID를 비교하여 수정 페이지를 반환하거나 경고 페이지를 반환합니다.
     *
     * @param httpRequest 현재 요청 객체
     * @param model       Model 객체에 게시글 정보를 추가
     * @return 수정 페이지 경로 또는 경고 페이지 경로
     */
    @PostMapping("/modifypage")
    public String modifiyPageRequest(HttpServletRequest httpRequest, Model model) {
        String returnPage = boardService.boardModifyPage(httpRequest);

        if (returnPage.equals("board/ContentEditPage")) {
            model.addAttribute("boardId", httpRequest.getParameter("boardId"));
            model.addAttribute("title", httpRequest.getParameter("title"));
            model.addAttribute("boardContent", httpRequest.getParameter("boardContent"));
            model.addAttribute("region", httpRequest.getParameter("region"));
            model.addAttribute("boardDate", httpRequest.getParameter("boardDate"));
            model.addAttribute("boardHit", httpRequest.getParameter("boardHit"));
            model.addAttribute("AuthorNickname", httpRequest.getParameter("AuthorNickname"));
            model.addAttribute("writerId", httpRequest.getParameter("writerId"));
        } else {
            model.addAttribute("redirectUrl", "/wherehouse/choiceboard/" + httpRequest.getParameter("boardId"));
        }

        return returnPage;
    }

    /**
     * 게시글 수정 요청을 처리합니다.
     *
     * @param httpRequest 현재 요청 객체
     * @return 게시글 목록 페이지로 리다이렉트
     */
    @PostMapping("/modify")
    public String modifyPage(HttpServletRequest httpRequest) {
    	boardService.modifyBoard(httpRequest);
        return "redirect:/list/0";
    }

    // ======== 기타 작업 ========

    /**
     * 댓글 작성 요청을 처리합니다.
     *
     * @param httpRequest 현재 요청 객체
     * @param model       Model 객체 (리다이렉트 URL 설정)
     * @return 게시글 상세 페이지로 리다이렉트
     */
    @PostMapping("/replyWrite")
    public String replyWrite(HttpServletRequest httpRequest, Model model) {
    	boardService.writeReply(httpRequest);
        return "redirect:/choiceboard/" + httpRequest.getParameter("boardId");
    }
}
