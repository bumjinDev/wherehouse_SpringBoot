package com.wherehouse.members.service;


import com.wherehouse.members.model.MembersVO;

import jakarta.servlet.http.HttpServletRequest;

public interface IMembersEditPageService {
	
	public MembersVO searchEditMember(HttpServletRequest httpRequest);
}