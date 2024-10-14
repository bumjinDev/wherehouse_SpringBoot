package com.wherehouse.board.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wherehouse.board.dao.BoardRepository;

/* PageResponseController 메소드 "pageList" 실행 시 게시글 목록 구현 @Service 빈 클래스  */

@Service
public class BoardListService implements IBoardListService{

	@Autowired
	BoardRepository boardRepository;
	
	@Override
	public Map<String, Object> searchBoard(int pnIndex) {
		
		/* pnIndex 따라 실제 게시글 목록 조회. */
		return boardRepository.SearchBoardList(pnIndex);
	}
}