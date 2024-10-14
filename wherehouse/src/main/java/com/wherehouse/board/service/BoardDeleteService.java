package com.wherehouse.board.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wherehouse.board.dao.BoardRepository;

@Service
public class BoardDeleteService implements IBoardDeleteService{
	
	@Autowired
	BoardRepository boardRepository;

	@Override
	public void deleteBoard(String bId) {
		
		boardRepository.boardDelete(bId);
	}
}
