package com.wherehouse.members.service;

import jakarta.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletRequest;


public interface IMemberJoinService {
	
	public int ValidJoin(HttpServletRequest httpRequest); 		// MembersRepository.checkMember() 의 결과로써 숫자 값을 그대로 반환.
}