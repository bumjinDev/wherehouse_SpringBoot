package com.wherehouse.recommand.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wherehouse.recommand.model.RecCharterServiceRequestVO;
import com.wherehouse.recommand.model.RecMonthlyServiceRequestVO;
import com.wherehouse.recommand.model.RecServiceVO;
import com.wherehouse.recommand.service.IRecServiceMonthlyService;
import com.wherehouse.recommand.service.IRecommandCharterService;

import jakarta.validation.Valid;

/* 월세 및 전세에 대한 AJAX 요청 처리하는 컨트롤러. */

@RestController
@RequestMapping(value="/RecServiceController")
public class RecServiceController {
	
	private final Logger logger = LoggerFactory.getLogger(RecServiceController.class);

	IRecommandCharterService recommandCharterService;
	IRecServiceMonthlyService recServiceMonthlyService;
	
	public RecServiceController(
			IRecommandCharterService recommandCharterService,
			IRecServiceMonthlyService recServiceMonthlyService) {
		
		this.recommandCharterService = recommandCharterService;
		this.recServiceMonthlyService = recServiceMonthlyService;
		
	}
	
	/* 전세 요청 처리 */
	@PostMapping("/charter")
	public List<RecServiceVO> ControllerRecServiceCharter(
			@RequestBody @Valid RecCharterServiceRequestVO recCharterServiceRequestVO) {
		
		logger.info("charter 요청 컨트롤러 실행!");
		
		List<RecServiceVO> RecServiceResult = recommandCharterService.recommandCharterService(recCharterServiceRequestVO);
		return RecServiceResult;
		
	}
	
	/* 월세 요청 처리 */
	@PostMapping("/monthly")
	public List<RecServiceVO> ControllerRecServiceMothly(
			@RequestBody @Valid RecMonthlyServiceRequestVO recMonthlyServiceRequestVO) {	
		
		logger.info("/monthly 요청 컨트롤러 실행 !");
		
		List<RecServiceVO> RecServiceResult = recServiceMonthlyService.monthlyRecommandService(recMonthlyServiceRequestVO);
		return RecServiceResult;
	}
}