package com.wherehouse.members.unit.service;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
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
import com.wherehouse.members.service.MemberService;
import com.wherehouse.redis.handler.RedisHandler;

import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.sql.Date;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import java.security.Key;
//import javax.crypto.spec.SecretKeySpec;
//import java.util.Base64;

@ExtendWith(MockitoExtension.class)
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
    private ValueOperations<String, Object> mockValueOperations;

    @Mock
    private RedisHandler redisHandler;

    @Mock
    private JWTUtil jwtUtil;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Spy
    private MemberConverter memberConverter;

    @Spy
    private AuthenticationEntityConverter authenticationEntityConverter;

    private MemberDTO mockMemberDTO;
    private MembersEntity mockMembersEntity;
    private AuthenticationEntity authenticationEntity;

    @BeforeEach
    void setUp() {
    	
        mockMemberDTO = MemberDTO.builder()
                .id("testUser")
                .pw("password123")
                .nickName("testNick")
                .tel("010-1234-5678")
                .email("abcd@gmail.com")
                .joinDate(Date.valueOf("2024-03-10"))
                .build();

        mockMembersEntity = MembersEntity.builder()
                .id("testUser")
                .pw("password123")
                .nickName("testNick")
                .tel("010-1234-5678")
                .email("abcd@gmail.com")
                .joinDate(Date.valueOf("2024-03-10"))
                .build();

        authenticationEntity = AuthenticationEntity.builder()
                .userid(mockMemberDTO.getId())
                .username(mockMemberDTO.getNickName())
                .password(mockMemberDTO.getPw())
                .roles(List.of("ROLE_USER"))
                .build();
    }

    /** 로그인 성공 페이지 반환 - 성공 : Spring Security Filter Chain 내 "/login" 이후의 과정  */
    @Test
    void testValidLoginSuccess() {
        
    	String currentToken = "mockToken";
        String encodedKey = "mockBase64EncodedKey";
    	
        when(redisHandler.getValueOperations()).thenReturn(mockValueOperations);
        when(mockValueOperations.get(anyString())).thenReturn(encodedKey);
        
        when(jwtUtil.extractUserId(anyString(), any(Key.class))).thenReturn("testUser");
        when(jwtUtil.extractUsername(anyString(), any(Key.class))).thenReturn("testNick");

        var result = memberService.validLoginSuccess(currentToken);

        assertEquals("testUser", result.get("userId"));
        assertEquals("testNick", result.get("userName"));
    }

    /** 로그인 로직 검증 - 실패 케이스 작성 목록 : 
     * 1. 로그인 시도 시 JWT TTL 만료로 인한 실패에 대한 예외 반환 : 추가적인 서비스 빈 로직 작성 이후 재 작성 예정 */
    
    /** 회원가입 검증 (성공) */
    @Test
    void testValidJoin_Success() {
        
    	when(memberEntityRepository.findById(anyString())).thenReturn(Optional.empty());		// 아이디 중복되지 않음 확인
        when(memberEntityRepository.findByNickName(anyString())).thenReturn(Optional.empty());	// 닉네임 중복되지 않음 확인
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");				// "Pure Unit Testing" 개념에 따라 외부 라이브러리 의존성 배제 
        when(authenticationEntityConverter.toEntity(any(MemberDTO.class), any(List.class))).thenReturn(authenticationEntity);
        when(membersRepository.addMember(any(MembersEntity.class), any(AuthenticationEntity.class))).thenReturn(1);

        int result = memberService.validJoin(mockMemberDTO);

        assertEquals(1, result);
        verify(membersRepository, times(1)).addMember(any(), any());
    }

    /** 회원가입 검증 - 실패 케이스 : 아이디 중복 */
    @Test
    void testValidJoin_UserIdDuplicate() {
    	
        when(memberEntityRepository.findById(anyString())).thenReturn(Optional.of(mockMembersEntity));
        
        int result = memberService.validJoin(mockMemberDTO);
        assertEquals(memberService.USER_ID_DUPLICATE, result);
    }

    /** 회원가입 검증 - 실패 케이스 : 닉네임 중복 */
    @Test
    void testValidJoin_NicknameDuplicate() {
        when(memberEntityRepository.findById(anyString())).thenReturn(Optional.empty());
        when(memberEntityRepository.findByNickName(anyString())).thenReturn(Optional.of(mockMembersEntity));

        int result = memberService.validJoin(mockMemberDTO);
        assertEquals(memberService.NICKNAME_DUPLICATE, result);
    }

    /** 회원 정보 조회 로직 검증 :
     * 	1. MemberService.searchEditMember() 호출
     *  2. MembersRepository.getMember() 내 수정 요청자 이름으로 검색 수행, 테스트 코드이므로 'mockMembersEntity' 반환.
     *  3. 검사.  */
    @Test
    void testSearchEditMember() {
    	
        when(membersRepository.getMember(anyString())).thenReturn(mockMembersEntity);	// 

        MemberDTO memberDTO = memberService.searchEditMember("testUser");
        assertNotNull(memberDTO);
        assertEquals("testUser", memberDTO.getId());
    }

    /** 회원 정보 수정 검증 - 성공 */
    @Test
    void testEditMember_Success() {
    	
        String currentToken = "mockToken";
        String newToken = "mockNewToken";
        String encodedKey = "mockBase64EncodedKey";
        
        when(userEntityRepository.findById(mockMemberDTO.getId())).thenReturn(Optional.of(authenticationEntity));
        when(passwordEncoder.encode(mockMemberDTO.getPw())).thenReturn("mockEncodedPassword");
        when(membersRepository.editMember(any(MembersEntity.class), any(AuthenticationEntity.class))).thenReturn(1);
        when(redisHandler.getValueOperations()).thenReturn(mockValueOperations);
        when(mockValueOperations.get(currentToken)).thenReturn(encodedKey);
        when(jwtUtil.modifyClaim(any(String.class), any(Key.class), any(String.class), any(String.class)))
        .thenReturn(newToken);

        doNothing().when(mockValueOperations).set(anyString(), any(), any(Duration.class));

        String resultToken = memberService.editMember(currentToken, mockMemberDTO);

        assertEquals(newToken, resultToken);
        verify(membersRepository, times(1)).editMember(any(MembersEntity.class), any(AuthenticationEntity.class));
        verify(mockValueOperations, times(1)).set(anyString(), any(), any(Duration.class));
    }
    
    /* 회원 정보 수정 검증 실패 케이스 작성 예정 :
     * 1. 없는 사용자의 Id 반환. */
}
