package com.wherehouse.recommand.batch.BatchScheduler;

import com.wherehouse.recommand.batch.dto.DistrictCrimeCountDto;
import com.wherehouse.recommand.batch.dto.Property;
import com.wherehouse.recommand.batch.entity.PropertyCharter;
import com.wherehouse.recommand.batch.entity.PropertyMonthly;
import com.wherehouse.recommand.batch.event.DataCollectionCompletedEvent;
import com.wherehouse.recommand.batch.repository.*;
import com.wherehouse.redis.handler.RedisHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 배치 데이터 동기화 리스너 (Processor Phase)
 *
 * 역할:
 * 1. DataCollectionCompletedEvent 수신 (비동기 처리)
 * 2. Oracle RDB에 매물 데이터 적재 (Source of Truth 확보)
 * 3. Redis에 매물 정보 및 검색 인덱스 적재
 * 4. [추가] 정규화 범위(Bounds) 계산 및 Redis 적재
 * 5. [추가] 안전성 점수(Safety Score) 계산 및 Redis 적재
 *
 * [수정 사항]
 * - 정규화 범위 및 안전성 점수 저장 시 상세 정보를 log.info로 출력하도록 로깅 추가
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RdbSyncListener {

    // 매물 리포지토리
    private final PropertyCharterRepository propertyCharterRepository;
    private final PropertyMonthlyRepository propertyMonthlyRepository;

    // 분석 데이터 리포지토리 (안전성 점수 계산용)
    private final AnalysisEntertainmentRepository entertainmentRepository;
    private final AnalysisPopulationDensityRepository populationRepository;
    private final AnalysisCrimeRepository crimeRepository;

    private final RedisHandler redisHandler;

    // 대량의 Redis 연산(Pipeline)을 위해 RedisTemplate을 직접 사용합니다.
    private final RedisTemplate<String, Object> redisTemplate;

    // 서울시 25개 자치구 코드 매핑 (안전성 점수 계산용)
    private static final Map<String, String> SEOUL_DISTRICT_CODES;
    static {
        Map<String, String> codes = new HashMap<>();
        codes.put("11110", "종로구"); codes.put("11140", "중구"); codes.put("11170", "용산구");
        codes.put("11200", "성동구"); codes.put("11215", "광진구"); codes.put("11230", "동대문구");
        codes.put("11260", "중랑구"); codes.put("11290", "성북구"); codes.put("11305", "강북구");
        codes.put("11320", "도봉구"); codes.put("11350", "노원구"); codes.put("11380", "은평구");
        codes.put("11410", "서대문구"); codes.put("11440", "마포구"); codes.put("11470", "양천구");
        codes.put("11500", "강서구"); codes.put("11530", "구로구"); codes.put("11545", "금천구");
        codes.put("11560", "영등포구"); codes.put("11590", "동작구"); codes.put("11620", "관악구");
        codes.put("11650", "서초구"); codes.put("11680", "강남구"); codes.put("11710", "송파구");
        codes.put("11740", "강동구");
        SEOUL_DISTRICT_CODES = Collections.unmodifiableMap(codes);
    }

    /**
     * 데이터 수집 완료 이벤트 핸들러
     *
     * @param event 수집된 매물 데이터(전세/월세 리스트)가 담긴 이벤트 객체
     */
    @EventListener
    @Transactional
    public void handleDataCollectionCompletedEvent(DataCollectionCompletedEvent event) {
        log.info(">>> [Phase 2] RdbSyncListener 이벤트 수신. 데이터 적재 프로세스 시작. (전세: {}건, 월세: {}건)",
                event.getCharterCount(), event.getMonthlyCount());

        long startTime = System.currentTimeMillis();

        // =================================================================================
        // Step 1. [RDB] 매물 원본 데이터 적재 (Upsert) - 기존 유지
        // =================================================================================
        saveCharterPropertiesToRdb(event.getCharterProperties());
        saveMonthlyPropertiesToRdb(event.getMonthlyProperties());

        /*
         * =================================================================================
         * [TODO: Phase 3 - 리뷰 시스템 데이터 정합성 확보 로직]
         * // reviewStatisticsService.initAndReconcileStatistics(event);
         * =================================================================================
         */

        log.info(">>> [Phase 2-1] RDB 적재 완료. Redis 동기화(Pipeline) 시작.");

        // =================================================================================
        // Step 2. [Redis] 매물 정보 및 검색 인덱스 동기화 - 기존 유지
        // =================================================================================
        syncCharterToRedis(event.getCharterProperties());
        syncMonthlyToRedis(event.getMonthlyProperties());

        /*
         * =================================================================================
         * [TODO: Phase 3 - 리뷰 통계 캐시 동기화]
         * // reviewStatisticsService.syncStatisticsToRedis();
         * =================================================================================
         */

        // =================================================================================
        // Step 3. [Redis] 정규화 범위(Bounds) 계산 및 적재 - 추가됨
        // =================================================================================
        // 정규화 계산을 위해 전세/월세 데이터를 합칩니다.
        List<Property> allProperties = new ArrayList<>();
        if (event.getCharterProperties() != null) allProperties.addAll(event.getCharterProperties());
        if (event.getMonthlyProperties() != null) allProperties.addAll(event.getMonthlyProperties());

        calculateAndStoreNormalizationBounds(allProperties);

        // =================================================================================
        // Step 4. [Redis] 안전성 점수(Safety Score) 계산 및 적재 - 추가됨
        // =================================================================================
        calculateAndStoreSafetyScores();

        long endTime = System.currentTimeMillis();
        log.info(">>> [Phase 2] 배치 동기화 프로세스 정상 종료. 총 소요시간: {}ms", (endTime - startTime));
    }

    // =================================================================================
    // Internal Methods: RDB Operation
    // =================================================================================

    private void saveCharterPropertiesToRdb(List<Property> properties) {
        if (properties == null || properties.isEmpty()) return;

        List<PropertyCharter> entities = properties.stream()
                .map(PropertyCharter::from)
                .collect(Collectors.toList());

        propertyCharterRepository.saveAll(entities);
        log.info("RDB: 전세 매물 {}건 저장 완료", entities.size());
    }

    private void saveMonthlyPropertiesToRdb(List<Property> properties) {
        if (properties == null || properties.isEmpty()) return;

        List<PropertyMonthly> entities = properties.stream()
                .map(PropertyMonthly::from)
                .collect(Collectors.toList());

        propertyMonthlyRepository.saveAll(entities);
        log.info("RDB: 월세 매물 {}건 저장 완료", entities.size());
    }

    // =================================================================================
    // Internal Methods: Redis Pipeline Operation
    // =================================================================================

    private void syncCharterToRedis(List<Property> properties) {
        if (properties == null || properties.isEmpty()) return;

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            RedisSerializer<String> stringSerializer = redisTemplate.getStringSerializer();
            @SuppressWarnings("unchecked")
            RedisSerializer<Object> valueSerializer = (RedisSerializer<Object>) redisTemplate.getValueSerializer();

            for (Property p : properties) {
                String id = p.getPropertyId();
                String district = p.getDistrictName().replace("서울시 ", "").trim();

                // 1. 매물 상세 정보 저장 (Hash) -> property:charter:{id}
                byte[] key = stringSerializer.serialize("property:charter:" + id);
                Map<byte[], byte[]> hash = new HashMap<>();

                hash.put(stringSerializer.serialize("propertyId"), valueSerializer.serialize(p.getPropertyId()));
                hash.put(stringSerializer.serialize("aptNm"), valueSerializer.serialize(p.getAptNm()));
                hash.put(stringSerializer.serialize("deposit"), valueSerializer.serialize(String.valueOf(p.getDeposit())));
                hash.put(stringSerializer.serialize("areaInPyeong"), valueSerializer.serialize(String.valueOf(p.getAreaInPyeong())));
                hash.put(stringSerializer.serialize("floor"), valueSerializer.serialize(String.valueOf(p.getFloor())));
                hash.put(stringSerializer.serialize("buildYear"), valueSerializer.serialize(String.valueOf(p.getBuildYear())));
                hash.put(stringSerializer.serialize("address"), valueSerializer.serialize(p.getAddress()));
                hash.put(stringSerializer.serialize("leaseType"), valueSerializer.serialize("전세"));
                hash.put(stringSerializer.serialize("districtName"), valueSerializer.serialize(p.getDistrictName()));

                if (p.getSggCd() != null) hash.put(stringSerializer.serialize("sggCd"), valueSerializer.serialize(p.getSggCd()));
                if (p.getUmdNm() != null) hash.put(stringSerializer.serialize("umdNm"), valueSerializer.serialize(p.getUmdNm()));
                if (p.getJibun() != null) hash.put(stringSerializer.serialize("jibun"), valueSerializer.serialize(p.getJibun()));

                connection.hMSet(key, hash);

                // 2. 검색 인덱스 업데이트 (Sorted Set)
                byte[] priceIdxKey = stringSerializer.serialize("idx:charterPrice:" + district);
                connection.zAdd(priceIdxKey, p.getDeposit(), stringSerializer.serialize(id));

                byte[] areaIdxKey = stringSerializer.serialize("idx:area:" + district + ":전세");
                connection.zAdd(areaIdxKey, p.getAreaInPyeong(), stringSerializer.serialize(id));
            }
            return null;
        });
        log.info("Redis: 전세 데이터 및 인덱스 파이프라인 전송 완료");
    }

    private void syncMonthlyToRedis(List<Property> properties) {
        if (properties == null || properties.isEmpty()) return;

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            RedisSerializer<String> stringSerializer = redisTemplate.getStringSerializer();
            @SuppressWarnings("unchecked")
            RedisSerializer<Object> valueSerializer = (RedisSerializer<Object>) redisTemplate.getValueSerializer();

            for (Property p : properties) {
                String id = p.getPropertyId();
                String district = p.getDistrictName().replace("서울시 ", "").trim();

                // 1. 매물 상세 정보 저장 (Hash) -> property:monthly:{id}
                byte[] key = stringSerializer.serialize("property:monthly:" + id);
                Map<byte[], byte[]> hash = new HashMap<>();

                hash.put(stringSerializer.serialize("propertyId"), valueSerializer.serialize(p.getPropertyId()));
                hash.put(stringSerializer.serialize("aptNm"), valueSerializer.serialize(p.getAptNm()));
                hash.put(stringSerializer.serialize("deposit"), valueSerializer.serialize(String.valueOf(p.getDeposit())));
                hash.put(stringSerializer.serialize("monthlyRent"), valueSerializer.serialize(String.valueOf(p.getMonthlyRent())));
                hash.put(stringSerializer.serialize("areaInPyeong"), valueSerializer.serialize(String.valueOf(p.getAreaInPyeong())));
                hash.put(stringSerializer.serialize("floor"), valueSerializer.serialize(String.valueOf(p.getFloor())));
                hash.put(stringSerializer.serialize("buildYear"), valueSerializer.serialize(String.valueOf(p.getBuildYear())));
                hash.put(stringSerializer.serialize("address"), valueSerializer.serialize(p.getAddress()));
                hash.put(stringSerializer.serialize("leaseType"), valueSerializer.serialize("월세"));
                hash.put(stringSerializer.serialize("districtName"), valueSerializer.serialize(p.getDistrictName()));

                if (p.getSggCd() != null) hash.put(stringSerializer.serialize("sggCd"), valueSerializer.serialize(p.getSggCd()));
                if (p.getUmdNm() != null) hash.put(stringSerializer.serialize("umdNm"), valueSerializer.serialize(p.getUmdNm()));
                if (p.getJibun() != null) hash.put(stringSerializer.serialize("jibun"), valueSerializer.serialize(p.getJibun()));

                connection.hMSet(key, hash);

                // 2. 검색 인덱스 업데이트 (Sorted Set)
                byte[] depositIdxKey = stringSerializer.serialize("idx:deposit:" + district);
                connection.zAdd(depositIdxKey, p.getDeposit(), stringSerializer.serialize(id));

                byte[] rentIdxKey = stringSerializer.serialize("idx:monthlyRent:" + district + ":월세");
                connection.zAdd(rentIdxKey, p.getMonthlyRent(), stringSerializer.serialize(id));

                byte[] areaIdxKey = stringSerializer.serialize("idx:area:" + district + ":월세");
                connection.zAdd(areaIdxKey, p.getAreaInPyeong(), stringSerializer.serialize(id));
            }
            return null;
        });
        log.info("Redis: 월세 데이터 및 인덱스 파이프라인 전송 완료");
    }

    // =================================================================================
    // [추가된 로직] 정규화 범위 계산 및 저장 (Legacy Code 차용)
    // =================================================================================

    private void calculateAndStoreNormalizationBounds(List<Property> properties) {
        log.info("=== 정규화 범위 계산 및 저장 시작 ===");

        // 1. 데이터 그룹핑: 지역구명 + 임대유형
        Map<String, List<Property>> groupedProperties = groupPropertiesByDistrictAndLeaseType(properties);
        String currentTime = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        for (Map.Entry<String, List<Property>> entry : groupedProperties.entrySet()) {
            String groupKey = entry.getKey();
            List<Property> groupProperties = entry.getValue();

            try {
                if (groupProperties.size() < 1) continue;

                // 2. 정규화 범위 계산
                NormalizationBounds bounds = calculateBoundsForGroupFixed(groupProperties, groupKey);

                if (bounds == null) continue;

                // 3. Redis 저장 (로그 추가)
                storeBoundsToRedisFixed(groupKey, bounds, groupProperties, currentTime);

            } catch (Exception e) {
                log.error("그룹 [{}] 정규화 처리 중 오류 발생", groupKey, e);
            }
        }
        log.info("=== 정규화 범위 계산 완료 ===");
    }

    private Map<String, List<Property>> groupPropertiesByDistrictAndLeaseType(List<Property> properties) {
        Map<String, List<Property>> groupedProperties = new HashMap<>();
        for (Property property : properties) {
            if (property.getDistrictName() == null || property.getLeaseType() == null) continue;
            if (property.getDeposit() == null || property.getAreaInPyeong() == null) continue;
            if (property.getAreaInPyeong() <= 0) continue;

            String groupKey = property.getDistrictName() + ":" + property.getLeaseType();
            groupedProperties.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(property);
        }
        return groupedProperties;
    }

    private NormalizationBounds calculateBoundsForGroupFixed(List<Property> groupProperties, String groupKey) {
        String[] keyParts = groupKey.split(":");
        String leaseType = keyParts[1];
        List<Double> prices = new ArrayList<>();
        List<Double> areas = new ArrayList<>();

        for (Property property : groupProperties) {
            if ("전세".equals(leaseType) && property.getDeposit() > 0) {
                prices.add(property.getDeposit().doubleValue());
            } else if ("월세".equals(leaseType) && property.getDeposit() > 0) {
                prices.add(property.getDeposit().doubleValue());
            }

            if (property.getAreaInPyeong() > 0) {
                areas.add(property.getAreaInPyeong());
            }
        }

        if (prices.isEmpty() || areas.isEmpty()) return null;

        double minPrice = Collections.min(prices);
        double maxPrice = Collections.max(prices);
        double minArea = Collections.min(areas);
        double maxArea = Collections.max(areas);

        if (minPrice == maxPrice) maxPrice = minPrice + 1000.0;
        if (minArea == maxArea) maxArea = minArea + 1.0;

        return new NormalizationBounds(minPrice, maxPrice, minArea, maxArea);
    }

    private void storeBoundsToRedisFixed(String groupKey, NormalizationBounds bounds,
                                         List<Property> groupProperties, String currentTime) {
        String[] keyParts = groupKey.split(":");
        String districtName = keyParts[0];
        String leaseType = keyParts[1];
        int propertyCount = groupProperties.size();

        if ("전세".equals(leaseType)) {
            String redisKey = "bounds:" + districtName + ":전세";
            Map<String, Object> boundsHash = new HashMap<>();
            boundsHash.put("minPrice", String.valueOf(bounds.minPrice));
            boundsHash.put("maxPrice", String.valueOf(bounds.maxPrice));
            boundsHash.put("minArea", String.valueOf(bounds.minArea));
            boundsHash.put("maxArea", String.valueOf(bounds.maxArea));
            boundsHash.put("propertyCount", String.valueOf(propertyCount));
            boundsHash.put("lastUpdated", currentTime);
            redisHandler.redisTemplate.opsForHash().putAll(redisKey, boundsHash);

            // [추가된 로그] 전세 정규화 범위 저장 확인
            log.info("Redis [Bounds] 저장 완료 - Key: {}, Count: {}, Price[{}-{}], Area[{}-{}]",
                    redisKey, propertyCount, bounds.minPrice, bounds.maxPrice, bounds.minArea, bounds.maxArea);

        } else if ("월세".equals(leaseType)) {
            List<Double> monthlyRents = new ArrayList<>();
            for (Property property : groupProperties) {
                if (property.getMonthlyRent() != null && property.getMonthlyRent() > 0) {
                    monthlyRents.add(property.getMonthlyRent().doubleValue());
                }
            }
            double minMonthlyRent = monthlyRents.isEmpty() ? 0.0 : Collections.min(monthlyRents);
            double maxMonthlyRent = monthlyRents.isEmpty() ? 500.0 : Collections.max(monthlyRents);
            if (minMonthlyRent == maxMonthlyRent) maxMonthlyRent = minMonthlyRent + 10.0;

            String redisKey = "bounds:" + districtName + ":월세";
            Map<String, Object> boundsHash = new HashMap<>();
            boundsHash.put("minDeposit", String.valueOf(bounds.minPrice));
            boundsHash.put("maxDeposit", String.valueOf(bounds.maxPrice));
            boundsHash.put("minMonthlyRent", String.valueOf(minMonthlyRent));
            boundsHash.put("maxMonthlyRent", String.valueOf(maxMonthlyRent));
            boundsHash.put("minArea", String.valueOf(bounds.minArea));
            boundsHash.put("maxArea", String.valueOf(bounds.maxArea));
            boundsHash.put("propertyCount", String.valueOf(propertyCount));
            boundsHash.put("lastUpdated", currentTime);
            redisHandler.redisTemplate.opsForHash().putAll(redisKey, boundsHash);

            // [추가된 로그] 월세 정규화 범위 저장 확인
            log.info("Redis [Bounds] 저장 완료 - Key: {}, Count: {}, Deposit[{}-{}], Rent[{}-{}], Area[{}-{}]",
                    redisKey, propertyCount, bounds.minPrice, bounds.maxPrice, minMonthlyRent, maxMonthlyRent, bounds.minArea, bounds.maxArea);
        }
    }

    private static class NormalizationBounds {
        final double minPrice;
        final double maxPrice;
        final double minArea;
        final double maxArea;

        NormalizationBounds(double minPrice, double maxPrice, double minArea, double maxArea) {
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
            this.minArea = minArea;
            this.maxArea = maxArea;
        }
    }

    // =================================================================================
    // [추가된 로직] 안전성 점수 계산 및 저장 (Legacy Code 차용)
    // =================================================================================

    private void calculateAndStoreSafetyScores() {
        log.info("=== 안전성 점수 계산 및 저장 시작 ===");

        try {
            // 1. 범죄 데이터 수집
            List<DistrictCrimeCountDto> crimeData = crimeRepository.findCrimeCount();
            Map<String, Long> crimeCountMap = new HashMap<>();
            for (DistrictCrimeCountDto crime : crimeData) {
                crimeCountMap.put(crime.getDistrictName(), crime.getTotalOccurrence());
            }

            // 2. 인구 데이터 수집
            List<Object[]> populationCountData = populationRepository.findPopulationCountByDistrict();
            Map<String, Long> populationCountMap = new HashMap<>();
            for (Object[] row : populationCountData) {
                populationCountMap.put((String) row[0], ((Number) row[1]).longValue());
            }

            // 3. 유흥업소 데이터 수집
            List<Object[]> entertainmentData = entertainmentRepository.findActiveEntertainmentCountByDistrict();
            Map<String, Double> entertainmentCountMap = new HashMap<>();
            for (Object[] row : entertainmentData) {
                entertainmentCountMap.put((String) row[0], ((Number) row[1]).doubleValue());
            }

            // 4. 지역구별 범죄율 계산
            Map<String, Double> crimeRateMap = new HashMap<>();
            for (String districtName : SEOUL_DISTRICT_CODES.values()) {
                Long crimeCount = crimeCountMap.getOrDefault(districtName, 0L);
                Long population = populationCountMap.getOrDefault(districtName, 1L);
                if (population > 0) {
                    double crimeRate = (crimeCount.doubleValue() / population.doubleValue()) * 100000.0;
                    crimeRateMap.put(districtName, crimeRate);
                }
            }

            // 5. 정규화를 위한 최대/최소값 계산
            double maxEntertainment = entertainmentCountMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
            double minEntertainment = entertainmentCountMap.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            double maxPopulation = populationCountMap.values().stream().mapToDouble(v -> v.doubleValue()).max().orElse(1.0);
            double minPopulation = populationCountMap.values().stream().mapToDouble(v -> v.doubleValue()).min().orElse(0.0);

            // 6. 점수 계산
            Map<String, Double> rawSafetyScoreMap = new HashMap<>();
            Map<String, Double> safetyScoreMap = new HashMap<>();

            for (String districtName : SEOUL_DISTRICT_CODES.values()) {
                if (!crimeRateMap.containsKey(districtName)) continue;

                Double entertainmentCount = entertainmentCountMap.getOrDefault(districtName, 0.0);
                Long populationCount = populationCountMap.getOrDefault(districtName, 0L);

                double normalizedEntertainment = 0.0;
                if (maxEntertainment > minEntertainment) {
                    normalizedEntertainment = (entertainmentCount - minEntertainment) / (maxEntertainment - minEntertainment);
                }

                double normalizedPopulation = 0.0;
                if (maxPopulation > minPopulation) {
                    normalizedPopulation = (populationCount.doubleValue() - minPopulation) / (maxPopulation - minPopulation);
                }

                double crimeRiskScore = (1.0229 * normalizedEntertainment) - (0.0034 * normalizedPopulation);
                double rawSafetyScore = 100 - (crimeRiskScore * 10);
                rawSafetyScoreMap.put(districtName, rawSafetyScore);
            }

            double maxRawScore = rawSafetyScoreMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(100.0);
            double minRawScore = rawSafetyScoreMap.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);

            for (Map.Entry<String, Double> entry : rawSafetyScoreMap.entrySet()) {
                double normalizedScore = 0.0;
                if (maxRawScore > minRawScore) {
                    normalizedScore = ((entry.getValue() - minRawScore) / (maxRawScore - minRawScore)) * 100.0;
                }
                safetyScoreMap.put(entry.getKey(), normalizedScore);
            }

            // 7. Redis 저장
            storeSafetyScoresToRedis(safetyScoreMap);

            log.info("=== 안전성 점수 계산 완료: {}개 지역구 ===", safetyScoreMap.size());

        } catch (Exception e) {
            log.error("안전성 점수 계산 중 오류 발생", e);
        }
    }

    private void storeSafetyScoresToRedis(Map<String, Double> safetyScoreMap) {
        String currentTime = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        for (Map.Entry<String, Double> entry : safetyScoreMap.entrySet()) {
            try {
                String redisKey = "safety:" + entry.getKey();
                Map<String, Object> safetyHash = new HashMap<>();
                safetyHash.put("districtName", entry.getKey());
                safetyHash.put("safetyScore", String.valueOf(entry.getValue()));
                safetyHash.put("lastUpdated", currentTime);
                safetyHash.put("version", "1.0");
                redisHandler.redisTemplate.opsForHash().putAll(redisKey, safetyHash);

                // [수정] {:.2f} 제거 -> {} 로 변경하여 실제 값 출력
                log.info("Redis [Safety] 저장 완료 - Key: {}, Score: {}", redisKey, entry.getValue());

            } catch (Exception e) {
                log.error("Redis 저장 실패: {}", entry.getKey(), e);
            }
        }
    }
}