package com.wherehouse.members.unit.controller;

import jakarta.servlet.http.Cookie;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.wherehouse.members.controller.MembersController;
import com.wherehouse.members.model.MemberDTO;
import com.wherehouse.members.service.IMemberService;

@WebMvcTest(MembersController.class) // ✅ Spring MVC 테스트 (JSP 뷰 포함)
@AutoConfigureMockMvc
class MembersControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IMemberService memberService; // ✅ 서비스 계층을 MockBean으로 등록

    private static final String mockJwt = "mockJwt";
    private static final String mockUserId = "testUser";
    private static final Cookie authCookie = new Cookie("Authorization", mockJwt);

    @BeforeEach
    void setUp() {
        when(memberService.validLoginSuccess(anyString()))
            .thenReturn(Map.of("userId", mockUserId, "userName", "testNick"));
    }

    // ✅ 1. 로그인 성공 테스트
    @Test
    void testLoginSuccess() throws Exception {
        mockMvc.perform(get("/loginSuccess").cookie(authCookie))
            .andExpect(status().isOk())  
            .andExpect(model().attributeExists("userId"))
            .andExpect(model().attributeExists("userName"))
            .andExpect(view().name("members/loginSuccess"));
    }

    // ✅ 2. 로그인 실패 (JWT 쿠키 없음)
    @Test
    void testLoginSuccess_MissingCookie() throws Exception {
        mockMvc.perform(get("/loginSuccess"))
                .andExpect(status().isBadRequest())  // 쿠키 없음 예외 발생
                .andExpect(content().string("필수 쿠키가 존재하지 않습니다: Authorization"));
    }

    // ✅ 3. 회원 정보 수정 페이지 요청 테스트
    @Test
    void testModifyMemberPage() throws Exception {
        when(memberService.searchEditMember(mockUserId)).thenReturn(new MemberDTO());

        mockMvc.perform(post("/membermodifypage").param("editid", mockUserId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("MembersVO"))
                .andExpect(view().name("members/modify"));
    }

    // ✅ 4. 회원 정보 수정 요청 테스트 (닉네임 중복 없음)
    @Test
    void testEditMember_Success() throws Exception {
        when(memberService.editMember(eq(mockJwt), any(MemberDTO.class))).thenReturn("newMockJwt");

        mockMvc.perform(post("/membermodifyok").cookie(authCookie)
                .param("id", mockUserId)
                .param("nickName", "newNick")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("Authorization"))
                .andExpect(model().attribute("ri", 1))
                .andExpect(view().name("members/modifyOk"));
    }

    // ✅ 5. 회원 정보 수정 실패 (닉네임 중복)
    @Test
    void testEditMember_NicknameDuplicate() throws Exception {
        when(memberService.editMember(eq(mockJwt), any(MemberDTO.class))).thenReturn("2");

        mockMvc.perform(post("/membermodifyok").cookie(authCookie)
                .param("id", mockUserId)
                .param("nickName", "existingNick")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(model().attribute("ri", 2))
                .andExpect(view().name("members/modifyOk"));
    }

    // ✅ 6. 회원 가입 요청 테스트
    @Test
    void testJoinRequest_Success() throws Exception {
        when(memberService.validJoin(any(MemberDTO.class))).thenReturn(0);

        mockMvc.perform(post("/joinOk")
                .param("id", mockUserId)
                .param("pw", "password123")
                .param("nickName", "testNick")
                .param("email", "test@gmail.com")
                .param("tel", "010-1234-5678")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(model().attribute("resInt", "0"))
                .andExpect(view().name("members/joinOk"));
    }
}
