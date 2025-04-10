package com.wherehouse.board.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;
import com.wherehouse.board.service.IBoardService;

/**
 * 게시판 페이지 라우팅(View 반환용) 컨트롤러
 * 
 * <p>요청된 URI에 따라 JSP 기반 View를 반환하며, 게시글 목록, 상세, 작성/수정 페이지 렌더링을 담당한다.</p>
 * 
 * <p>모든 요청은 URI prefix "/boards" 하위에서 동작한다.</p>
 * 
 * @author -
 */
@Controller
@RequestMapping("/boards")
public class BoardPageController {

    private final IBoardService boardService;

    private static final String BOARD_LIST_PAGE = "board/BoardListPage";
    private static final String CONTENT_PAGE = "board/ContentPage";
    private static final String WRITE_PAGE = "board/WritePage";
    private static final String CONTENT_EDIT_PAGE = "board/ContentEditPage";

    public BoardPageController(IBoardService boardService) {
        this.boardService = boardService;
    }

    /**
     * 게시글 목록 페이지 요청 (페이지네이션 포함)
     *
     * @param pnIndex 페이지 번호 (0부터 시작)
     * @param model   JSP View에 전달할 데이터 모델
     * @return 게시글 목록 View 이름 ("board/BoardListPage")
     */
    @GetMapping("/page/{pnIndex}")
    public String getBoardPage(@PathVariable("pnIndex") int pnIndex, Model model) {
        model.addAllAttributes(boardService.listBoards(pnIndex));
        return BOARD_LIST_PAGE;
    }

    /**
     * 특정 게시글 상세 페이지 요청
     *
     * @param boardNumber 게시글 번호
     * @param model       View에 전달할 게시글 상세 정보
     * @return 게시글 상세 View 이름 ("board/ContentPage")
     */
    @GetMapping("/{boardId}")
    public String getBoardDetail(@PathVariable("boardId") int boardId, Model model) {
        model.addAllAttributes(boardService.getBoardDetail(boardId));
        return CONTENT_PAGE;
    }

    /**
     * 게시글 작성 페이지 요청
     *
     * @param token JWT 인증 토큰 (Authorization 쿠키)
     * @param model 작성 페이지에 필요한 사용자 정보 포함
     * @return 게시글 작성 View 이름 ("board/WritePage")
     */
    @GetMapping("/new")
    public String getBoardWritePage(@CookieValue(value = "Authorization", required = false) String token, Model model) {
        model.addAllAttributes(boardService.getBoardCreationInfo(token));
        return WRITE_PAGE;
    }

    /**
     * 게시글 수정 페이지 요청
     *
     * <p>해당 게시글에 대한 작성자 여부를 인증 토큰 기반으로 검증한 뒤 수정 화면을 렌더링한다.</p>
     *
     * @param jwt     JWT 인증 토큰 (Authorization 쿠키)
     * @param boardId 수정 대상 게시글 ID
     * @param model   수정 대상 게시글 정보 포함
     * @return 게시글 수정 View 이름 ("board/ContentEditPage")
     */
    @GetMapping("/edit")
    public String getBoardEditPage(@CookieValue(value = "Authorization", required = false) String jwt,
                                   @RequestParam("boardId") int boardId, Model model) {
        model.addAllAttributes(boardService.getBoardForUpdate(jwt, boardId));
        return CONTENT_EDIT_PAGE;
    }

    /**
     * 예외 상황 발생 시 예외 View 반환
     *
     * @param stateCode 예외 상태 코드 ("403", "404" 등)
     * @param model     상태 코드를 기반으로 메시지 렌더링
     * @return 예외 View ("board/exception/exceptionPage")
     */
    @GetMapping("/exception")
    public String getErrorPage(@RequestParam("stateCode") String stateCode, Model model) {
        model.addAttribute("stateCode", stateCode);
        return "board/exception/exceptionPage";
    }
}