package com.wherehouse.recommand.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.wherehouse.recommand.model.RecServiceVO;
import com.wherehouse.recommand.service.IRecService;

/* 월세 및 전세에 대한 AJAX 요청 처리하는 컨트롤러. */

@RestController
@RequestMapping(value="/RecServiceController")
public class RecServiceController {
	
	private final Logger logger = LoggerFactory.getLogger(RecServiceController.class);

	IRecService recServiceCharterService;
	
	IRecService recServiceMonthlyService;
	
	public RecServiceController(
			@Qualifier("recServiceCharterService") IRecService recServiceCharterService,
			@Qualifier("recServiceMonthlyService") IRecService recServiceMonthlyService) {
		
		this.recServiceCharterService = recServiceCharterService;
		this.recServiceMonthlyService = recServiceMonthlyService;
		
	}
	
	/* 전세 요청 처리 */
	@PostMapping("/charter")
	public List<RecServiceVO> ControllerRecServiceCharter(@RequestBody Map<String, String>requestAjax) {
		
		logger.info("charter 요청 컨트롤러 실행!");
		
		if(requestAjax.get("charter_avg").equals("")) { return null; }
		else {
			List<RecServiceVO> RecServiceResult = recServiceCharterService.execute(requestAjax);	/* ServiceBean으로 분기하여 `작업 */
			return RecServiceResult;
		}
	}
	
	/* 월세 요청 처리 */
	@PostMapping("/monthly")
	public List<RecServiceVO> ControllerRecServiceMothly(@RequestBody Map<String, String>requestAjax) {	
		
		logger.info("/monthly 요청 컨트롤러 실행 !");
		
		if(requestAjax.get("deposit_avg").equals("")) { return null; }
		else {
			List<RecServiceVO> RecServiceResult = recServiceMonthlyService.execute(requestAjax);		/* ServiceBean으로 분기하여 작업 */
			return RecServiceResult;
		}
	}
}