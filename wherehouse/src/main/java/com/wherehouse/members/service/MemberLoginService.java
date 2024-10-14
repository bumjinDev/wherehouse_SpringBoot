package com.wherehouse.members.service;


import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.wherehouse.members.dao.IMembersRepository;
import com.wherehouse.members.model.MembersVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/* 올바른 로그인 요청인지 판단하는 서비스 빈즈 */
@Service
public class MemberLoginService implements IMemberLoginService{

	@Autowired
	IMembersRepository membersRepository;
	
	@Override
	public String ValidLoginCheck(String id, String pw, HttpServletRequest httpRequest) {
		
		Map<String, Object> resObj = membersRepository.checkMember(id, pw);
		MembersVO membersVO = (MembersVO) resObj.get("membersVO");
		
		if(resObj.get("resInt").equals("1")){
			
			HttpSession httpSession = httpRequest.getSession();
			httpSession.setAttribute("id", membersVO.getId());
			httpSession.setAttribute("nickname", membersVO.getNickName());
			httpSession.setAttribute("validMem", "yes");
			
			return "1";
		} else if(resObj.get("resInt").equals("-1"))			// 아이디로 조회 시 사용자 확인되지 않음.
			return "-1";
		else													// 아이디는 일치하는 사용자가 비밀번호 틀림. 
			return "0";
	}
}