package com.wherehouse.members.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import java.util.Map;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.wherehouse.members.model.MembersVO;

@Repository
public class MembersRepository implements IMembersRepository {

	@Autowired
	JdbcTemplate jdbcTemplate;

	/* 로그인 요청 시 올바른 요청인지 확인.  */
	@Override
	public Map<String, Object> checkMember(String id, String pw) {
		
		String query = "select * from membertbl where id = ?";
		HashMap<String, Object> resObj = new HashMap<String, Object>(); 
		
		/* 로그인 요청 결과로 ID 불일치 시 -1, pw 불일치 시 0, 올바른 로그인 시도라면 1 반환 */		
		
		MembersVO membersVO = null;
		
		try {
			membersVO = jdbcTemplate.queryForObject(query, new MemberVOMapper(), new Object[] {id});	
		}catch(EmptyResultDataAccessException e) {		/* JdbcTemplate.queryForObject() 결과가 없다면 해당하는 Id 값이 없다는 의미니 -1 반환 */
			
			System.out.println("MembersRepository.checkMember : -1 반환");
			
			resObj.put("resInt", "-1");
			return resObj;
		}
		
		if(membersVO.getPw().equals(pw)) {		/* id로 회원이 조회되며 동시에 pw도 같으면 올바른 사용자라 확인. */
			System.out.println("MembersRepository.checkMember : 0 반환");
			resObj.put("membersVO", membersVO);
			resObj.put("resInt", "1");
			return resObj;
		}else {											/* id로 조회가 되나 pw가 다르다면 올바르지 않은 로그인 시도라 판단 */
			System.out.println("MembersRepository.checkMember : 1 반환");
			resObj.put("resInt", "0");
			return resObj;
		}
	}

	/* 회원 가입 요청 시 이미 존재하는 회원 Id 인지 확인. */
	@Override
	public int confimMember(String id) {
		
		String query = "select * from membertbl where id = ?";
		
		/* 반환된 튜플 개수가 존재 할 시 이미 가입되어 있는 사용자, 아닐 시 존재하지 않는 사용자. */
		if(jdbcTemplate.queryForList(query, id).size() >= 1){
			
			return 1;
		} else {
			return 0;
		}
	}

	/* 새로운 회원 가입 실행 */
	@Override
	public int addMember(Map <String, String> requestData) {
		
		String query = "insert into membertbl (id, pw, nickname, tel, email) values (?,?,?,?,?)";
		jdbcTemplate.update(query,  new Object[] { requestData.get("id"), requestData.get("pw"), requestData.get("nickName"), requestData.get("tel"), requestData.get("email")});
		
		return 0;
	}
	
	/* 회원 Id로 사용자 검색. */
	@Override
	public MembersVO getMember(String parameterId) {
		
		String query = "select * from membertbl where id = ?";
		System.out.println("디버깅 : parameterId : " + parameterId);
		
		return jdbcTemplate.queryForObject(query, new MemberVOMapper(), parameterId);
	}
	
	/* 회원 수정 */
	@Override
	public int editMember(Object[] editParameter) {
		
		String nickquery = "select * from membertbl where nickname = ? and  id !=?";			//  회원 정보 수정 요청 중 닉네임이 중복되는 지 확인;
		String query = "update membertbl set pw=?, nickname=?, tel=?, email=? where id=?";				// 회원 정보 수정 쿼리.
			
		/* 회원 수정 요청 중 닉네임이 기존과 일치하지 않는 다면 작업, 아니라면 닉네임 중복을 회원에게 알림. */
		if( jdbcTemplate.query(nickquery, new MemberVOMapper(), new Object[] {editParameter[1], editParameter[4]}).isEmpty()) {
			System.out.println("닉네임 중복 아닐 시 실행하는 부분 실행");
			return jdbcTemplate.update(query, editParameter) ; 
	
		} else {
			System.out.println("닉네임 중복 났으면 실행.");
			return 2;
		}
	}
	
	private class MemberVOMapper implements RowMapper<MembersVO> {

		@Override
		public MembersVO mapRow(ResultSet rs, int rowNum) throws SQLException {
			
			MembersVO membersVo = new MembersVO();
			
			membersVo.setId(rs.getString("id"));
			membersVo.setPw(rs.getString("pw"));
			membersVo.setNickName(rs.getString("nickname"));
			membersVo.setTel(rs.getString("tel"));
			membersVo.setEmail(rs.getString("email"));
			membersVo.setJoinDate(rs.getDate("joinDate"));
			
			return membersVo;
		}
	}

	
}
