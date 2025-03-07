package com.wherehouse.recommand.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.wherehouse.recommand.dao.IRecServiceEmpRepository;
import com.wherehouse.recommand.model.RecServiceVO;

/* 전세금 요청 처리 */
@Service
public class RecServiceCharterService implements IRecService{

	private final Logger logger = LoggerFactory.getLogger(RecServiceCharterService.class);
	
	IRecServiceEmpRepository recServiceEmpRepository;
	
	public RecServiceCharterService(IRecServiceEmpRepository recServiceEmpRepository) {
		this.recServiceEmpRepository = recServiceEmpRepository;
	}
	
	@Override
	public List<RecServiceVO> execute(Map <String, String> requestAjax) {
		
		logger.info("RecServiceCharterService.execute()!");
		/* 입력받은 데이터를 각 변수안에 삽입. */
		List<RecServiceVO> RecServiceResult = recServiceEmpRepository.chooseCharterRec(
												Integer.parseInt(requestAjax.get("charter_avg")),
												Integer.parseInt(requestAjax.get("safe_score")),
												Integer.parseInt(requestAjax.get("cvt_score"))
											  );		
		return RecServiceResult;
	}
}
