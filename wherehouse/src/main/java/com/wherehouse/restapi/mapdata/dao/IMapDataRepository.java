package com.wherehouse.restapi.mapdata.dao;

import java.util.List;

import com.wherehouse.restapi.mapdata.model.MapDataEntity;


public interface IMapDataRepository {
	List<MapDataEntity> searchRecommandGuAll();
	public List<MapDataEntity> searchRecommandGuSelect(List<Double> chacheMissGuIds);
}
