package com.wherehouse.restapi.mapdata.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.wherehouse.restapi.mapdata.dao.MapDataRepository;
import com.wherehouse.restapi.mapdata.model.MapDataEntity;

import java.time.Duration;
import java.util.*;

@Service
public class MapDataService implements IMapService {

    @Autowired
    private RedisTemplate<String, Map<String, List<Map<String, Double>>>> redisTemplateAllMapData;

    @Autowired
    private RedisTemplate<String, List<MapDataEntity>> redisTemplateChoiceMapData;

    @Autowired
    private MapDataRepository mapDataRepository;

    /**
     * Table "MapDATA" 에서 모든 데이터를 가져옴 (cache-aside 패턴 적용).
     */
    public Map<String, List<Map<String, Double>>> getAllMapDataService() {
    	
    	System.out.println("MapDataService.getAllMapDataService()!");
    	
        String cacheKey = "mapdata:all";

        // 1. Redis 캐시 조회
        Map<String, List<Map<String, Double>>> usedData = redisTemplateAllMapData.opsForValue().get(cacheKey);
        
        if (usedData != null)
            return usedData; // 캐시가 있으면 즉시 반환
        
        // 2. 캐시 미스 발생 → DB에서 모든 지역 데이터 조회
        List<MapDataEntity> loadRepository = mapDataRepository.searchRecommandGuAll();
        // usedData 내 null 이므로 초기화.
        usedData = new HashMap<>();

        processMapDataEntities(loadRepository, usedData);
        // 3. Redis에 저장 (TTL: 24시간)
        redisTemplateAllMapData.opsForValue().set(cacheKey, usedData, Duration.ofHours(24));

        return usedData;
    }

    /**
     * Table "MapDATA" 에서 지정된 지역구 3개 ID 에 대해서만 데이터를 가져옴.
     * cache-aside 패턴 적용: 캐시 조회 → 캐시 미스 발생 시 DB 조회 후 캐시에 저장.
     */
    public Map<String, List<Map<String, Double>>> getChoiceMapDataService(List<String> guNames) {
    	
    	System.out.println("MapDataService.getChoiceMapDataService()!");
    	
        String cacheKeyPrefix = "mapdata:"; // 개별 지역구 키

        Map<String, List<Map<String, Double>>> usedData = new HashMap<>();	// 브라우저에 반환할 데이터 셋
        List<String> emptyGuIdList = new ArrayList<>();		// 브라우저가 요청한 guIds 에 대해 chache miss 발생 guId 리스트

        // 1. 캐시에서 데이터 조회 : guIds 에 대해 한개씩 순회하면서 캐시 데이터 조회 후 chache miss 발생 시 이를 "emptyGuIdList" 내 저장.
        for (String guName : guNames) {
        	// chache 조회
            List<MapDataEntity> cacheLoadData = redisTemplateChoiceMapData.opsForValue().get(cacheKeyPrefix + guName);
            /* chache hit */
            if (cacheLoadData != null) {		// 해당 구 guId 에 대한 캐시 내용이 존재한다면 최종적으로 반환할 "usedData"내 데이터 추가.
            	processMapDataEntities(cacheLoadData, usedData); // 캐시 데이터 또는 DBMS 로드 데이터를 "usedData" 내 저장 메소드
            /* chache miss */
            } else {
                emptyGuIdList.add(guName);
            }
        }
        
        // 2. 캐시 미스 발생 시, 미스 발생한 guId 들에 한해서만 추가적으로 DB에서 조회 후 캐싱
        if (!emptyGuIdList.isEmpty()) {

        	// chache miss 발생한 guId 에 한해 DBMS 에서 데이터 조회
            List<MapDataEntity> loadRepository = mapDataRepository.searchRecommandGuSelect(emptyGuIdList);
            
            // 조회된 데이터들을 우선 브라우저에 반환할 데이터 셋인 "usedData" 내 추가.
            processMapDataEntities(loadRepository, usedData); // 캐시 데이터 또는 DBMS 로드 데이터를 "usedData" 내 저장 메소드

            // 3. 캐시에 저장 (TTL: 1시간) : 캐시 미스가 발생한 guId 들을 DB 에서 로드 하였고, 이를 캐시 redis 에 저장.
            for (String guName : emptyGuIdList) {
            	
                List<MapDataEntity> filteredData = filterByGuName(loadRepository, guName);
                redisTemplateChoiceMapData.opsForValue().set(cacheKeyPrefix + guName, filteredData, Duration.ofHours(1));
            }
        }

        return usedData;
    }

    /**
     * DB에서 조회한 "List<MapDataEntity>" 데이터를 변환하여 JS에서 사용할 수 있는 구조로 가공하는 메서드.
     * 각 "MapDataEntity"에서 "guid", "latitude", "longitude" 데이터를 추출하여,
     * 각 구 이름("guname")을 Key로 하는 "usedData" Map에 저장한다.
     * 
     * - 캐시에서 조회된 데이터 또는 DB에서 조회한 데이터를 처리하는 공통 로직.
     * - 만약 "guname"이 null이라면 해당 데이터를 무시한다. (예외 방지)
     */

    private void processMapDataEntities(List<MapDataEntity> loadRepositoryList, Map<String, List<Map<String, Double>>> usedData) {
    	
        for (MapDataEntity mapDataEntity : loadRepositoryList) {
            if (mapDataEntity.getGuname() == null) {
                continue; // guname이 null이면 저장하지 않음 (예외 방지)
            }
            
            usedData.computeIfAbsent(mapDataEntity.getGuname(), k -> new ArrayList<>()).add(
                Map.of(
                    "guid", mapDataEntity.getGuid(),
                    "latitude", mapDataEntity.getLatitude(),
                    "longitude", mapDataEntity.getLongitude()
                )
            );
        }
    }


    /**
     * DB에서 가져온 전체 데이터 중 특정 "guId"에 해당하는 데이터만 필터링하는 메서드.
     * 캐시 미스 발생 시, DB에서 불러온 데이터 중 요청된 "guId"에 해당하는 데이터만 반환한다.
     * 
     * - "guId"가 null이거나, "entity.getGuid()"가 null인 경우 예외 발생을 방지함.
     */

    private List<MapDataEntity> filterByGuName(List<MapDataEntity> loadRepository, String guName) {
    	
        List<MapDataEntity> filteredList = new ArrayList<>();
        
        if (guName == null) {
            return filteredList; // guName이 null이면 빈 리스트 반환
        }
        for (MapDataEntity entity : loadRepository) {
            if (entity.getGuname() != null && entity.getGuname().equals(guName)) {
                filteredList.add(entity);
            }
        }
        return filteredList;
    }

}
