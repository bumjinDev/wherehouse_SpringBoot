package com.wherehouse.board.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.wherehouse.board.dao.BoardRepository;

import jakarta.servlet.http.HttpServletRequest;

@Repository
public class BoardWriteService implements IBoardWriteService{
	
	@Autowired
	BoardRepository boardRepository;

	@Override
	public void boardWrite(HttpServletRequest httpRequest) {
		
		Object [] parameters = new Object[4];
		
		/* 게시글 작성 내용을 Repository로 전달하여 작업 수행. */
		parameters[0] = httpRequest.getParameter("id");
		parameters[1] = httpRequest.getParameter("title");
		parameters[2] = httpRequest.getParameter("bcontent");
		parameters[3] = httpRequest.getParameter("regions");
		
		boardRepository.boardWrite(parameters);
	}
}
