package com.wherehouse.members.service;

import java.util.Map;
import com.wherehouse.members.model.MembersEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface IMemberService {

	public Map<String, String> validLogin(HttpServletRequest httpRequest);
	public int validJoin(HttpServletRequest httpRequest); 		// MembersRepository.checkMember() 의 결과로써 숫자 값을 그대로 반환.
	public MembersEntity searchEditMember(HttpServletRequest httpRequest);	// 회원 정보 조회 : 회원이 회원 정보 수정 요청 시 이를 위한 회원 검색
	public int editMember(HttpServletRequest httpRequest, HttpServletResponse httpResponse);		// 실제 회원 수정 요청
}
