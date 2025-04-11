package com.wherehouse.recommand.service;

import java.util.List;

import com.wherehouse.recommand.model.RecCharterServiceRequestVO;
import com.wherehouse.recommand.model.RecServiceVO;

public interface IRecommandCharterService {

	public List<RecServiceVO> recommandCharterService(RecCharterServiceRequestVO recCharterServiceRequestVO);
}
