package com.wherehouse.restapi.dao;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import com.wherehouse.rest.redius.model.MapDataENtity;

@Repository
public class MapDataRepository implements IMapDataRepository {

    @Autowired
    private MapDataEntityRepository mapDataEntityRepository;

    @Override
    public List<MapDataENtity> searchRecommandGu(List<Integer> guIds) {
        System.out.println("MapDataRepository.searchRecommandGu()!");

        List<MapDataENtity> entities = mapDataEntityRepository.findByGuidIn(guIds);

        if (entities.isEmpty()) {
            System.out.println("조회 결과가 없습니다.");
        } else {
            entities.forEach(entity -> {
            	
            	if(!(entity.getLatitude().equals(37.5681874428964))) {
            	
            		System.out.println("GUID: " + entity.getGuid());
            		System.out.println("GUNAME: " + entity.getGuname());
            		System.out.println("LAT: " + entity.getLatitude() + ", LNG : " + entity.getLongitude());
            	}
            });
        }

        return entities;
    }
}
