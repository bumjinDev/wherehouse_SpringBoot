package com.wherehouse.members.unit;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import com.wherehouse.members.service.MemberService;


import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import com.wherehouse.JWT.DTO.AuthenticationEntity;
import com.wherehouse.JWT.DTO.AuthenticationEntityConverter;
import com.wherehouse.JWT.Filter.Util.JWTUtil;
import com.wherehouse.JWT.Repository.UserEntityRepository;
import com.wherehouse.members.dao.IMembersRepository;
import com.wherehouse.members.dao.MemberEntityRepository;
import com.wherehouse.members.model.MemberConverter;
import com.wherehouse.members.model.MemberDTO;
import com.wherehouse.members.model.MembersEntity;
import com.wherehouse.redis.handler.RedisHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.Date;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ExtendWith(MockitoExtension.class) // ✅ Mockito 테스트 환경 설정
class MemberServiceTest {

    @InjectMocks
    private MemberService memberService;
	    
    @Mock
    private IMembersRepository membersRepository;

    @Mock
    private MemberEntityRepository memberEntityRepository;

    @Mock
    private UserEntityRepository userEntityRepository;

    @Mock
    private RedisHandler redisHandler;

    @Mock
    private ValueOperations<String, Object> mockValueOperations;
    
    @Mock
    private JWTUtil jwtUtil;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private MemberConverter memberConverter;
    
    @Mock
    AuthenticationEntityConverter authenticationEntityConverter;
    
    private MemberDTO mockMemberDTO;
    private MembersEntity mockMembersEntity;
    private AuthenticationEntity authenticationEntity;

    @BeforeEach
    void setUp() {
    	
        mockMemberDTO =  MemberDTO.builder()
        					.id("testUser")
        					.pw("password123")
        					.nickName("testNick")
        					.tel("010-1234-5678")
        					.email("abcd@gmail.com")
        					.joinDate(Date.valueOf("2024-03-10"))
//        					.joinDate(new Date(System.currentTimeMillis()))
        					.build();
        
       mockMembersEntity = MembersEntity.builder()
					        .id("testUser")
					        .pw("password123")
					        .nickName("testNick")
					        .tel("010-1234-5678")
					        .email("abcd@gmail.com")
		                    .joinDate(Date.valueOf("2024-03-10")) // 특정 날짜 지정 가능
//					        .joinDate(new Date(System.currentTimeMillis())) // 현재 날짜로 설정
					        .build();
       
       authenticationEntity = AuthenticationEntity.builder()
				.userid(mockMemberDTO.getId())
				.username(mockMemberDTO.getNickName())
				.password(mockMemberDTO.getPw())
				.roles(List.of("ROLE_USER"))
				.build();

    }

    // 로그인 성공 요청 검증
    @Test
    void testValidLoginSuccess() {
    	
        String jwtToken = "mockToken";
        
        when(jwtUtil.extractUserId(anyString(), any())).thenReturn("testUser");
        when(jwtUtil.extractUsername(anyString(), any())).thenReturn("testNick");

        Map<String, String> result = memberService.validLoginSuccess(jwtToken);

        assertEquals("testUser", result.get("userId"));
        assertEquals("testNick", result.get("userName"));
    }

    // 회원가입 시도 및 검증 - 중복되지 않은 상황.
    @Test
    void testValidJoin_Success() {
    	
    	/* MembemrService.validJoin() 실제 호출 때 실행되는 Repository 빈을 Mock 객체로써 기능함으로써 실제 DBMS 와 독립된 실행 결과를 확인. */
        when(memberEntityRepository.findById(anyString())).thenReturn(Optional.empty());
        when(memberEntityRepository.findByNickName(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPasswordValue");
        when(authenticationEntityConverter.toEntity(any(), any())).thenReturn(authenticationEntity);
        when(membersRepository.addMember(
        		any(),
        		any())).thenReturn(1);

        int result = memberService.validJoin(mockMemberDTO);

        assertEquals(1, result); // 회원가입 성공
        verify(membersRepository, times(1)).addMember(any(), any());
    }

    // 회원가입 검증 - 아이디 중복 되지 않음.
    @Test
    void testValidJoin_UserIdDuplicate() {
    	
        when(memberEntityRepository.findById(anyString())).thenReturn(Optional.of(mockMembersEntity));

        int result = memberService.validJoin(mockMemberDTO);	// MemberService.USER_ID_DUPLICATE(1) 반환.
        assertEquals(memberService.USER_ID_DUPLICATE, result);
    }

    // 회원가입 검증 - 닉네임 중복 : Id 조회는 중복이 아니나 닉네임 조회 시 객체를 반환하도록 함으로써 닉네임 중복 설정.
    @Test
    void testValidJoin_NicknameDuplicate() {
    	
        when(memberEntityRepository.findById(anyString())).thenReturn(Optional.empty());
        when(memberEntityRepository.findByNickName(anyString())).thenReturn(Optional.of(mockMembersEntity));

        int result = memberService.validJoin(mockMemberDTO);

        assertEquals(memberService.NICKNAME_DUPLICATE, result);
    }

    // 회원 조회 검증
    @Test
    void testSearchEditMember() {
    	
        when(membersRepository.getMember(anyString())).thenReturn(mockMembersEntity);

        MembersEntity result = memberService.searchEditMember("testUser");

        assertNotNull(result);
        assertEquals("testUser", result.getId());
    }

    // 회원 정보 수정 - 성공
    @Test
    void testEditMember_Success() {

        String currentToken = "mockCurrnetToken";
        String newToken = "newMockToken";

        // Spring Security 테이블 내 조회 시 "authenticationEntity" 반환.
        when(userEntityRepository.findById(anyString())).thenReturn(Optional.of(authenticationEntity));
        
        // DTO 상관 없이 지정 문자열 반환.
        when(passwordEncoder.encode(anyString())).thenReturn("encodePasswordValue");
        
        /* 회원 수정 시도 시 반드시 성공 반환. */
        when(membersRepository.editMember(any(), any())).thenReturn(1);
        
        /* "MemberService.editToken()" 내  토큰 수정 작업 시 설정된 토큰 반환. */
        when(jwtUtil.modifyClaim(anyString(), any(), eq("username"), anyString())).thenReturn(newToken);
        
        /* "MemberService.updateJwtToken()" 내 redisHandler 작업 설정  */
        when(redisHandler.getValueOperations()).thenReturn(mockValueOperations);
        when(redisHandler.getValueOperations()).thenReturn(mockValueOperations);

        doNothing().when(mockValueOperations).set(anyString(), anyString(), any());

        
        String resultToken = memberService.editMember(currentToken, mockMemberDTO);

        assertEquals(newToken, resultToken);
    }

    // 회원 정보 수정 실패 (닉네임 중복)
    @Test
    void testEditMember_NicknameDuplicate() {
        String currentToken = "mockToken";

        //when(userEntityRepository.findById(anyString())).thenReturn(Optional.of(mockMembersEntity));
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(membersRepository.editMember(any(), any())).thenReturn(2); // 닉네임 중복

        String resultToken = memberService.editMember(currentToken, mockMemberDTO);

        assertEquals(String.valueOf(memberService.NICKNAME_DUPLICATE), resultToken);
    }
}
