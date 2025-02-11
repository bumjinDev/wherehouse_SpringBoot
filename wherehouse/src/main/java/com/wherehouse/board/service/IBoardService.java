package com.wherehouse.board.service;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;

public interface IBoardService {
	
	public  Map<String, Object> sarchView(int boardNum);						// 게시글 선택 시 해당 게시글 내용 확인.
	public String deleteBoard(String boardId, HttpServletRequest httpRequest);	// 게시글 삭제
	public Map<String, Object> searchBoard(int pnIndex);						// board.list.jsp 랜더링
	public String boardModifyPage(HttpServletRequest httpRequest);				// 게시글 수정 페이지 제공 서비스
	public void modifyBoard(HttpServletRequest httpRequest);					// 게시글 수정 서비스 제공	
	public void writeReply(HttpServletRequest httpRequest);						// 게시글 내 댓글 작성 서비스
	public void boardWrite(HttpServletRequest httpRequest);						// 게시글 작성 서비스
}
