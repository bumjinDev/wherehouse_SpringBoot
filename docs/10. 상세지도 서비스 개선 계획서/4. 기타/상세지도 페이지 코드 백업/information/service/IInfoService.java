package com.wherehouse.information.service;

import java.util.List;

import com.wherehouse.information.model.AddrRateVO;
import com.wherehouse.information.model.CctvVO;
import com.wherehouse.information.model.PoliceOfficeVO;


public interface IInfoService {
	List<PoliceOfficeVO> getListPO();
	PoliceOfficeVO getClosestPO(double latitude, double longitude);
	List<CctvVO> getListCCTV(double latitude, double longitude);
	AddrRateVO getRate(String addr);
}
