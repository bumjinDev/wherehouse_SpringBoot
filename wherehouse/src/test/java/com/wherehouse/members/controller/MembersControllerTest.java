package com.wherehouse.members.controller;

import jakarta.servlet.http.Cookie;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.wherehouse.members.model.MemberDTO;
import com.wherehouse.members.service.IMemberService;

/* 'MembersController' API Test */
//@WebMvcTest(controllers = MembersController.class) // 단순 컨트롤러만 가져오는게 아니라 전체를 가져 와야 됨..
@SpringBootTest
@AutoConfigureMockMvc
class MembersControllerAPITest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IMemberService memberService;

    private static final String mockJwt = "mockJwt";
    private static final String mockUserId = "testUser";
    
    @Value("${TestJWT.user}")
    private static String testUser;
    
    private static final Cookie authCookie = new Cookie("Authorization", testUser);
    
    @BeforeEach
    void setUp() {

    	HashMap<String, String> loginSucessRequest = new HashMap<String, String>();
    	loginSucessRequest.put("userId", "mockUserId");
    	loginSucessRequest.put("userName", "testNick");
    	
        when(memberService.validLoginSuccess(anyString()))
            .thenReturn(loginSucessRequest);
    }

    // 1. 로그인 성공 테스트
    @Test
    void testLoginSuccess() throws Exception {
    	
    	System.out.println("testLoginSuccess()!");
    	System.out.println(authCookie.getValue());

        mockMvc.perform(get("/loginSuccess").cookie(authCookie))
            .andDo(print())  // 요청 및 응답 내용을 콘솔에 출력
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("userId"))
            .andExpect(model().attributeExists("userName"));
    }


    // 2. 로그인 실패 (JWT 쿠키 없음)
    @Test
    void testLoginSuccess_MissingCookie() throws Exception {
        mockMvc.perform(get("/loginSuccess"))
                .andExpect(status().isBadRequest())  // 쿠키 없음 예외 발생
                .andExpect(content().string("필수 쿠키가 존재하지 않습니다: Authorization"));
    }

    // 3. 회원 정보 수정 페이지 요청 테스트
    @Test
    void testModifyMemberPage() throws Exception {
        when(memberService.searchEditMember(mockUserId)).thenReturn(new MemberDTO());

        mockMvc.perform(post("/membermodifypage").param("editid", mockUserId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("MembersVO"))
                .andExpect(view().name("members/modify"));
    }

    // 4. 회원 정보 수정 요청 테스트 (닉네임 중복 없음)
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

    // 5. 회원 정보 수정 실패 (닉네임 중복)
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

    // 6. 회원 가입 요청 테스트
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
