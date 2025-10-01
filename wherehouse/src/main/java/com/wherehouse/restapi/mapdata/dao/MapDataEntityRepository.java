package com.wherehouse.restapi.mapdata.dao;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import com.wherehouse.restapi.mapdata.model.MapDataEntity;


public interface MapDataEntityRepository extends JpaRepository<MapDataEntity, Integer> {
    List<MapDataEntity> findAll();
	//List<MapDataEntity> findByGuidIn(List<Double> guIds, Sort by);
	List<MapDataEntity> findByGunameIn(List<String> guNames, Sort by);
}
