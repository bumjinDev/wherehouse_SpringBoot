package com.wherehouse.board.service;

import java.util.HashMap;
import java.util.Map;


import com.wherehouse.board.model.BoardVO;
import com.wherehouse.board.model.CommandtVO;

public interface IBoardService {
	
	public Map<String, Object> sarchView(int boardNum);						// 게시글 선택 시 해당 게시글 내용 확인.
	public Map<String, String> deleteBoard(int boardId, String jwt);	// 게시글 삭제
	public Map<String, Object> searchBoard(int pnIndex);						// board.list.jsp 랜더링
	public Map<String, String> writePage(String jwtToken);									// 게시글 작성 페이지에 필요한 요청자 Id 를 반환.
	public HashMap<String, Object> boardModifyPage(String jwt, BoardVO boardVO);				// 게시글 수정 페이지 제공 서비스
	public void modifyBoard(BoardVO boardVO);					// 게시글 수정 서비스 제공	
	public void writeReply(String jwt, CommandtVO commandtVO);						// 게시글 내 댓글 작성 서비스
	public void boardWrite(BoardVO boardVO);						// 게시글 작성 서비스
	
}
