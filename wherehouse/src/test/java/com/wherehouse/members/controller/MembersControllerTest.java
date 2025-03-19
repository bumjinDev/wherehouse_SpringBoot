package com.wherehouse.members.controller;

import jakarta.servlet.http.Cookie;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import com.wherehouse.members.model.MemberDTO;
import com.wherehouse.members.service.IMemberService;

/**
 * MembersController 단위 테스트 (뷰 리졸버, 서비스, 레포지토리 로드하지 않음)
 */
@WebMvcTest(controllers = MembersController.class)
@ContextConfiguration(classes = {MembersController.class}) // 강제로 특정 빈만 로드
@AutoConfigureMockMvc(addFilters = false)  // Security 필터 비활성화// 실제 HTTP 요청 테스트(tetRestTemplate 의 실제 HTTPServletRequest 요청과 달리 "MockHttpServletRequest"요청이 디스패처 서블릿만 호출)
class MembersControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IMemberService memberService;

    private static final String MOCK_JWT = "mock-jwt-token";
    private static final String MOCK_USER_ID = "testUser";
    private static final String MOCK_USER_NAME = "Test User";

    private Cookie authCookie;

    //private static final Logger logger = LoggerFactory.getLogger(MembersControllerUnitTest.class);

    @BeforeEach
    void setUp() {
        authCookie = new Cookie("Authorization", MOCK_JWT);
    }

    // 1. 로그인 성공 테스트
    @Test
    void testLoginSuccess() throws Exception {

        // Mocking
        when(memberService.validLoginSuccess(MOCK_JWT))
            .thenReturn(
            		Map.of("userId", MOCK_USER_ID, "userName", MOCK_USER_NAME));

        mockMvc.perform(get("/loginSuccess").cookie(authCookie))
            .andExpect(status().isOk())
            .andExpect(model().attribute("userId", MOCK_USER_ID))
            .andExpect(model().attribute("userName", MOCK_USER_NAME))
            .andDo(print());
    }

    // 2. 로그인 실패 (JWT 쿠키 없음)
    @Test
    void testLoginSuccess_MissingCookie() throws Exception {
        mockMvc.perform(get("/loginSuccess"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("필수 쿠키가 존재하지 않습니다: Authorization"))
            .andDo(print());
    }

    // 3. 회원 정보 수정 페이지 요청 테스트
    @Test
    void testModifyMemberPage() throws Exception {
        
        MemberDTO mockMember = new MemberDTO();
        mockMember.setId(MOCK_USER_ID);

        // Mocking
        when(memberService.searchEditMember(MOCK_USER_ID)).thenReturn(mockMember);

        mockMvc.perform(post("/membermodifypage")
                .param("editid", MOCK_USER_ID))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("MembersVO"))
                .andExpect(view().name("members/modify"))
                .andDo(print());
    }

    // 4. 회원 정보 수정 요청 테스트 (닉네임 중복 없음)
    @Test
    void testEditMember_Success() throws Exception {
        
        MemberDTO modifiedMember = new MemberDTO();
        modifiedMember.setId(MOCK_USER_ID);
        modifiedMember.setNickName("newNick");

        // Mocking
        when(memberService.editMember(eq(MOCK_JWT), any(MemberDTO.class)))
            .thenReturn("newMockJwtToken");

        mockMvc.perform(post("/membermodifyok").cookie(authCookie)
                .param("id", MOCK_USER_ID)
                .param("nickName", "newNick")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("Authorization"))
                .andExpect(model().attribute("ri", 1))
                .andExpect(view().name("members/modifyOk"))
                .andDo(print());
    }

    // 5. 회원 정보 수정 실패 (닉네임 중복)
    @Test
    void testEditMember_NicknameDuplicate() throws Exception {

        // Mocking (중복 발생 시 "2" 반환)
        when(memberService.editMember(eq(MOCK_JWT), any(MemberDTO.class)))
            .thenReturn("2");

        mockMvc.perform(post("/membermodifyok").cookie(authCookie)
                .param("id", MOCK_USER_ID)
                .param("nickName", "existingNick")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(model().attribute("ri", 2))
                .andExpect(view().name("members/modifyOk"))
                .andDo(print());
    }

    // 6. 회원 가입 요청 테스트
    @Test
    void testJoinRequest_Success() throws Exception {

        // Mocking (정상 가입이면 0 반환)
        when(memberService.validJoin(any(MemberDTO.class)))
            .thenReturn(0);

        mockMvc.perform(post("/joinOk")
                .param("id", MOCK_USER_ID)
                .param("pw", "password123")
                .param("nickName", "testNick")
                .param("email", "test@gmail.com")
                .param("tel", "010-1234-5678")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(model().attribute("resInt", "0"))
                .andExpect(view().name("members/joinOk"))
                .andDo(print());
    }
}
