package com.wherehouse.recommand.batch.BatchScheduler;

import com.wherehouse.recommand.batch.dto.Property;
import com.wherehouse.recommand.batch.entity.PropertyCharter;
import com.wherehouse.recommand.batch.entity.PropertyMonthly;
import com.wherehouse.recommand.batch.event.DataCollectionCompletedEvent;
import com.wherehouse.recommand.batch.repository.PropertyCharterRepository;
import com.wherehouse.recommand.batch.repository.PropertyMonthlyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 배치 데이터 동기화 리스너 (Processor Phase)
 *
 * 역할:
 * 1. DataCollectionCompletedEvent 수신 (비동기 처리)
 * 2. Oracle RDB에 매물 데이터 적재 (Source of Truth 확보)
 * 3. Redis에 매물 정보 및 검색 인덱스 적재 (Legacy 배치의 역할 대체)
 *
 * [성능 측정 전략 - BottleNeck Testing]
 * 현재 코드는 리뷰 시스템 도입 전의 '베이스라인(Baseline)' 성능을 측정하기 위한 버전입니다.
 * 따라서 리뷰 통계 초기화, 집계, 동기화 로직은 주석 처리되어 있습니다.
 * 추후 Phase 3 단계에서 주석을 해제하여 부하를 비교할 예정입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RdbSyncListener {

    private final PropertyCharterRepository propertyCharterRepository;
    private final PropertyMonthlyRepository propertyMonthlyRepository;

    // 대량의 Redis 연산(Pipeline)을 위해 RedisTemplate을 직접 사용합니다.
    private final RedisTemplate<String, Object> redisTemplate;

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
        // Step 1. [RDB] 매물 원본 데이터 적재 (Upsert)
        // =================================================================================
        saveCharterPropertiesToRdb(event.getCharterProperties());
        saveMonthlyPropertiesToRdb(event.getMonthlyProperties());

        /*
         * =================================================================================
         * [TODO: Phase 3 - 리뷰 시스템 데이터 정합성 확보 로직]
         * 리뷰 시스템이 활성화되면 아래 로직의 주석을 해제해야 합니다.
         *
         * 1. 신규 매물 식별 및 리뷰 통계 테이블(REVIEW_STATISTICS) 초기화 (INSERT 0)
         * 2. 리뷰 테이블(REVIEWS) 기반 전수 집계 수행 (COUNT, AVG)
         * 3. 집계된 데이터를 통계 테이블에 반영 (Reconciliation)
         *
         * // reviewStatisticsService.initAndReconcileStatistics(event);
         * =================================================================================
         */

        log.info(">>> [Phase 2-1] RDB 적재 완료. Redis 동기화(Pipeline) 시작.");

        // =================================================================================
        // Step 2. [Redis] 매물 정보 및 검색 인덱스 동기화
        // =================================================================================
        syncCharterToRedis(event.getCharterProperties());
        syncMonthlyToRedis(event.getMonthlyProperties());

        /*
         * =================================================================================
         * [TODO: Phase 3 - 리뷰 통계 캐시 동기화]
         * RDB에 반영된 최신 리뷰 통계(평점, 리뷰 수)를 Redis(stats:*:*)에 동기화합니다.
         *
         * // reviewStatisticsService.syncStatisticsToRedis();
         * =================================================================================
         */

        long endTime = System.currentTimeMillis();
        log.info(">>> [Phase 2] 배치 동기화 프로세스 정상 종료. 총 소요시간: {}ms", (endTime - startTime));
    }

    // =================================================================================
    // Internal Methods: RDB Operation
    // =================================================================================

    private void saveCharterPropertiesToRdb(List<Property> properties) {
        if (properties == null || properties.isEmpty()) return;

        // DTO -> Entity 변환
        List<PropertyCharter> entities = properties.stream()
                .map(PropertyCharter::from)
                .collect(Collectors.toList());

        // Bulk Save (Upsert)
        propertyCharterRepository.saveAll(entities);
        log.info("RDB: 전세 매물 {}건 저장 완료", entities.size());
    }

    private void saveMonthlyPropertiesToRdb(List<Property> properties) {
        if (properties == null || properties.isEmpty()) return;

        // DTO -> Entity 변환
        List<PropertyMonthly> entities = properties.stream()
                .map(PropertyMonthly::from)
                .collect(Collectors.toList());

        // Bulk Save (Upsert)
        propertyMonthlyRepository.saveAll(entities);
        log.info("RDB: 월세 매물 {}건 저장 완료", entities.size());
    }

    // =================================================================================
    // Internal Methods: Redis Pipeline Operation
    // =================================================================================

    /**
     * 전세 매물 Redis 동기화
     * 1. 매물 상세 정보 (Hash)
     * 2. 검색 인덱스 (Sorted Set: 가격, 평수)
     */
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

                // 필요한 추가 필드 매핑
                if (p.getSggCd() != null) hash.put(stringSerializer.serialize("sggCd"), valueSerializer.serialize(p.getSggCd()));
                if (p.getUmdNm() != null) hash.put(stringSerializer.serialize("umdNm"), valueSerializer.serialize(p.getUmdNm()));
                if (p.getJibun() != null) hash.put(stringSerializer.serialize("jibun"), valueSerializer.serialize(p.getJibun()));

                connection.hMSet(key, hash);

                // 2. 검색 인덱스 업데이트 (Sorted Set)
                // 2-1. 전세금 인덱스 (idx:charterPrice:{구})
                byte[] priceIdxKey = stringSerializer.serialize("idx:charterPrice:" + district);
                connection.zAdd(priceIdxKey, p.getDeposit(), stringSerializer.serialize(id));

                // 2-2. 평수 인덱스 (idx:area:{구}:전세)
                byte[] areaIdxKey = stringSerializer.serialize("idx:area:" + district + ":전세");
                connection.zAdd(areaIdxKey, p.getAreaInPyeong(), stringSerializer.serialize(id));
            }
            return null;
        });
        log.info("Redis: 전세 데이터 및 인덱스 파이프라인 전송 완료");
    }

    /**
     * 월세 매물 Redis 동기화
     * 1. 매물 상세 정보 (Hash)
     * 2. 검색 인덱스 (Sorted Set: 보증금, 월세금, 평수)
     */
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
                // 2-1. 보증금 인덱스 (idx:deposit:{구})
                byte[] depositIdxKey = stringSerializer.serialize("idx:deposit:" + district);
                connection.zAdd(depositIdxKey, p.getDeposit(), stringSerializer.serialize(id));

                // 2-2. 월세금 인덱스 (idx:monthlyRent:{구}:월세)
                byte[] rentIdxKey = stringSerializer.serialize("idx:monthlyRent:" + district + ":월세");
                connection.zAdd(rentIdxKey, p.getMonthlyRent(), stringSerializer.serialize(id));

                // 2-3. 평수 인덱스 (idx:area:{구}:월세)
                byte[] areaIdxKey = stringSerializer.serialize("idx:area:" + district + ":월세");
                connection.zAdd(areaIdxKey, p.getAreaInPyeong(), stringSerializer.serialize(id));
            }
            return null;
        });
        log.info("Redis: 월세 데이터 및 인덱스 파이프라인 전송 완료");
    }
}