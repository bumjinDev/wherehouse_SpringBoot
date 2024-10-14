package com.wherehouse.board.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.wherehouse.board.model.BoardVO;
import com.wherehouse.board.model.CommentVO;

public interface IBoardRepository {
	
	public HashMap<String, Object> SearchBoardList(int pnIndex);		// 페이지 네이션 요청에 맞게 게시판 목록 제공.
	public void boardWrite(Object [] parameter);							// 게시판 글 작성.
	public BoardVO boardView(int boardId);								// 선택한 게시판의 내용 제공.(게시판 Id로 선택)
	public ArrayList<String> getMembers(ArrayList<String> userId);	// 각 게시글 별 사용자 닉네임.
	public void upHit(int boardId);											// 선택한 게시글에 대한 조회수 증가.
	public void boardDelete(String boardId);								// 선택한 게시글 삭제.
	public void boardModify(Object [] boardEdit);							// 게시글 수정
	public void replyWrite(Object [] replyParameter);						// 댓글 작성
	public List<CommentVO> commentSearch(int commentId);			// 각 게시글 선택 시 게시글에 맞는 댓글 조회.
	
}
