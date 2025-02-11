package com.wherehouse.information.dao;


import org.apache.ibatis.annotations.Param;

import com.wherehouse.information.model.AddrRateVO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IAddrRateRepository {
	AddrRateVO getRate(@Param("address") String addr);
}
