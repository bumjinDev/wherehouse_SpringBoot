package com.wherehouse.information.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wherehouse.information.dao.IAddrRateRepository;
import com.wherehouse.information.dao.ICCTVRepository;
import com.wherehouse.information.dao.IPoliceOfficeRepository;
import com.wherehouse.information.model.AddrRateVO;
import com.wherehouse.information.model.CctvVO;
import com.wherehouse.information.model.PoliceOfficeVO;

@Service
public class InfoService implements IInfoService {
	
	@Autowired
	IPoliceOfficeRepository policeOfficeRepo;
	@Autowired
	ICCTVRepository cctvRepo;
	@Autowired
	IAddrRateRepository addrRateRepo;
	
	@Override
	public List<PoliceOfficeVO> getListPO() {
		System.out.println("#getListPO");
		System.out.println(policeOfficeRepo.getListPO());
		return policeOfficeRepo.getListPO();
	}

	@Override
	public PoliceOfficeVO getClosestPO(double latitude, double longitude) {
		System.out.println("#getClosestPO");
		return policeOfficeRepo.getClosestPO(latitude, longitude);
	}
	
	@Override
	public List<CctvVO> getListCCTV(double latitude, double longitude) {
		System.out.println("#getListCCTV");
		return cctvRepo.getListCCTV(latitude, longitude);
	}
	
	@Override
	public AddrRateVO getRate(String addr) {
		System.out.println("#getRate");
		return addrRateRepo.getRate(addr);
	}
}
