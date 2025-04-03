package com.wherehouse.members.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.wherehouse.members.model.MemberDTO;
import com.wherehouse.members.service.IMemberService;

import java.util.Map;

/**
 * MembersAPIController
 * - 회원 가입 및 회원 정보 수정 API 처리
 * - 입력값 유효성 검증 수행 (Bean Validation)
 * - JWT 재발급 및 HttpOnly 쿠키 재설정 포함
 */
/* Exception Handler 'MemberAPIControllerExceptionHandler' 으로 위침 */
@RestController
public class MembersAPIController {

    private static final Logger logger = LoggerFactory.getLogger(MembersAPIController.class);

    private final IMemberService memberService;

    public MembersAPIController(IMemberService memberService) {
        this.memberService = memberService;
    }

    /**
     * 회원 가입 요청 처리
     *
     * - @Valid를 통해 MemberDTO에 선언된 유효성 검증 애너테이션 기반 검사 수행
     * - 실패 시 MethodArgumentNotValidException 발생 → 예외 처리기로 이동
     */
    @PostMapping("/members/join")
    public ResponseEntity<Map<String, Object>> joinRequest(
            @Valid @RequestBody MemberDTO memberEditRequestDTO,
            Model model) {

        logger.info("MemberController.joinRequest() 호출");

        memberService.validJoin(memberEditRequestDTO);
        return ResponseEntity.ok(Map.of("message", "회원가입이 정상적으로 완료되었습니다."));
    }

    /**
     * 회원 정보 수정 요청 처리
     *
     * - JWT 쿠키로 인증 식별
     * - @Valid를 통해 입력값 형식 검사 수행
     * - 서비스 레벨에서는 닉네임 중복, 사용자 존재 여부 등의 도메인 검증 수행
     */
    @PostMapping("/members/edit")
    public ResponseEntity<Map<String, Object>> editMember(
            @CookieValue(name = "Authorization", required = true) String jwt,
            @Valid @RequestBody MemberDTO editRequestDTO,
            HttpServletResponse httpResponse,
            Model model) {

        logger.info("MembersController.editMember() 호출 - 사용자 ID: {}", editRequestDTO.getId());

        String editToken = memberService.editMember(jwt, editRequestDTO);

        // JWT 쿠키 갱신 (보안 속성 적용 필요 시 수정)
        Cookie cookie = new Cookie("Authorization", editToken);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setSecure(true);  // 운영 환경에서는 true 적용
        httpResponse.addCookie(cookie);

        return ResponseEntity.ok(Map.of("message", "회원 수정이 정상적으로 완료되었습니다."));
    }
}
