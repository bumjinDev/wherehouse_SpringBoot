package com.wherehouse.review.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 리뷰 게시판 뷰 컨트롤러
 *
 * JSP 페이지를 반환하는 컨트롤러
 * API 호출은 ReviewQueryController, ReviewWriteController에서 처리
 *
 * 설계 명세서: 사용자 경험 반영 - 리뷰 게시판 UI
 *
 * 접근 경로: /wherehouse/reviews/board
 */
@Slf4j
@Controller
@RequestMapping("/reviews")
public class ReviewBoardViewController {

    /**
     * 리뷰 게시판 메인 페이지
     *
     * 접근 URL: /wherehouse/reviews/board
     *
     * main.js 또는 다른 메뉴에서 호출:
     * - iframeSection.src = "/wherehouse/reviews/board"
     *
     * @return review_board.jsp 뷰 이름
     */
    @GetMapping("/board")
    public String showReviewBoard() {

        log.info("리뷰 게시판 메인 페이지 요청");

        // ViewResolver가 /WEB-INF/views/review_board.jsp를 찾음
        // 또는 프로젝트 설정에 따라 /webapp/review_board.jsp
        return "review/review_board";
    }
}