package com.wherehouse.restapi.mapdata.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;


import java.util.List;

import org.springframework.data.domain.Sort;
import com.wherehouse.restapi.mapdata.model.MapDataEntity;
@Repository
public class MapDataRepository {

    @Autowired
    private MapDataEntityRepository mapDataEntityRepository;

    public List<MapDataEntity> searchRecommandGuAll() {
    	return mapDataEntityRepository.findAll(Sort.by(Sort.Order.asc("id")));
    }
    
    public List<MapDataEntity> searchRecommandGuSelect(List<String> guNames) {
    	return mapDataEntityRepository.findByGunameIn(guNames, Sort.by(Sort.Order.asc("id")));
    }
}
