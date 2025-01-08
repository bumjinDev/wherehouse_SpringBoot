package com.wherehouse.board.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.wherehouse.board.model.BoardEntity;
import com.wherehouse.board.model.CommentEntity;

public interface IBoardRepository {
	
	public HashMap<String, Object> searchBoardList(int pnIndex);		// 페이지 네이션 요청에 맞게 게시판 목록 제공.
	public void boardWrite(BoardEntity boardEntity);							// 게시판 글 작성.
	public BoardEntity findBoard(int boardId);								// 선택한 게시판의 내용 제공.(게시판 Id로 선택)
	public ArrayList<String> getMembers(int start, int end);	// 각 게시글 별 사용자 닉네임.
	public void upHit(int boardId);											// 선택한 게시글에 대한 조회수 증가.
	public void deleteBoard(int boardId);							// 선택한 게시글 삭제.
	public void boardModify(BoardEntity boardEntity);							// 게시글 수정
	public void replyWrite(CommentEntity comment);						// 댓글 작성
	public List<CommentEntity> commentSearch(int commentId);			// 각 게시글 선택 시 게시글에 맞는 댓글 조회.
	
}
