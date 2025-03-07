package com.wherehouse.board.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wherehouse.board.model.BoardDTO;
import com.wherehouse.board.model.CommandtVO;
import com.wherehouse.board.service.IBoardService;

/**
 * BoardServiceController는 게시판 기능(글 목록 조회, 글 작성, 수정, 삭제 등)을 처리하는 컨트롤러입니다.
 * 각 메소드는 클라이언트의 요청에 따라 필요한 데이터를 처리하거나 서비스를 호출하여 로직을 수행합니다.
 */
@Controller
public class BoardServiceController {

    private final IBoardService boardService;

    // JSP 경로 상수.
    private static final String BOARD_LIST_PAGE = "board/BoardListPage";
    private static final String CONTENT_PAGE = "board/ContentPage";
    private static final String WRITE_PAGE = "board/WritePage";
    private static final String CONTENT_EDIT_PAGE = "board/ContentEditPage";

    public BoardServiceController(IBoardService boardService) {
        this.boardService = boardService;
    }

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

        /*  model.addAllAttributes : pnSize / boardList / members  */
        model.addAllAttributes(listView);
        
        return BOARD_LIST_PAGE;
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
    	
        /*  model.addAllAttributes : content_view / comments / AuthorNickname  */
        model.addAllAttributes(contentView);
        
        return CONTENT_PAGE;
    }

    // ======== 작성 관련 메소드 ========

    /**
     * 글 작성 페이지를 반환합니다.
     *
     * @return 글 작성 페이지 경로
     */
    @GetMapping("/writepage")
    public String WritePage(
    				@CookieValue(value = "Authorization", required = false) String token,
    				Model model) {
    	
    	Map<String, String> writePageData = boardService.writePage(token);
    	
    	/*  model.addAllAttributes : userId / userName */
    	model.addAllAttributes(writePageData);
    	
        return WRITE_PAGE;
    }

    /**
     * 글 작성 요청을 처리합니다.
     *
     * @param httpRequest 현재 요청 객체
     * @param model       Model 객체 (에러 처리 시 사용)
     * @return 게시글 목록 페이지로 리다이렉트
     */
    @PostMapping("/boardwrite")
    public String WritePage(@ModelAttribute BoardDTO boardVO) {
        	
        boardService.boardWrite(boardVO); // 게시글 작성 직후 전체 게시글 목록으로 리 다이렉트!
        
        return "redirect:/list/0";
    }

    // ======== 삭제 관련 메소드 ========

    /**
     * 게시글 삭제 요청을 처리 한다. 현재 게시글 내 작성자 ID 와 현재 요청한 ID 가 같으면 삭제하고 아니면 삭제하지 않는다.
     *
     * @param boardId     삭제할 게시글 ID
     * @param httpRequest 현재 요청 객체 (사용자 정보 포함)
     * @param model       Model 객체에 리다이렉트 URL 추가
     * @return 삭제 결과 페이지 경로
     */
    @DeleteMapping("/delete/{boardId}")
    public ResponseEntity<Void>  deletePage(
    			@CookieValue(value = "Authorization", required = false) String jwt,
    			@PathVariable("boardId") int boardId,
    			Model model) {
    	
    	boardService.deleteBoard(boardId, jwt); // 삭제 수행 (예외 발생 가능)
    	return ResponseEntity.noContent().build(); // 204 No Content 반환
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
    public String modifiyPageRequest(
            @CookieValue(value = "Authorization", required = false) String jwt,
            @ModelAttribute BoardDTO boardVO,
            RedirectAttributes redirectAttributes, // Flash Attribute 추가
            Model model) {

    	HashMap<String, Object> returnData = boardService.boardModifyPage(jwt, boardVO);

        if (returnData.get("canModify").equals(true)) {
        	
            model.addAllAttributes(returnData);
            return CONTENT_EDIT_PAGE;
            
        } else {
            // FlashAttribute를 사용하여 alert 메시지 추가
            redirectAttributes.addFlashAttribute("alertMessage", "게시글 작성자가 아니므로 수정할 수 없습니다.");
            return "redirect:/wherehouse/choiceboard/" + boardVO.getBoardId();
        }
    }

    /**
     * 게시글 수정 요청을 처리합니다.
     *
     * @param httpRequest 현재 요청 객체
     * @return 게시글 목록 페이지로 리다이렉트
     */
    @PostMapping("/modify")
    public String modifyPage(@ModelAttribute BoardDTO boardVO) {
    	
    	boardService.modifyBoard(boardVO);
    	
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
    
    public String replyWrite(
    		@CookieValue(value = "Authorization", required = false) String jwt,
    		@ModelAttribute CommandtVO commandtVO) {
    	
    	boardService.writeReply(jwt, commandtVO);
    	
        return "redirect:/choiceboard/" + commandtVO.getBoardId();
    }
}
