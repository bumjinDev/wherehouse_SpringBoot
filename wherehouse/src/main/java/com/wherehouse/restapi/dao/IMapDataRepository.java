package com.wherehouse.restapi.dao;

import java.util.List;
import com.wherehouse.rest.redius.model.MapDataEntity;

public interface IMapDataRepository {
	List<MapDataEntity> searchRecommandGuAll();
	public List<MapDataEntity> searchRecommandGuSelect(List<Double> chacheMissGuIds);
}
