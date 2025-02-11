package com.wherehouse.restapi.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import com.wherehouse.rest.redius.model.MapDataEntity;
import java.util.List;

import org.springframework.data.domain.Sort;

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
