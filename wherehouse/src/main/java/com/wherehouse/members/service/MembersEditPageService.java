package com.wherehouse.members.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.wherehouse.members.dao.MembersRepository;
import com.wherehouse.members.model.MembersVO;
import jakarta.servlet.http.HttpServletRequest;

/* 회원 정보 수정 페이지 제공 서비스 빈즈. */
@Service
public class MembersEditPageService implements IMembersEditPageService {

	@Autowired
	MembersRepository membersRepository;
	
	/* 1. 회원정보 검색하여 MembersVO 객체로 반환
	 * 2. 반환된 객체를 MembersEditController 에게 반환. */
	@Override
	public MembersVO searchEditMember(HttpServletRequest httpRequest) {
		
		return membersRepository.getMember(httpRequest.getParameter("editid"));		
	}
}
