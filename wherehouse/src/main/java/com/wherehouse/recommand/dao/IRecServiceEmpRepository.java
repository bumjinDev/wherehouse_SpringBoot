package com.wherehouse.recommand.dao;


import java.util.List;

import com.wherehouse.recommand.model.*;

public interface IRecServiceEmpRepository {

	
	public List<RecServiceVO> chooseCharterRec(int inputData, int safe, int cvt);							/* 전세 요청 담당 */
	public List<RecServiceVO> chooseMonthlyRec(int deposit, int monthly, int safe, int cvt);			/* 월세 요청 담당 */
	
}
