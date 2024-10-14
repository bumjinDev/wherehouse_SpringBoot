package com.wherehouse.members.service;

import jakarta.servlet.http.HttpServletRequest;

public interface IMemberLogoutService {
	
	public void executeLogout(HttpServletRequest httpRequest);
}
