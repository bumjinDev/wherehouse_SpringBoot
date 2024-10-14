package com.wherehouse.members.service;

import java.util.HashMap;
import java.util.Map;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wherehouse.members.dao.MembersRepository;

import jakarta.servlet.http.HttpServletRequest;

/* 회원 가입 요청 따른 정보를 받아서 비지니스 로직 실행, 회원 가입에 필요한 데이터는 종류가 많으므로 Map 컬렉션으로 받음,
 * 	동작으로는 회원 가입 요청 데이터를 레포지토리 빈즈에 전달, 이후 레포지토리에서는 MembersVo 객체로써 생성 후에 id 값을 테이블에 조회하여 이미 존재하는 회원인지 확인 후에 회원 가입 진행.  */
@Service
public class MemberJoinService implements IMemberJoinService {
	
	@Autowired
	MembersRepository membersRepository;
	
	@Override
	public int ValidJoin(HttpServletRequest httpRequest) {
	
		Map <String, String> requestData = new HashMap<String, String>();
		
		requestData.put("id", httpRequest.getParameter("id"));
		requestData.put("pw", httpRequest.getParameter("pw"));
		requestData.put("nickName", httpRequest.getParameter("nickName"));
		requestData.put("tel", httpRequest.getParameter("tel"));
		requestData.put("email", httpRequest.getParameter("email"));
		
		/* 현재 회원 가입 요청 id 정보로 이미 존재하는 회원인지 여부 확인. */
		if(membersRepository.confimMember(httpRequest.getParameter("id")) >= 1) {				// 이미 존재하는 회원이니 회원 가입 하지 않는다.
			
			return 1;
		} else {		// 0 반환 시 새로운 회원 가입 요청이므로 회원 가입 비즈니스 로직 실행.
			return membersRepository.addMember(requestData);
		}
	}
}