package com.wherehouse.recommand.dao;


import java.util.List;

import com.wherehouse.recommand.model.*;

public interface IRecServiceEmpRepository {

	
	public List<RecServiceVO> chooseCharterRec(String query, Object[] params);							/* 전세 요청 담당 */
	public List<RecServiceVO> chooseMonthlyRec(String query, Object[] params);			/* 월세 요청 담당 */
	
}
