package com.wherehouse.recommand.service;

import java.util.List;

import com.wherehouse.recommand.model.RecMonthlyServiceRequestVO;
import com.wherehouse.recommand.model.RecServiceVO;

public interface IRecServiceMonthlyService {
	
	public List<RecServiceVO> monthlyRecommandService(RecMonthlyServiceRequestVO recMonthlyServiceRequestVO);		/* ajax 데이터를 받아서 처리하는 메소드, 결과는 Object로 반환 함으로써 2개의 Service 빈(인스턴스)를 반환할 수 있게 설계 */
}
