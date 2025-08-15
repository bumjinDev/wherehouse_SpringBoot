package com.wherehouse.recommand.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import com.wherehouse.recommand.model.RecServiceVO;

@Repository
public class RecServiceEmpRepository implements IRecServiceEmpRepository {
	
	private final Logger logger = LoggerFactory.getLogger(RecServiceEmpRepository.class);
	
	JdbcTemplate jdbcTemplate;
	
	public RecServiceEmpRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}
	
	@Override
	public List<RecServiceVO> chooseCharterRec(String query, Object[] params) {							/* 전세 요청 담당 */
		
		logger.info("RecServiceEmpRepository.chooseCharterRec()!");
		
        return jdbcTemplate.query(query, new EmpMapper(), params);
	}

	/* 월세 요청 담당 */
	@Override
	public List<RecServiceVO> chooseMonthlyRec(String query, Object[] params) {	
		
		logger.info("RecServiceEmpRepository.chooseMonthlyRec()!");
        return jdbcTemplate.query(query, new EmpMapper(), params);
	}
	
	private class EmpMapper implements RowMapper<RecServiceVO>{			/* jdbcTemplate 에서 가져올 RowMapper 구현 클래스 */

		@Override
		public RecServiceVO mapRow(ResultSet rs, int rowNum) throws SQLException {
				
			RecServiceVO dto = new RecServiceVO();
			
			dto.setGu_id(rs.getInt("gu_id"));
            dto.setGu_name(rs.getString("gu_name"));	
            dto.setCvt_score(rs.getInt("cvt_score"));
            dto.setSafe_score(rs.getInt("safe_score"));
            dto.setCafe(rs.getInt("cafe"));
            dto.setCvt_store(rs.getInt("cvt_store"));
            dto.setDaiso(rs.getInt("daiso"));
            dto.setOliveYoung(rs.getInt("oliveYoung"));
            dto.setRestourant(rs.getInt("restourant"));
            dto.setPolice_office(rs.getInt("police_office"));
            dto.setCctv(rs.getInt("cctv"));
            dto.setCharter_avg(rs.getInt("charter_avg"));
            dto.setDeposit_avg(rs.getInt("deposit_avg"));
            dto.setMonthly_avg(rs.getInt("monthly_avg"));
            
			return dto;
		}
	}
}
