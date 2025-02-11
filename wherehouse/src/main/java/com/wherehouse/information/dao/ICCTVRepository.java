package com.wherehouse.information.dao;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import com.wherehouse.information.model.CctvVO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ICCTVRepository {
	List<CctvVO> getListCCTV(@Param("latitude") double latitude,@Param("longitude") double longitude);
}
