package com.wherehouse.rest.redius.dao;

import java.util.List;

import com.wherehouse.rest.redius.model.MapDataENtity;


public interface IMapDataRepository {

	List<MapDataENtity> searchRecommandGu(List<Integer> guIds);
}
