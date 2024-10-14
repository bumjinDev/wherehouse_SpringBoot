package com.wherehouse.members.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.wherehouse.members.dao.MembersRepository;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class MembersEditService implements IMembersEditService{

	@Autowired
	MembersRepository membersRepository;
	
	@Override
	public int editMember(HttpServletRequest httpRequest) {
		
		Object [] editParameters = new Object[5];
		
		editParameters[0] = httpRequest.getParameter("pw");
		editParameters[1] = httpRequest.getParameter("nickname");
		editParameters[2] = httpRequest.getParameter("tel");
		editParameters[3] = httpRequest.getParameter("email");
		editParameters[4] = httpRequest.getParameter("id");
		
		int result = (Integer) membersRepository.editMember(editParameters);

		return result; 
	}

}
