package com.wherehouse.recommand.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.wherehouse.recommand.dao.IRecServiceEmpRepository;
import com.wherehouse.recommand.model.RecServiceVO;

/* 월세 요청 처리 */
@Service
public class RecServiceMonthlyService implements IRecService{

	IRecServiceEmpRepository recServiceEmpRepository;
	
	private static final Logger logger = LoggerFactory.getLogger(RecServiceMonthlyService.class);
	
	public RecServiceMonthlyService(IRecServiceEmpRepository recServiceEmpRepository) {
		this.recServiceEmpRepository = recServiceEmpRepository;
	}
	
	@Override
	public List<RecServiceVO> execute(Map<String, String> requestAjax) {
		
		logger.info("RecServiceMonthlyService.execute()!");
		logger.info("" + Integer.parseInt(requestAjax.get("deposit_avg")));
		logger.info("" + Integer.parseInt(requestAjax.get("monthly_avg")));
		logger.info("" + Integer.parseInt(requestAjax.get("safe_score")));
		logger.info("" + Integer.parseInt(requestAjax.get("cvt_score")));
		
		List<RecServiceVO> RecServiceResult = recServiceEmpRepository.chooseMonthlyRec(
																		Integer.parseInt(requestAjax.get("deposit_avg")),
																		Integer.parseInt(requestAjax.get("monthly_avg")), 
																		Integer.parseInt(requestAjax.get("safe_score")),
																		Integer.parseInt(requestAjax.get("cvt_score")));
		
		return RecServiceResult;
	}
}