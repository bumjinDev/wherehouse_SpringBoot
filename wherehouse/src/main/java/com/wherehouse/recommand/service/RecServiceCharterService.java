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
	public List<RecServiceVO> execute(Map<String, String> requestAjax) {
	    logger.info("RecServiceCharterService.execute()!");

	    // 방어 코드 추가: null 체크
	    if (requestAjax == null || !requestAjax.containsKey("charter_avg") ||
	        !requestAjax.containsKey("safe_score") || !requestAjax.containsKey("cvt_score")) {
	        throw new IllegalArgumentException("Invalid request data");
	    }

	    // 로그 출력
	    logger.info("charter_avg: " + requestAjax.get("charter_avg"));
	    logger.info("safe_score: " + requestAjax.get("safe_score"));
	    logger.info("cvt_score: " + requestAjax.get("cvt_score"));

	    // `Integer.parseInt()` 사용 시 예외 발생 가능성 방지
	    int charterAvg = Integer.parseInt(requestAjax.get("charter_avg"));
	    int safeScore = Integer.parseInt(requestAjax.get("safe_score"));
	    int cvtScore = Integer.parseInt(requestAjax.get("cvt_score"));

	    return recServiceEmpRepository.chooseCharterRec(charterAvg, safeScore, cvtScore);
	}
}
