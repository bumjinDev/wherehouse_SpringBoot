package com.wherehouse.PropertyManagement.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 매물 게시판 JSP 뷰 컨트롤러
 *
 * 설계 명세서 참조: 섹션 5.2, 11.4
 *   - 매물 등록·수정·목록 JSP 뷰 렌더링 담당
 *   - API 호출은 PropertyWriteController / PropertyQueryController 가 처리
 *
 * 접근 경로 (컨텍스트 경로 /wherehouse 포함 기준):
 *   /wherehouse/properties/board                 → 전체 목록 페이지 (F004)
 *   /wherehouse/properties/register              → 매물 등록 페이지 (F001)
 *   /wherehouse/properties/edit/{propertyId}     → 매물 수정 페이지 (F002)
 *
 * 비고: 기존 ReviewBoardViewController 와 동일한 구조·네이밍 관례를 따름.
 */
@Slf4j
@Controller
@RequestMapping("/properties")
public class PropertyBoardViewController {

    /**
     * 매물 게시판 메인 페이지 (F004 매물 전체 목록 페이지)
     *
     * 기획서 "UI/UX 연동 방식 — 매물 전체 목록 페이지 (신규)" 구현 지점.
     * 기존 리뷰 게시판과 동일한 UI 패턴(페이지네이션 + 검색)을 따름.
     *
     * @return property/property_board 뷰 이름
     */
    @GetMapping("/board")
    public String showPropertyBoard() {

        log.info("매물 게시판 메인 페이지 요청");

        return "property/property_board";
    }

    /**
     * 매물 등록 폼 페이지 (F001 매물 등록 화면)
     *
     * 기획서 "매물 등록 화면 — 입력 폼 영역" 구현 지점.
     * 필수 입력: 임대 유형, 아파트명, 주소, 층수, 전용면적, 가격 정보.
     *
     * @return property/property_register 뷰 이름
     */
    @GetMapping("/register")
    public String showRegisterPage() {

        log.info("매물 등록 폼 페이지 요청");

        return "property/property_register";
    }

    /**
     * 매물 수정 폼 페이지 (F002)
     *
     * 주의: 본 뷰 진입 자체에는 권한 검증이 없음.
     *       등록자 본인 검증은 실제 수정 API 호출 시점에 서비스 계층이 수행(섹션 9.2.3).
     *       타인 매물의 편집 페이지에 접근은 가능하나 저장 시 E4003 반환.
     *
     * @param propertyId 수정 대상 매물 식별자
     * @return property/property_edit 뷰 이름
     */
    @GetMapping("/edit/{propertyId}")
    public String showEditPage(@PathVariable String propertyId) {

        log.info("매물 수정 폼 페이지 요청: propertyId={}", propertyId);

        return "property/property_edit";
    }
}
