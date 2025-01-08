package com.wherehouse.information.controller;

import java.util.List;
import java.util.Map;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.wherehouse.information.model.AddrRateVO;
import com.wherehouse.information.model.CctvVO;
import com.wherehouse.information.model.PoliceOfficeVO;
import com.wherehouse.information.service.IInfoService;

@Controller
public class InformationController {
	
	@Autowired
	IInfoService infoService;
	
	 @GetMapping("/information")
	 public String redirectToPage() {
		 return "information/informationPage";
	 }
	
	 @GetMapping("/information/policeOffice")
	 public @ResponseBody List<PoliceOfficeVO> getListPO() {
		 System.out.println("getListPO 메소드 호출");
		 return infoService.getListPO();
	 }
	 
	 @GetMapping("/information/dist")
	 public @ResponseBody PoliceOfficeVO getClosest(@RequestParam Map<String, String> param) {
		 double lat = Double.parseDouble(param.get("latitude"));
		 double lng = Double.parseDouble(param.get("longitude"));
		 return infoService.getClosestPO(lat, lng);
	 }
	 
	 @GetMapping("/information/cctv")
	 public @ResponseBody List<CctvVO> getListCCTV(@RequestParam Map<String, String> param) {
		 double lat = Double.parseDouble(param.get("latitude"));
		 double lng = Double.parseDouble(param.get("longitude"));
		 return infoService.getListCCTV(lat, lng);
	 }
	 
	 @GetMapping("/information/rate")
	 public @ResponseBody AddrRateVO getRate(@RequestParam(value = "addr") String param) {
		 return infoService.getRate(param);
	 }

}
