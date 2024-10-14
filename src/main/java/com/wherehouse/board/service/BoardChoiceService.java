package com.wherehouse.board.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wherehouse.board.dao.BoardRepository;
import com.wherehouse.board.model.BoardVO;


@Service
public class BoardChoiceService implements IBoardChoiceService{

	@Autowired
	BoardRepository boardRepository;
	
	@Override
	public Map<String, Object> sarchView(int contentnum) {
		
		HashMap<String, Object> resultMap = new HashMap<String, Object>();
		
		/* 선택한 게시글 조회수 1 증가 */
		boardRepository.upHit(contentnum);
		
		/* 게시글 내용 찾기. */
		BoardVO boardVO = boardRepository.boardView(contentnum);
		resultMap.put("content_view", boardVO);
		
		/* 게시글 내 댓글 찾기 */
		resultMap.put("comments", boardRepository.commentSearch(contentnum));
		
		/* 게시글 작성자 닉네임, 게시글 내 작성자 id로 검색.(ArrayList 객체로 조회). */
		ArrayList<String> userId  = new ArrayList<String>();
		userId.add(String.valueOf(boardVO.getuserid()));
		resultMap.put("nickname", boardRepository.getMembers(userId)) ;
		
		return resultMap;
	}
}