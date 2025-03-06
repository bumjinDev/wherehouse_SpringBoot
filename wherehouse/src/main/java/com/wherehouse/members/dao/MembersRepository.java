package com.wherehouse.members.dao;

import java.util.NoSuchElementException;

import org.springframework.stereotype.Repository;

import com.wherehouse.JWT.DTO.AuthenticationEntity;
import com.wherehouse.JWT.Repository.UserEntityRepository;
import com.wherehouse.members.model.MembersEntity;

/**
 * MembersRepository:
 * 회원 관리 테이블과 인증 정보를 처리하는 데이터 접근 계층.
 * 회원 정보의 추가, 조회, 수정과 관련된 로직을 처리하며,
 * 회원 관리 테이블과 인증 테이블을 모두 연동합니다.
 */
@Repository
public class MembersRepository implements IMembersRepository {

    MemberEntityRepository memberEntityRepository; // 회원 관리 테이블의 데이터 접근 객체

    UserEntityRepository userEntityRepository; // JWT 인증을 위해 관리하는 회원 인증 테이블의 데이터 접근 객체

    public final int SUCCESS = 0;
    public final int EDIT_SUCESS = 1;
    public final int NICKNAME_DUPLICATE = 2;
    
    public MembersRepository(
    		MemberEntityRepository memberEntityRepository,
    		UserEntityRepository userEntityRepository
    		
    		) {
    	this.memberEntityRepository = memberEntityRepository;
    	this.userEntityRepository = userEntityRepository;
    }
    
    /**
     * 회원 추가:
     * 회원 관리 테이블(membersVO)과 인증 테이블(userEntity)에 데이터를 추가합니다.
     *
     * @param membersVO 회원 관리 테이블에 저장될 객체
     * @param userEntity 인증 정보를 관리하기 위한 객체
     * @return 정상적으로 회원 가입된 경우 0을 반환
     */
    @Override
    public int addMember(MembersEntity membersEntity, AuthenticationEntity authenticationEntity) {
    	
        memberEntityRepository.save(membersEntity); // 회원 관리 테이블에 데이터 저장
        userEntityRepository.save(authenticationEntity); // 인증 정보 테이블에 데이터 저장

        return SUCCESS; // 정상적으로 처리되었음을 알리는 값 반환
    }

    /**
     * 회원 정보 조회:
     * 회원 ID를 사용하여 회원 정보를 검색합니다.
     *
     * @param userId 회원 ID
     * @return 조회된 회원 정보 객체 (MembersVO)
     */
    @Override
    public MembersEntity getMember(String userId) {

        return memberEntityRepository.findById(userId)
        								.orElseThrow(() -> new NoSuchElementException("해당 게시글을 찾을 수 없습니다. ID: " + userId));
    }

    /**
     * 회원 정보 수정:
     * 회원 관리 테이블과 인증 테이블의 정보를 모두 동시에 수정합니다.
     * 닉네임이 변경되었을 경우 닉네임 중복 여부를 확인합니다.
     *
     * @param memberVO 수정할 회원 관리 테이블 데이터
     * @param userEntity 수정할 인증 테이블 데이터
     * @return 수정 결과에 따른 상태 코드
     *         - 1: 수정 성공
     *         - 2: 닉네임 중복으로 인해 수정 실패
     */
    @Override
    public int editMember(MembersEntity membersEntity, AuthenticationEntity userEntity) {
    		
        // 닉네임 중복 확인: 닉네임이 기존과 다르고 다른 사용자와 중복되지 않을 경우에만 수정 가능
        if (!memberEntityRepository.findByNicknameAndNotIdNative(membersEntity.getNickName(), membersEntity.getId()).isPresent()) {
            
        	memberEntityRepository.save(membersEntity); // 회원 관리 테이블 데이터 수정
            userEntityRepository.save(userEntity); // 인증 정보 테이블 데이터 수정

            return EDIT_SUCESS; // 수정 성공
            
        } else { return NICKNAME_DUPLICATE; }	// 닉네임 중복 검사 따른 수정 과정 실패.
    }
}
