package com.wherehouse.board.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.wherehouse.board.dao.BoardRepository;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class BoardModifyService implements IBoardModifyService {

	@Autowired
	BoardRepository boardRepository;
	
	@Override
	public void modifyBoard(HttpServletRequest httpRequest) {
		
		Object [] boardEdit = new Object[4];
		
		boardEdit[0] = httpRequest.getParameter("title");
		boardEdit[1] = httpRequest.getParameter("bcontent");
		boardEdit[2] = httpRequest.getParameter("regions");
		boardEdit[3] = httpRequest.getParameter("bId");		
		
		boardRepository.boardModify(boardEdit);
	}
}