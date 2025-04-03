package com.wherehouse.board.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.ui.Model;

import com.wherehouse.board.model.BoardDTO;
import com.wherehouse.board.model.CommandtVO;
import com.wherehouse.board.service.IBoardService;



@Controller
public class BoardViewController {

    private final IBoardService boardService;
    private static final String BOARD_LIST_PAGE = "board/BoardListPage";
    private static final String CONTENT_PAGE = "board/ContentPage";
    private static final String WRITE_PAGE = "board/WritePage";
    private static final String CONTENT_EDIT_PAGE = "board/ContentEditPage";

    public BoardViewController(IBoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping("/list/{pnIndex}")
    public String pageListPn(@PathVariable("pnIndex") int pnIndex, Model model) {
        model.addAllAttributes(boardService.searchBoard(pnIndex));
        return BOARD_LIST_PAGE;
    }

    @GetMapping("/choiceboard/{boardNumber}")
    public String choiceboard(@PathVariable("boardNumber") int boardNumber, Model model) {
        model.addAllAttributes(boardService.sarchView(boardNumber));
        return CONTENT_PAGE;
    }

    @GetMapping("/writepage")
    public String writePage(@CookieValue(value = "Authorization", required = false) String token, Model model) {
        model.addAllAttributes(boardService.writePage(token));
        return WRITE_PAGE;
    }

    @PostMapping("/modifypage")
    public String modifiyPageRequest(@CookieValue(value = "Authorization", required = false) String jwt,
                                     @ModelAttribute BoardDTO boardVO, Model model) {
        model.addAllAttributes(boardService.boardModifyPage(jwt, boardVO));
        return CONTENT_EDIT_PAGE;
    }
    
    @PostMapping("/boardwrite")
    public String /* ResponseEntity<Void> */ writeBoard(@ModelAttribute BoardDTO boardVO) {
        boardService.boardWrite(boardVO);
        //return ResponseEntity.status(201).build(); // Created
        return "redirect:/list/0";
    }

    @PostMapping("/modify")
    public String /* ResponseEntity<Void> */ modifyBoard(
    		@CookieValue(value = "Authorization", required = false) String token,
    		@ModelAttribute BoardDTO boardVO) {
        boardService.modifyBoard(boardVO, token);
        return "redirect:/list/0"; // return ResponseEntity.noContent().build(); // 204 No Content
    }
    
    @PostMapping("/replyWrite")
    public String /* ResponseEntity<Void> */ writeReply(@CookieValue(value = "Authorization", required = false) String jwt,
                                           @ModelAttribute CommandtVO commandtVO) {
        boardService.writeReply(jwt, commandtVO);
        // return ResponseEntity.status(201).build();
        return "redirect:/choiceboard/" + commandtVO.getBoardId();
    }
}
