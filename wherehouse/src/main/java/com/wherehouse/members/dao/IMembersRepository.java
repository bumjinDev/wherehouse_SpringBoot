package com.wherehouse.members.dao;

import com.wherehouse.JWT.UserDTO.UserEntity;
import com.wherehouse.members.model.MembersEntity;


public interface IMembersRepository {
	
	//public Map<String, Object> checkMember(String id, String pw);		// 로그인 시의 회원 여부를 반환.(아이디 틀리면 -1, 패스워드 틀리면 0, 일치하는 사용자 있을 시 1)
	//public int confimMember(String id);								// 회원 가입 시의 이미 존재하는 회원인지 ID 확인. (joinOk 요청, 이미 존재하는 회원이라면 1 반환, 없는 회원이라는 0 반환 후 다음 비즈니스 로직 실행).
	public int addMember(MembersEntity membersVO, UserEntity userEntity);			// confimMember() 실행 시 이상 없으면 회원 가입 진행.
	public MembersEntity getMember(String userId);						// modify.jsp 페이지 제공 위한 MembersVO
	public int editMember(MembersEntity memberVO, UserEntity userEntity);					// 회원정보 수정.
}
