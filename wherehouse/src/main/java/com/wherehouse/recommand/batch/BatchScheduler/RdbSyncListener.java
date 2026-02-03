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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;

// [2차 테스트] Slice 페이징을 위한 import 추가
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

// [2차 테스트] 영속성 컨텍스트 초기화를 위한 import 추가
import jakarta.persistence.EntityManager;


/**
 * 배치 데이터 동기화 리스너 (Processor Phase)
 *
 * 역할:
 * 1. DataCollectionCompletedEvent 수신
 * 2. Oracle RDB에 매물 데이터 적재 (Source of Truth 확보)
 * 3. RDB에서 저장된 데이터 재조회 후 Redis에 동기화 (데이터 일관성 보장)
 * 4. 정규화 범위(Bounds) 계산 및 Redis 적재
 * 5. 안전성 점수(Safety Score) 계산 및 Redis 적재
 *
 * [설계 변경 - 2025-12-09]
 * - Redis 저장 시 메모리 기반 → RDB 기반으로 변경
 * - 이유: RDB 저장 중 실패/중복 제거된 건이 Redis에 반영되지 않는 불일치 해소
 * - RDB가 Single Source of Truth로서 역할 수행
 *
 * [2차 테스트 변경사항 - 2026-01-30]
 * - findAll() → Slice 기반 청크 처리로 변경
 * - 목적: OOM 병목 해소 (힙 피크 사용량 제한)
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

    // RedisHandler만 사용 (백업본과 동일)
    private final RedisHandler redisHandler;

    // [2차 테스트] 영속성 컨텍스트 초기화용 EntityManager
    private final EntityManager entityManager;

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

    // =================================================================================
    // [2차 테스트] 청크 크기 설정
    // =================================================================================
    /**
     * Slice 페이징 청크 크기
     *
     * 설계 근거:
     * - 1차 테스트 결과: 건당 Retained Size 약 863 Bytes
     * - 10,000건 기준 예상 메모리: 약 8.6MB
     * - 256MB 힙 기준 안전 마진 확보 (피크 시 ~3.4% 점유)
     *
     * 조정 가이드:
     * - 힙 여유 시: 20,000건으로 증가 (쿼리 횟수 감소)
     * - 힙 부족 시: 5,000건으로 감소 (메모리 피크 완화)
     */
    private static final int CHUNK_SIZE = 10000;

    /**
     * 데이터 수집 완료 이벤트 핸들러
     */
    @Scheduled(fixedDelay = Long.MAX_VALUE, initialDelay = 4000)
    /* 이 부분 실제로 사용하는 코드이나 현재 테스트 환경이므로 임시로 주석 */
//    @EventListener
//    @Transactional
    public void handleDataCollectionCompletedEvent() {  // DataCollectionCompletedEvent event
//        log.info(">>> [Phase 2] RdbSyncListener 이벤트 수신. 데이터 적재 프로세스 시작. (전세: {}건, 월세: {}건)",
//                event.getCharterCount(), event.getMonthlyCount());

        // =================================================================================
        // [2차 테스트] 성능 측정 로깅 - Slice 청크 처리 버전
        // =================================================================================
        String threadName = Thread.currentThread().getName();
        long batchStartTs = System.currentTimeMillis();
        log.info("[PERF:BATCH:TOTAL] thread={} | phase=START | ts={}", threadName, batchStartTs);

        long startTime = System.currentTimeMillis();

        /* ** 이 위치 실제 사용하는 코드이나 임시 주석, 이유는 현재 테스트 환경에서 매번 저장하는 로직 발생 시 매우 시간 오래 걸림. */
        // Step 1. [RDB] 매물 원본 데이터 적재 (1차 배치 프로세스로부터 전달 받은 데이터를 저장)
//        saveCharterPropertiesToRdb(event.getCharterProperties());
//        saveMonthlyPropertiesToRdb(event.getMonthlyProperties());

        log.info(">>> [Phase 2-1] RDB 적재 완료. RDB 기준 데이터 재조회 시작.");

        // =================================================================================
        // [2차 테스트] Step 2 + Step 3 통합: Slice 기반 청크 로드 + 즉시 DTO 변환
        // =================================================================================
        // 설계 원리:
        // - Entity 청크를 로드하자마자 DTO로 변환하여 charterProperties/monthlyProperties에 누적
        // - Entity 청크 참조는 다음 반복에서 덮어쓰여 GC 대상이 됨
        // - 힙 피크: 청크 크기(10,000건) 수준으로 제한
        // =================================================================================

        // === [2차 테스트] 전세 데이터: Slice 청크 로드 + DTO 변환 ===
        long charterStartTs = System.currentTimeMillis();
        log.info("[PERF:DBLOAD:CHARTER] thread={} | phase=START | ts={} | chunkSize={}",
                threadName, charterStartTs, CHUNK_SIZE);

        List<Property> charterProperties = new ArrayList<>();
        Pageable charterPageable = PageRequest.of(0, CHUNK_SIZE, Sort.by("propertyId"));
        Slice<PropertyCharter> charterSlice;
        int charterChunkIndex = 0;
        int charterTotalCount = 0;
        long charterTransformTotalMs = 0;  // [2차 테스트] 변환 시간 누적

        do {
            long chunkStartTs = System.currentTimeMillis();

            charterSlice = propertyCharterRepository.findAllBy(charterPageable);
            List<PropertyCharter> chunkEntities = charterSlice.getContent();

            long chunkLoadEndTs = System.currentTimeMillis();
            long chunkLoadMs = chunkLoadEndTs - chunkStartTs;

            // ▶▶▶ 브레이크포인트 #1: 첫 번째 청크 로드 직후 (charterChunkIndex == 0일 때)
            //     이 시점에서 VisualVM 힙 덤프 → 청크 크기(10,000건)만 적재 상태 확인

            // 즉시 DTO 변환 → charterProperties에 누적
            long transformStartTs = System.currentTimeMillis();
            for (PropertyCharter entity : chunkEntities) {
                charterProperties.add(convertCharterEntityToProperty(entity));
            }
            long transformEndTs = System.currentTimeMillis();
            long chunkTransformMs = transformEndTs - transformStartTs;
            charterTransformTotalMs += chunkTransformMs;

            charterTotalCount += chunkEntities.size();
            long chunkEndTs = System.currentTimeMillis();

            log.info("[PERF:CHUNK:CHARTER] thread={} | phase=COMPLETE | chunkIndex={} | chunkSize={} | cumulative={} | load_ms={} | transform_ms={} | total_ms={} | hasNext={}",
                    threadName, charterChunkIndex, chunkEntities.size(), charterTotalCount,
                    chunkLoadMs, chunkTransformMs, (chunkEndTs - chunkStartTs), charterSlice.hasNext());

            // [2차 테스트] 영속성 컨텍스트 초기화 → Entity 참조 해제 → GC 가능
            // 이 호출이 없으면 1차 캐시에 모든 청크의 Entity가 누적되어 findAll()과 동일한 메모리 사용
            entityManager.clear();

            charterPageable = charterSlice.nextPageable();
            charterChunkIndex++;

        } while (charterSlice.hasNext());

        long charterEndTs = System.currentTimeMillis();
        log.info("[PERF:DBLOAD:CHARTER] thread={} | phase=END | ts={} | totalCount={} | totalChunks={} | elapsed_ms={}",
                threadName, charterEndTs, charterTotalCount, charterChunkIndex, (charterEndTs - charterStartTs));

        // === [2차 테스트] 월세 데이터: Slice 청크 로드 + DTO 변환 ===
        long monthlyStartTs = System.currentTimeMillis();
        log.info("[PERF:DBLOAD:MONTHLY] thread={} | phase=START | ts={} | chunkSize={}",
                threadName, monthlyStartTs, CHUNK_SIZE);

        List<Property> monthlyProperties = new ArrayList<>();
        Pageable monthlyPageable = PageRequest.of(0, CHUNK_SIZE, Sort.by("propertyId"));
        Slice<PropertyMonthly> monthlySlice;
        int monthlyChunkIndex = 0;
        int monthlyTotalCount = 0;
        long monthlyTransformTotalMs = 0;  // [2차 테스트] 변환 시간 누적

        do {
            long chunkStartTs = System.currentTimeMillis();

            monthlySlice = propertyMonthlyRepository.findAllBy(monthlyPageable);
            List<PropertyMonthly> chunkEntities = monthlySlice.getContent();

            long chunkLoadEndTs = System.currentTimeMillis();
            long chunkLoadMs = chunkLoadEndTs - chunkStartTs;

            // ▶▶▶ 브레이크포인트 #2: 첫 번째 청크 로드 직후 (monthlyChunkIndex == 0일 때)
            //     이 시점에서 VisualVM 힙 덤프 → 전세 DTO 전체 + 월세 청크(10,000건) 적재 상태 확인

            // 즉시 DTO 변환 → monthlyProperties에 누적
            long transformStartTs = System.currentTimeMillis();
            for (PropertyMonthly entity : chunkEntities) {
                monthlyProperties.add(convertMonthlyEntityToProperty(entity));
            }
            long transformEndTs = System.currentTimeMillis();
            long chunkTransformMs = transformEndTs - transformStartTs;
            monthlyTransformTotalMs += chunkTransformMs;

            monthlyTotalCount += chunkEntities.size();
            long chunkEndTs = System.currentTimeMillis();

            log.info("[PERF:CHUNK:MONTHLY] thread={} | phase=COMPLETE | chunkIndex={} | chunkSize={} | cumulative={} | load_ms={} | transform_ms={} | total_ms={} | hasNext={}",
                    threadName, monthlyChunkIndex, chunkEntities.size(), monthlyTotalCount,
                    chunkLoadMs, chunkTransformMs, (chunkEndTs - chunkStartTs), monthlySlice.hasNext());

            // [2차 테스트] 영속성 컨텍스트 초기화 → Entity 참조 해제 → GC 가능
            entityManager.clear();

            monthlyPageable = monthlySlice.nextPageable();
            monthlyChunkIndex++;

        } while (monthlySlice.hasNext());

        long monthlyEndTs = System.currentTimeMillis();
        log.info("[PERF:DBLOAD:MONTHLY] thread={} | phase=END | ts={} | totalCount={} | totalChunks={} | elapsed_ms={}",
                threadName, monthlyEndTs, monthlyTotalCount, monthlyChunkIndex, (monthlyEndTs - monthlyStartTs));

        log.info(">>> [Phase 2-2] RDB 재조회 완료. (전세: {}건, 월세: {}건)",
                charterProperties.size(), monthlyProperties.size());

        // [2차 테스트] Step 3 Entity→DTO 변환은 위 루프에서 통합 처리됨
        log.info(">>> [Phase 2-3] Entity → Property 변환 완료. Redis 동기화 시작.");

        // Step 4. [Redis] 매물 정보 및 검색 인덱스 동기화 (RDB 기준)
        syncCharterToRedis(charterProperties);
        syncMonthlyToRedis(monthlyProperties);

        // Step 5. [Redis] 정규화 범위(Bounds) 계산 및 적재
        List<Property> allProperties = new ArrayList<>();
        allProperties.addAll(charterProperties);
        allProperties.addAll(monthlyProperties);

        calculateAndStoreNormalizationBounds(allProperties);

        // Step 6. [Redis] 안전성 점수(Safety Score) 계산 및 적재
        calculateAndStoreSafetyScores();

        long endTime = System.currentTimeMillis();
        log.info(">>> [Phase 2] 배치 동기화 프로세스 정상 종료. 총 소요시간: {}ms", (endTime - startTime));

        // =================================================================================
        // [2차 테스트] 배치 종료 로깅 및 측정 결과 요약
        // =================================================================================
        long batchEndTs = System.currentTimeMillis();
        log.info("[PERF:BATCH:TOTAL] thread={} | phase=END | ts={} | elapsed_ms={}",
                threadName, batchEndTs, (batchEndTs - batchStartTs));

        log.info("====================================================================");
        log.info(">>> [2차 테스트] 측정 결과 요약 (Slice 청크 처리) <<<");
        log.info("  CHUNK_SIZE          = {} 건/청크", CHUNK_SIZE);
        log.info("  DBLOAD:CHARTER      = {} ms ({}건, {}청크)", (charterEndTs - charterStartTs), charterTotalCount, charterChunkIndex);
        log.info("  DBLOAD:MONTHLY      = {} ms ({}건, {}청크)", (monthlyEndTs - monthlyStartTs), monthlyTotalCount, monthlyChunkIndex);
        log.info("  TRANSFORM:CHARTER   = {} ms (누적)", charterTransformTotalMs);
        log.info("  TRANSFORM:MONTHLY   = {} ms (누적)", monthlyTransformTotalMs);
        log.info("  BATCH:TOTAL         = {} ms", (batchEndTs - batchStartTs));
        log.info("====================================================================");
    }

    // =================================================================================
    // Entity → Property 변환 메서드
    // =================================================================================

    /**
     * PropertyCharter Entity → Property DTO 변환
     */
    private Property convertCharterEntityToProperty(PropertyCharter entity) {
        return Property.builder()
                .propertyId(entity.getPropertyId())
                .aptNm(entity.getAptNm())
                .excluUseAr(entity.getExcluUseAr())
                .floor(entity.getFloor())
                .buildYear(entity.getBuildYear())
                .dealDate(entity.getDealDate())
                .deposit(entity.getDeposit())
                .monthlyRent(null)  // 전세는 월세금 없음
                .leaseType("전세")
                .umdNm(entity.getUmdNm())
                .jibun(entity.getJibun())
                .sggCd(entity.getSggCd())
                .address(entity.getAddress())
                .areaInPyeong(entity.getAreaInPyeong())
                .rgstDate(entity.getRgstDate())
                .districtName(entity.getDistrictName())
                .build();
    }

    /**
     * PropertyMonthly Entity → Property DTO 변환
     */
    private Property convertMonthlyEntityToProperty(PropertyMonthly entity) {
        return Property.builder()
                .propertyId(entity.getPropertyId())
                .aptNm(entity.getAptNm())
                .excluUseAr(entity.getExcluUseAr())
                .floor(entity.getFloor())
                .buildYear(entity.getBuildYear())
                .dealDate(entity.getDealDate())
                .deposit(entity.getDeposit())
                .monthlyRent(entity.getMonthlyRent())
                .leaseType("월세")
                .umdNm(entity.getUmdNm())
                .jibun(entity.getJibun())
                .sggCd(entity.getSggCd())
                .address(entity.getAddress())
                .areaInPyeong(entity.getAreaInPyeong())
                .rgstDate(entity.getRgstDate())
                .districtName(entity.getDistrictName())
                .build();
    }

    // =================================================================================
    // RDB Operation
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
    // Redis Operation (백업본과 동일한 구조)
    // =================================================================================

    private void syncCharterToRedis(List<Property> properties) {
        if (properties == null || properties.isEmpty()) return;

        final int BATCH_SIZE = 500;
        int totalSuccess = 0;

        for (int batchStart = 0; batchStart < properties.size(); batchStart += BATCH_SIZE) {

            final int start = batchStart;
            final int end = Math.min(batchStart + BATCH_SIZE, properties.size());
            List<Property> batch = properties.subList(start, end);

            // 익명 클래스 내부에서 batchStart를 참조하기 위해 final 변수 사용
            final int currentBatchStart = batchStart;

            try {
                redisHandler.redisTemplate.executePipelined(new SessionCallback<Object>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public Object execute(RedisOperations operations) throws DataAccessException {

                        for (int i = 0; i < batch.size(); i++) {
                            Property property = batch.get(i);
                            String propertyId = property.getPropertyId();
                            String districtName = property.getDistrictName();

                            if (propertyId == null || districtName == null) continue;

                            // -------------------------------------------------------
                            // [비즈니스 로직 유지] 데이터 준비
                            // -------------------------------------------------------
                            Map<String, Object> propertyHash = new HashMap<>();
                            propertyHash.put("propertyId", propertyId != null ? propertyId : "");
                            propertyHash.put("aptNm", property.getAptNm() != null ? property.getAptNm() : "");
                            propertyHash.put("excluUseAr", property.getExcluUseAr() != null ? property.getExcluUseAr().toString() : "0.0");
                            propertyHash.put("floor", property.getFloor() != null ? property.getFloor().toString() : "0");
                            propertyHash.put("buildYear", property.getBuildYear() != null ? property.getBuildYear().toString() : "0");
                            propertyHash.put("dealDate", property.getDealDate() != null ? property.getDealDate() : "");
                            propertyHash.put("leaseType", "전세");
                            propertyHash.put("umdNm", property.getUmdNm() != null ? property.getUmdNm() : "");
                            propertyHash.put("jibun", property.getJibun() != null ? property.getJibun() : "");
                            propertyHash.put("sggCd", property.getSggCd() != null ? property.getSggCd() : "");
                            propertyHash.put("address", property.getAddress() != null ? property.getAddress() : "");
                            propertyHash.put("areaInPyeong", property.getAreaInPyeong() != null ? property.getAreaInPyeong().toString() : "0.0");
                            propertyHash.put("rgstDate", property.getRgstDate() != null ? property.getRgstDate() : "");
                            propertyHash.put("districtName", districtName != null ? districtName : "");
                            propertyHash.put("deposit", property.getDeposit() != null ? property.getDeposit().toString() : "0");

                            // -------------------------------------------------------
                            // [비즈니스 로직 유지] 키 생성
                            // -------------------------------------------------------
                            String charterPropertyKey = "property:charter:" + propertyId;
                            String charterPriceIndexKey = "idx:charterPrice:" + districtName;
                            String charterAreaIndexKey = "idx:area:" + districtName + ":전세";

                            // -------------------------------------------------------
                            // [비즈니스 로직 유지] Redis 명령어 수행
                            // -------------------------------------------------------

                            // [저장소 1] 매물 원본 데이터 (Hash)
                            // 로그 추가: Hash 키와 적재되는 필드 개수 확인
//                            log.info("   [Hash] Key=[{}] -> putAll({} fields)", charterPropertyKey, propertyHash.size());
                            operations.opsForHash().putAll(charterPropertyKey, propertyHash);

                            // [저장소 2] 전세금 인덱스 (Sorted Set)
                            double charterPrice = property.getDeposit() != null ? property.getDeposit().doubleValue() : 0.0;

                            // 로그 추가: 인덱스 키, 멤버(ID), 점수(가격) 확인 -> 여기서 propertyId가 UUID인지 MD5인지 확실히 보입니다.
//                            log.info("   [ZSet:Price] Key=[{}], Member=[{}], Score=[{}]",
//                                    charterPriceIndexKey, propertyId, charterPrice);

                            operations.opsForZSet().add(charterPriceIndexKey, propertyId, charterPrice);

                            // [저장소 3] 평수 인덱스 (Sorted Set)
                            double areaScore = property.getAreaInPyeong() != null ? property.getAreaInPyeong() : 0.0;

                            operations.opsForZSet().add(charterAreaIndexKey, propertyId, areaScore);
                        }

                        return null;
                    }
                });

                totalSuccess += batch.size();

            } catch (Exception e) {
                log.error("전세 배치 저장 실패 (범위: {}-{}): {}", start, end, e.getMessage());
            }
        }

        log.info("Redis [Pipeline]: 전세 매물 저장 완료 - 성공: {}건", totalSuccess);
//        verifyRedisStorage(properties, "charter", Math.min(10, properties.size()));
    }

    private void syncMonthlyToRedis(List<Property> properties) {
        if (properties == null || properties.isEmpty()) return;

        final int BATCH_SIZE = 500;
        int totalSuccess = 0;

        for (int batchStart = 0; batchStart < properties.size(); batchStart += BATCH_SIZE) {

            final int start = batchStart;
            final int end = Math.min(batchStart + BATCH_SIZE, properties.size());
            List<Property> batch = properties.subList(start, end);

            // 익명 클래스 내부에서 batchStart를 참조하기 위해 final 변수 사용
            final int currentBatchStart = batchStart;

            try {
                redisHandler.redisTemplate.executePipelined(new SessionCallback<Object>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public Object execute(RedisOperations operations) throws DataAccessException {

                        for (int i = 0; i < batch.size(); i++) {
                            Property property = batch.get(i);
                            String propertyId = property.getPropertyId();
                            String districtName = property.getDistrictName();

                            if (propertyId == null || districtName == null) continue;

                            // -------------------------------------------------------
                            // [비즈니스 로직 유지] 데이터 준비
                            // -------------------------------------------------------
                            Map<String, Object> propertyHash = new HashMap<>();
                            propertyHash.put("propertyId", propertyId != null ? propertyId : "");
                            propertyHash.put("aptNm", property.getAptNm() != null ? property.getAptNm() : "");
                            propertyHash.put("excluUseAr", property.getExcluUseAr() != null ? property.getExcluUseAr().toString() : "0.0");
                            propertyHash.put("floor", property.getFloor() != null ? property.getFloor().toString() : "0");
                            propertyHash.put("buildYear", property.getBuildYear() != null ? property.getBuildYear().toString() : "0");
                            propertyHash.put("dealDate", property.getDealDate() != null ? property.getDealDate() : "");
                            propertyHash.put("leaseType", "월세");
                            propertyHash.put("umdNm", property.getUmdNm() != null ? property.getUmdNm() : "");
                            propertyHash.put("jibun", property.getJibun() != null ? property.getJibun() : "");
                            propertyHash.put("sggCd", property.getSggCd() != null ? property.getSggCd() : "");
                            propertyHash.put("address", property.getAddress() != null ? property.getAddress() : "");
                            propertyHash.put("areaInPyeong", property.getAreaInPyeong() != null ? property.getAreaInPyeong().toString() : "0.0");
                            propertyHash.put("rgstDate", property.getRgstDate() != null ? property.getRgstDate() : "");
                            propertyHash.put("districtName", districtName != null ? districtName : "");
                            propertyHash.put("deposit", property.getDeposit() != null ? property.getDeposit().toString() : "0");
                            propertyHash.put("monthlyRent", property.getMonthlyRent() != null ? property.getMonthlyRent().toString() : "0");

                            // -------------------------------------------------------
                            // [비즈니스 로직 유지] 키 생성
                            // -------------------------------------------------------
                            String monthlyPropertyKey = "property:monthly:" + propertyId;
                            String depositIndexKey = "idx:deposit:" + districtName;
                            String monthlyRentIndexKey = "idx:monthlyRent:" + districtName + ":월세";
                            String monthlyAreaIndexKey = "idx:area:" + districtName + ":월세";

                            // -------------------------------------------------------
                            // [비즈니스 로직 유지] Redis 명령어 수행
                            // -------------------------------------------------------

                            // [저장소 1] 매물 원본 데이터 (Hash)
                            operations.opsForHash().putAll(monthlyPropertyKey, propertyHash);

                            // [저장소 2] 보증금 인덱스 (Sorted Set)
                            double depositPrice = property.getDeposit() != null ? property.getDeposit().doubleValue() : 0.0;
                            operations.opsForZSet().add(depositIndexKey, propertyId, depositPrice);

                            // [저장소 3] 월세금 인덱스 (Sorted Set)
                            double monthlyRentPrice = property.getMonthlyRent() != null ? property.getMonthlyRent().doubleValue() : 0.0;
                            operations.opsForZSet().add(monthlyRentIndexKey, propertyId, monthlyRentPrice);

                            // [저장소 4] 평수 인덱스 (Sorted Set)
                            double areaScore = property.getAreaInPyeong() != null ? property.getAreaInPyeong() : 0.0;
                            operations.opsForZSet().add(monthlyAreaIndexKey, propertyId, areaScore);
                        }

                        return null;
                    }
                });

                totalSuccess += batch.size();

            } catch (Exception e) {
                log.error("월세 배치 저장 실패 (범위: {}-{}): {}", start, end, e.getMessage());
            }
        }

        log.info("Redis [Pipeline]: 월세 매물 저장 완료 - 성공: {}건", totalSuccess);
//        verifyRedisStorage(properties, "monthly", Math.min(10, properties.size()));
    }

    /**
     * Redis 저장 검증 - Hash 뿐만 아니라 Index Key까지 패턴 확인
     */
    private void verifyRedisStorage(List<Property> properties, String leaseType, int sampleSize) {
        log.info("=== Redis 저장 상세 검증 시작 ({}) - 샘플 {}건 ===", leaseType, sampleSize);

        for (int i = 0; i < sampleSize; i++) {
            Property p = properties.get(i);
            String propertyId = p.getPropertyId();
            String districtName = p.getDistrictName(); // DTO에 districtName 필드가 public이거나 getter 사용

            // 1. Hash Key 검증
            String redisKey = "property:" + leaseType + ":" + propertyId;
            Map<Object, Object> storedData = redisHandler.redisTemplate.opsForHash().entries(redisKey);


            // 2. Index Key 검증 (실제 데이터 존재 여부 확인은 생략하더라도 키 패턴 로그 출력)
            if ("charter".equals(leaseType)) {
                String priceIdx = "idx:charterPrice:" + districtName;
                String areaIdx = "idx:area:" + districtName + ":전세";

                // 실제 스코어 조회로 검증
                Double priceScore = redisHandler.redisTemplate.opsForZSet().score(priceIdx, propertyId);
                log.info("    -> [Index Check] {} : Score={}", priceIdx, priceScore);

                Double areaScore = redisHandler.redisTemplate.opsForZSet().score(areaIdx, propertyId);
                log.info("    -> [Index Check] {} : Score={}", areaIdx, areaScore);

            } else if ("monthly".equals(leaseType)) {
                String depositIdx = "idx:deposit:" + districtName;
                String rentIdx = "idx:monthlyRent:" + districtName + ":월세";
                String areaIdx = "idx:area:" + districtName + ":월세";

                Double depositScore = redisHandler.redisTemplate.opsForZSet().score(depositIdx, propertyId);
                log.info("    -> [Index Check] {} : Score={}", depositIdx, depositScore);

                Double rentScore = redisHandler.redisTemplate.opsForZSet().score(rentIdx, propertyId);
                log.info("    -> [Index Check] {} : Score={}", rentIdx, rentScore);

                Double areaScore = redisHandler.redisTemplate.opsForZSet().score(areaIdx, propertyId);
                log.info("    -> [Index Check] {} : Score={}", areaIdx, areaScore);
            }
        }

        log.info("=== Redis 저장 상세 검증 완료 ===");
    }

    // =================================================================================
    // 정규화 범위 계산 및 저장
    // =================================================================================

    private void calculateAndStoreNormalizationBounds(List<Property> properties) {
        log.info("=== 정규화 범위 계산 및 저장 시작 ===");

        Map<String, List<Property>> groupedProperties = groupPropertiesByDistrictAndLeaseType(properties);
        String currentTime = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        for (Map.Entry<String, List<Property>> entry : groupedProperties.entrySet()) {
            String groupKey = entry.getKey();
            List<Property> groupProperties = entry.getValue();

            try {
                if (groupProperties.size() < 1) continue;

                NormalizationBounds bounds = calculateBoundsForGroupFixed(groupProperties, groupKey);
                if (bounds == null) continue;

                storeBoundsToRedisFixed(groupKey, bounds, groupProperties, currentTime);

            } catch (Exception e) {
                log.error("그룹 [{}] 정규화 범위 저장 실패", groupKey, e);
            }
        }

        log.info("=== 정규화 범위 계산 완료 ===");
    }

    private Map<String, List<Property>> groupPropertiesByDistrictAndLeaseType(List<Property> properties) {
        Map<String, List<Property>> grouped = new HashMap<>();

        for (Property property : properties) {
            String districtName = property.getDistrictName();
            String leaseType = property.getLeaseType();

            if (districtName == null || leaseType == null) continue;

            String groupKey = districtName + ":" + leaseType;
            grouped.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(property);
        }

        return grouped;
    }

    private NormalizationBounds calculateBoundsForGroupFixed(List<Property> groupProperties, String groupKey) {
        String[] keyParts = groupKey.split(":");
        if (keyParts.length != 2) return null;

        String leaseType = keyParts[1];
        List<Double> prices = new ArrayList<>();
        List<Double> areas = new ArrayList<>();

        for (Property property : groupProperties) {
            if ("전세".equals(leaseType) || "월세".equals(leaseType)) {
                if (property.getDeposit() != null && property.getDeposit() > 0) {
                    prices.add(property.getDeposit().doubleValue());
                }
            }

            if (property.getAreaInPyeong() != null && property.getAreaInPyeong() > 0) {
                areas.add(property.getAreaInPyeong());
            }
        }

        if (prices.isEmpty() || areas.isEmpty()) return null;

        double minPrice = Collections.min(prices);
        double maxPrice = Collections.max(prices);
        double minArea = Collections.min(areas);
        double maxArea = Collections.max(areas);

        if (minPrice == maxPrice) maxPrice = minPrice + 1000.0;
        if (minArea == maxArea) maxArea = minArea + 5.0;

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

//            log.info("Redis [Bounds] 저장 완료 - Key: {}, Count: {}, Price[{}-{}], Area[{}-{}]",
//                    redisKey, propertyCount, bounds.minPrice, bounds.maxPrice, bounds.minArea, bounds.maxArea);

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
    // 안전성 점수 계산 및 저장
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

//                log.info("Redis [Safety] 저장 완료 - Key: {}, Score: {}", redisKey, entry.getValue());

            } catch (Exception e) {
                log.error("Redis 저장 실패: {}", entry.getKey(), e);
            }
        }
    }
}
