/* 까꿍 */
package com.wherehouse.board.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import org.springframework.stereotype.Repository;
import com.wherehouse.board.model.BoardVO;
import com.wherehouse.board.model.CommentVO;

@Repository
public class BoardRepository implements IBoardRepository  {

	@Autowired
	JdbcTemplate jdbcTemplate;
	
	@Override
	public HashMap<String, Object> SearchBoardList(int pnIndex) {
		
		String query = "select * from whereboard order by connum desc";		// 게시글 조회 쿼리.
		
		List<BoardVO> boardAllList = jdbcTemplate.query(query, new BoardVOMapper());		// 게시글 목록 전체 가져오기.(BoardVO로 변환)
		List <BoardVO> boardList = new ArrayList<BoardVO>();										// 전체 게시글 내용 중에서 사용자의 페이지 네이션 지정 번호 따른 저장 배열
		
		ArrayList<String> userId = new ArrayList<String>();											// 선택된 각각의 게시글 작성자 닉네임 저장.
		
		HashMap<String, Object> resultBoard = new HashMap<String, Object>();				// list.jsp 내 보여질 전체 데이터.
		
		/* pnMaxSize : 게시글 전체 페이지 개수, 한 페이지당 보여질 게시글 10개로 정해서 '전체 게시글 수/10' 의 결과 수
		 * pnEnd : 사용자로부터 요청 받은 게시글 페이지(페이지 네이션 요청 번호) */
		int pnSize = ((int) Math.ceil(boardAllList.size()/10))+1;
		pnIndex = pnIndex * 10;
		int pnEnd = pnIndex + 9;
		
		/* 전체 게시글 목록에서 사용자 요청 페이지에 해당하는 게시글 페이지 내 게시글을(최대 10개) 가져오기. */
		for(; pnIndex <= pnEnd && pnIndex < boardAllList.size(); pnIndex ++) {
			
			boardList.add(boardAllList.get(pnIndex));
			userId.add(boardAllList.get(pnIndex).getuserid());
		}
		
		/* "list.jsp" 페이지 내 렌더링 될 데이터들.
		 * 
		 * 1. 사용자가 페이지 선택 버튼 통해 특정 페이지 내 게시글 목록 요청 후 결과로 제공될 페이지 내 게시글 페이지 선택 버튼 개수.
		 * 2. 게시글 전체 개수
		 * 3. 각각의 게시글 작성자 닉네임들 */
		
		resultBoard.put("pnSize", pnSize);	
		resultBoard.put("boardList", boardList);
		resultBoard.put("members", getMembers(userId));
		
		return resultBoard;
	}

	/* 새로운 게시글 작성. */
	@Override
	public void boardWrite(Object [] parameters) {
		
		String query = "insert into whereboard(connum, id, title, content, region)"
				+ "			values(whereboarder_seq.nextval, ?, ?, ?, ?)";
		
		jdbcTemplate.update(query, parameters);
		
	}

	/* 게시글 목록에서 게시글 선택 시 해당 게시글 내용 상세를 BoardVO 객체로 변환. */
	@Override
	public BoardVO boardView(int boardId) {
		
		String query = "select * from whereboard where connum = ?";
		return jdbcTemplate.queryForObject(query, new BoardVOMapper(),  boardId);
	}

	@Override
	public void replyWrite(Object[] replyParameter) {
		
		String query = "insert into commenttbl(num, id, connum, nickname, title, content)"
				+ "values (?, ?, whereboarder_seq.nextval, ?, ?, ?)";
		
		jdbcTemplate.update(query, replyParameter);
	}
	
	/* 각 게시글 별 사용자 닉네임들. */
	@Override
	public ArrayList<String> getMembers(ArrayList<String> userId) {
		
		String query = "select nickname from membertbl where id = ?";															// 닉네임 조회 쿼리.
		ArrayList <String> members = new ArrayList<String>();
		
//		MapSqlParameterSource params = new MapSqlParameterSource();
	
		for(String searchId : userId) {
			
			
//		    String result = jdbcTemplate.queryForObject(query, new Object[]{searchId}, String.class);
		    String result = jdbcTemplate.queryForObject(query, String.class, searchId);
		    members.add(result);
		}
		
		return members;
	}
	
	
	/* 게시글 내 작성된 댓글들 */
	@Override
	public List<CommentVO> commentSearch(int commentId) {
		
		String query = "select * from commenttbl where num = ?";
		return jdbcTemplate.query(query, new CommentVOMapper(), commentId);		// List 로 반환.
	}
	
	private class CommentVOMapper implements RowMapper<CommentVO> {

		@Override
		public CommentVO mapRow(ResultSet rs, int rowNum) throws SQLException {
			
			CommentVO commentVO = new CommentVO();
			
			commentVO.setnum(rs.getInt("num"));
			commentVO.setid(rs.getString("id"));
			commentVO.setnum(rs.getInt("connum"));
			commentVO.setnickname(rs.getString("nickname"));
			commentVO.settitle(rs.getString("title"));
			commentVO.setcontent(rs.getString("content"));
			commentVO.setrecorddate(rs.getDate("bdate"));
			
			return commentVO;
		}
		
	}
	
	/* 게시글 삭제. */
	@Override
	public void boardDelete(String bId) {
		
		String query = "delete from whereboard where connum = ?";
		jdbcTemplate.update(query, bId);
	}
	
	/* 각 게시글 내용 확인 할 때 조회수 증가 */
	@Override
	public void upHit(int boardId) {
		
		String query = "update whereboard set HIT = HIT+1 where connum = ?";
		
		jdbcTemplate.update(query, boardId);
	}
	
	@Override
	public void boardModify(Object [] boardEdit) {
		
		String query = "update whereboard set title = ?, content = ?, region =? where connum = ?";
		jdbcTemplate.update(query, boardEdit);
	}
	
	/* 게시글 VO 객체 생성. */
	private class BoardVOMapper implements RowMapper<BoardVO> {

		@Override
		public BoardVO mapRow(ResultSet rs, int rowNum) throws SQLException {
			
			BoardVO boardVO = new BoardVO();
			
			boardVO.setcontentnum(rs.getInt("connum"));
			boardVO.setuserid(rs.getString("id"));
			boardVO.settitle(rs.getString("title"));
			boardVO.setbcontent(rs.getString("content"));
			boardVO.setregion(rs.getString("region"));
			boardVO.sethit(rs.getInt("hit"));
			boardVO.setbdate(rs.getDate("bdate"));
			
			return boardVO;
		}
	}

}