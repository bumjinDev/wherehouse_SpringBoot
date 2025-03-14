package com.wherehouse.members.unit.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wherehouse.JWT.DTO.AuthenticationEntity;
import com.wherehouse.JWT.Repository.UserEntityRepository;
import com.wherehouse.members.dao.MembersRepository;
import com.wherehouse.members.dao.MemberEntityRepository;
import com.wherehouse.members.model.MembersEntity;

@ExtendWith(MockitoExtension.class)
class MembersRepositoryTest {

    @InjectMocks
    private MembersRepository membersRepository; // 테스트할 대상

    @Mock
    private MemberEntityRepository memberEntityRepository; // 회원 관리 테이블(Mock)

    @Mock
    private UserEntityRepository userEntityRepository; // 인증 정보 테이블(Mock)

    private MembersEntity mockMember;
    private AuthenticationEntity mockAuthEntity;

    @BeforeEach
    void setUp() {
        // 테스트에 사용할 Mock 데이터 생성
        mockMember = MembersEntity.builder()
                .id("testUser")
                .pw("password123")
                .nickName("testNick")
                .tel("010-1234-5678")
                .email("abcd@gmail.com")
                .build();

        mockAuthEntity = AuthenticationEntity.builder()
                .userid("testUser")
                .username("testNick")
                .password("password123")
                .roles(List.of("ROLE_USER"))
                .build();
    }

    // 1. 회원 추가 테스트
    @Test
    void testAddMember_Success() {
    	
        when(memberEntityRepository.save(any(MembersEntity.class))).thenReturn(mockMember);
        when(userEntityRepository.save(any(AuthenticationEntity.class))).thenReturn(mockAuthEntity);

        int result = membersRepository.addMember(mockMember, mockAuthEntity);

        assertEquals(membersRepository.SUCCESS, result);
        
        verify(memberEntityRepository, times(1)).save(any(MembersEntity.class));
        verify(userEntityRepository, times(1)).save(any(AuthenticationEntity.class));
    }

    // 2-1. 존재하는 회원 조회 성공
    @Test
    void testGetMember_Success() {
    	
        when(memberEntityRepository.findById(mockMember.getId())).thenReturn(Optional.of(mockMember));

        MembersEntity result = membersRepository.getMember(mockMember.getId());

        assertNotNull(result);
        assertEquals(mockMember.getId(), result.getId());
        assertEquals(mockMember.getNickName(), result.getNickName());
    }

    // 2-2. 존재하지 않는 회원 조회 시 예외 발생
    @Test
    void testGetMember_UserNotFound() {
        when(memberEntityRepository.findById(mockMember.getId())).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> {
            membersRepository.getMember(mockMember.getId());
        });
    }

    // 3-1. 회원 정보 수정 성공 (닉네임이 중복되지 않은 경우)
    @Test
    void testEditMember_Success() {
        when(memberEntityRepository.findByNicknameAndNotIdNative(mockMember.getNickName(), mockMember.getId()))
                .thenReturn(Optional.empty()); // 닉네임 중복 없음

        when(memberEntityRepository.save(any(MembersEntity.class))).thenReturn(mockMember);
        when(userEntityRepository.save(any(AuthenticationEntity.class))).thenReturn(mockAuthEntity);

        int result = membersRepository.editMember(mockMember, mockAuthEntity);

        assertEquals(membersRepository.EDIT_SUCESS, result);
        
        verify(memberEntityRepository, times(1)).save(any(MembersEntity.class));
        verify(userEntityRepository, times(1)).save(any(AuthenticationEntity.class));
    }

    // 3-2. 회원 정보 수정 실패 (닉네임 중복)
    @Test
    void testEditMember_NicknameDuplicate() {
        when(memberEntityRepository.findByNicknameAndNotIdNative(mockMember.getNickName(), mockMember.getId()))
                .thenReturn(Optional.of(mockMember)); // 닉네임이 이미 존재함

        int result = membersRepository.editMember(mockMember, mockAuthEntity);

        assertEquals(membersRepository.NICKNAME_DUPLICATE, result);
        
        verify(memberEntityRepository, never()).save(any(MembersEntity.class));
        verify(userEntityRepository, never()).save(any(AuthenticationEntity.class));
    }
}