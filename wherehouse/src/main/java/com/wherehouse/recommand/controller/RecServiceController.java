package com.wherehouse.recommand.controller;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.wherehouse.recommand.model.RecServiceVO;
import com.wherehouse.recommand.service.IRecService;

import jakarta.annotation.PostConstruct;

/* 월세 및 전세에 대한 AJAX 요청 처리하는 컨트롤러. */

@Controller
@RequestMapping(value="/RecServiceController")
public class RecServiceController {
	
	@PostConstruct
	public void init() {
		System.out.println("컨트롤러 RecServiceController 실행");
	}
	
	@Autowired
	@Qualifier("recServiceCharterService")
	IRecService recServiceCharterService;
	
	@Autowired
	@Qualifier("recServiceMonthlyService")
	IRecService recServiceMonthlyService;
	
	/* 전세 요청 처리 */
	@PostMapping("/charter")
	public @ResponseBody List<RecServiceVO> ControllerRecServiceCharter(@RequestBody Map<String, String>requestAjax) {
		System.out.println("/charter.do 메소드 실행!");
		
		if(requestAjax.get("charter_avg").equals("")) {
			return null;
		} else {
			
			List<RecServiceVO> RecServiceResult = recServiceCharterService.execute(requestAjax);			/* ServiceBean으로 분기하여 `작업 */
			return RecServiceResult;
		}
	}
	
	/* 월세 요청 처리 */
	@PostMapping("/monthly")
	public @ResponseBody List<RecServiceVO> ControllerRecServiceMothly(@RequestBody Map<String, String>requestAjax) {	
		System.out.println("/monthly 메소드 실행 !");
		
		if(requestAjax.get("deposit_avg").equals("")) {
			return null;
		} else {
			List<RecServiceVO> RecServiceResult = recServiceMonthlyService.execute(requestAjax);		/* ServiceBean으로 분기하여 작업 */
			return RecServiceResult;
		}
	}
}