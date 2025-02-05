package com.wherehouse.rest.redius.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wherehouse.rest.redius.model.MapDataEntity;
import com.wherehouse.restapi.dao.MapDataRepository;

import java.util.*;

@Service
public class MapDataService {

    @Autowired
    private MapDataRepository mapDataRepository;

    public Map<String, List<Map<String, Double>>> getAllMapData() {
        // DB에서 데이터를 가져옴 (순서 유지)
        List<MapDataEntity> respositoryData = mapDataRepository.searchRecommandGuAll();

        // 결과 데이터 저장 (순서 유지)
        Map<String, List<Map<String, Double>>> restDataSet = new LinkedHashMap<>();

        /*
         * JSON 구조 (DB 순서 유지)
         * {
         *   "강북구": [
         *     { "guid": 1, "latitude": 37.5665, "longitude": 126.9780 },
         *     { "guid": 2, "latitude": 35.1796, "longitude": 129.0756 }
         *   ],
         *   "강남구": [
         *     { "guid": 3, "latitude": 36.3504, "longitude": 127.3845 }
         *   ]
         * }
         */

        for (MapDataEntity mapDataEntity : respositoryData) {
            // 좌표 정보를 저장할 LinkedHashMap (순서 유지)
            Map<String, Double> address = new LinkedHashMap<>();
            address.put("guid", mapDataEntity.getGuid());
            address.put("latitude", mapDataEntity.getLatitude());
            address.put("longitude", mapDataEntity.getLongitude());

            // 해당 구(guname)의 리스트가 없으면 새로 생성 (순서 유지)
            restDataSet.computeIfAbsent(mapDataEntity.getGuname(), k -> new ArrayList<>());

            // 리스트의 맨 뒤에 추가하여 원래 순서 유지
            restDataSet.get(mapDataEntity.getGuname()).add(address);
        }

        return restDataSet;
    }
}
