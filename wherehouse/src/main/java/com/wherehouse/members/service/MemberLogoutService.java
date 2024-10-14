package com.wherehouse.members.service;


import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Service
public class MemberLogoutService implements IMemberLogoutService{

	@Override
	public void executeLogout(HttpServletRequest httpRequest) {
		HttpSession session = httpRequest.getSession() ;
		session.invalidate();
	}
}