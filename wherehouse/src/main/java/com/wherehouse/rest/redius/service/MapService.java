package com.wherehouse.rest.redius.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wherehouse.rest.redius.model.MapDataENtity;
import com.wherehouse.restapi.dao.IMapDataRepository;

@Service
public class MapService implements IMapService {

    @Autowired
    private IMapDataRepository mapDataRepository;

    @Override
    public Map<String, List<Map<String, Double>>> getLocations(List<Integer> guIds) {
        System.out.println("MapService.getLocations()!");

        List<MapDataENtity> entities = mapDataRepository.searchRecommandGu(guIds);

        if (entities.isEmpty()) {
            System.out.println("조회 결과가 없습니다.");
            return Map.of(); // 빈 맵 반환
        }

        return entities.stream()
            .collect(Collectors.groupingBy(
                MapDataENtity::getGuname,
                Collectors.mapping(entity -> Map.of(
                    "latitude", entity.getLatitude(),
                    "longitude", entity.getLongitude()
                ), Collectors.toList())
            ));
    }
}
