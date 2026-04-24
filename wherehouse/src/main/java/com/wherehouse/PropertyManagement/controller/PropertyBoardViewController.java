package com.wherehouse.PropertyManagement.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
 * 인증 상태 전달:
 *   SecurityConfig.propertyViewFilterChain 이 /properties/** 경로에
 *   JwtAuthProcessorFilter 를 적용하여 선택적 인증을 지원한다.
 *   JWT 쿠키가 있으면 SecurityContext 에 userId 가 세팅되고,
 *   없으면 AnonymousAuthenticationFilter 가 "anonymousUser" 를 주입한다.
 *   뷰 컨트롤러는 이를 정규화(anonymousUser → null)하여 Model 에 주입하고,
 *   JSP 에서 JavaScript 변수로 출력하여 버튼 노출 제어에 사용한다.
 */
@Slf4j
@Controller
@RequestMapping("/properties")
public class PropertyBoardViewController {

    /**
     * 매물 게시판 메인 페이지 (F004 매물 전체 목록 페이지)
     *
     * @param userId Model 에 currentUserId 로 주입. 비인증 시 null.
     * @return property/property_board 뷰 이름
     */
    @GetMapping("/board")
    public String showPropertyBoard(
            @AuthenticationPrincipal String userId,
            Model model) {

        String currentUserId = normalizeUserId(userId);
        model.addAttribute("currentUserId", currentUserId);

        log.info("매물 게시판 메인 페이지 요청: currentUserId={}", currentUserId);

        return "property/property_board";
    }

    /**
     * 매물 등록 폼 페이지 (F001 매물 등록 화면)
     *
     * @param userId 인증된 사용자만 등록 가능하므로 Model 에 주입하여 UI 안내에 활용.
     * @return property/property_register 뷰 이름
     */
    @GetMapping("/register")
    public String showRegisterPage(
            @AuthenticationPrincipal String userId,
            Model model) {

        String currentUserId = normalizeUserId(userId);
        model.addAttribute("currentUserId", currentUserId);

        log.info("매물 등록 폼 페이지 요청: currentUserId={}", currentUserId);

        return "property/property_register";
    }

    /**
     * 매물 수정 폼 페이지 (F002)
     *
     * 수정 기능은 property_board 내 모달로 처리되므로
     * 이 경로 접근 시 게시판 페이지로 리다이렉트한다.
     *
     * @param propertyId 수정 대상 매물 식별자
     * @return 게시판 페이지로 리다이렉트
     */
    @GetMapping("/edit/{propertyId}")
    public String showEditPage(@PathVariable String propertyId) {

        log.info("매물 수정 폼 페이지 요청 → 게시판으로 리다이렉트: propertyId={}", propertyId);

        return "redirect:/properties/board";
    }

    /**
     * AnonymousAuthenticationFilter 가 주입하는 "anonymousUser" 를 null 로 정규화.
     * F005 CharterRecommendationService 와 동일 패턴.
     */
    private String normalizeUserId(String userId) {
        if (userId == null || "anonymousUser".equals(userId)) {
            return null;
        }
        return userId;
    }
}
