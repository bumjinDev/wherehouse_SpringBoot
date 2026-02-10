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
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnection;
import org.springframework.data.redis.serializer.RedisSerializer;



// [2차 테스트] Slice 페이징을 위한 import
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

// [2차 테스트] 영속성 컨텍스트 초기화를 위한 import
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
 *
 * [3차 변경 - Pipeline 정상화]
 * - Lettuce 네이티브 커넥션 직접 사용 방식 제거
 * - LettuceConnectionFactory.setPipeliningFlushPolicy(flushOnClose()) 설정 기반으로
 *   executePipelined(RedisCallback) 패턴이 진짜 Pipeline으로 동작하도록 변경
 * - 변경 전: Spring의 pipeline 모드에서도 Lettuce가 매 명령마다 writeAndFlush 수행
 * - 변경 후: closePipeline() 시점에만 flush → 배치 내 모든 명령이 한 번의 RTT로 전송
 *
 * [전제조건]
 * RedisConfig에서 아래 설정이 반드시 적용되어 있어야 함:
 *   factory.setPipeliningFlushPolicy(LettuceConnection.PipeliningFlushPolicy.flushOnClose());
 */
@Slf4j
//@Component
@RequiredArgsConstructor
public class RdbSyncListener {

    // 매물 리포지토리
    private final PropertyCharterRepository propertyCharterRepository;
    private final PropertyMonthlyRepository propertyMonthlyRepository;

    // 분석 데이터 리포지토리 (안전성 점수 계산용)
    private final AnalysisEntertainmentRepository entertainmentRepository;
    private final AnalysisPopulationDensityRepository populationRepository;
    private final AnalysisCrimeRepository crimeRepository;

    // RedisHandler
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
     */
    private static final int CHUNK_SIZE = 10000;

    /**
     * Redis Pipeline 배치 크기
     *
     * executePipelined() 한 번 호출 시 처리할 매물 건수.
     * closePipeline()에서 모든 응답을 List<Object>로 수집하므로,
     * 너무 크면 응답 리스트가 힙을 압박한다.
     * 2,000건 × 3 commands = 6,000개 응답 → 충분히 안전한 범위.
     */
    private static final int PIPELINE_BATCH_SIZE = 2000;

    /**
     * 데이터 수집 완료 이벤트 핸들러
     */
//    @Scheduled(fixedDelay = Long.MAX_VALUE, initialDelay = 1000)  // 원래 이 파일에서 실행하면 안되는 코드이나, 부분적으로 테스트 시에만 앞선 선행 스케줄러 사용 안하고 수행할 수 있도록
    /* 이 부분 실제로 사용하는 코드이나 현재 테스트 환경이므로 임시로 주석 */
//    @EventListener
    @Transactional
    public void handleDataCollectionCompletedEvent(DataCollectionCompletedEvent event) {  // DataCollectionCompletedEvent event : handleDataCollectionCompletedEvent() 내 넣을 매개변수

        // =================================================================================
        // [2차 테스트] 성능 측정 로깅 - Slice 청크 처리 버전
        // =================================================================================
        String threadName = Thread.currentThread().getName();
        long batchStartTs = System.currentTimeMillis();
        log.info("[PERF:BATCH:TOTAL] thread={} | phase=START | ts={}", threadName, batchStartTs);

        long startTime = System.currentTimeMillis();

        /* ** 이 위치 실제 사용하는 코드이나 임시 주석, 이유는 현재 테스트 환경에서 매번 저장하는 로직 발생 시 매우 시간 오래 걸림. */
        // Step 1. [RDB] 매물 원본 데이터 적재
        saveCharterPropertiesToRdb(event.getCharterProperties());
        saveMonthlyPropertiesToRdb(event.getMonthlyProperties());

        log.info(">>> [Phase 2-1] RDB 적재 완료. RDB 기준 데이터 재조회 시작.");

        // =================================================================================
        // [2차 테스트] Step 2 + Step 3 통합: Slice 기반 청크 로드 + 즉시 DTO 변환
        // =================================================================================

        // === 전세 데이터: Slice 청크 로드 + DTO 변환 ===
        long charterStartTs = System.currentTimeMillis();
        log.info("[PERF:DBLOAD:CHARTER] thread={} | phase=START | ts={} | chunkSize={}",
                threadName, charterStartTs, CHUNK_SIZE);

        List<Property> charterProperties = new ArrayList<>();
        Pageable charterPageable = PageRequest.of(0, CHUNK_SIZE, Sort.by("propertyId"));
        Slice<PropertyCharter> charterSlice;
        int charterChunkIndex = 0;
        int charterTotalCount = 0;
        long charterTransformTotalMs = 0;

        do {
            long chunkStartTs = System.currentTimeMillis();

            charterSlice = propertyCharterRepository.findAllBy(charterPageable);
            List<PropertyCharter> chunkEntities = charterSlice.getContent();

            long chunkLoadEndTs = System.currentTimeMillis();
            long chunkLoadMs = chunkLoadEndTs - chunkStartTs;

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

            // 영속성 컨텍스트 초기화 → Entity 참조 해제 → GC 가능
            entityManager.clear();

            charterPageable = charterSlice.nextPageable();
            charterChunkIndex++;

        } while (charterSlice.hasNext());

        long charterEndTs = System.currentTimeMillis();
        log.info("[PERF:DBLOAD:CHARTER] thread={} | phase=END | ts={} | totalCount={} | totalChunks={} | elapsed_ms={}",
                threadName, charterEndTs, charterTotalCount, charterChunkIndex, (charterEndTs - charterStartTs));

        // === 월세 데이터: Slice 청크 로드 + DTO 변환 ===
        long monthlyStartTs = System.currentTimeMillis();
        log.info("[PERF:DBLOAD:MONTHLY] thread={} | phase=START | ts={} | chunkSize={}",
                threadName, monthlyStartTs, CHUNK_SIZE);

        List<Property> monthlyProperties = new ArrayList<>();
        Pageable monthlyPageable = PageRequest.of(0, CHUNK_SIZE, Sort.by("propertyId"));
        Slice<PropertyMonthly> monthlySlice;
        int monthlyChunkIndex = 0;
        int monthlyTotalCount = 0;
        long monthlyTransformTotalMs = 0;

        do {
            long chunkStartTs = System.currentTimeMillis();

            monthlySlice = propertyMonthlyRepository.findAllBy(monthlyPageable);
            List<PropertyMonthly> chunkEntities = monthlySlice.getContent();

            long chunkLoadEndTs = System.currentTimeMillis();
            long chunkLoadMs = chunkLoadEndTs - chunkStartTs;

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

            // 영속성 컨텍스트 초기화 → Entity 참조 해제 → GC 가능
            entityManager.clear();

            monthlyPageable = monthlySlice.nextPageable();
            monthlyChunkIndex++;

        } while (monthlySlice.hasNext());

        long monthlyEndTs = System.currentTimeMillis();
        log.info("[PERF:DBLOAD:MONTHLY] thread={} | phase=END | ts={} | totalCount={} | totalChunks={} | elapsed_ms={}",
                threadName, monthlyEndTs, monthlyTotalCount, monthlyChunkIndex, (monthlyEndTs - monthlyStartTs));

        log.info(">>> [Phase 2-2] RDB 재조회 완료. (전세: {}건, 월세: {}건)",
                charterProperties.size(), monthlyProperties.size());

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
        // 배치 종료 로깅 및 측정 결과 요약
        // =================================================================================
        long batchEndTs = System.currentTimeMillis();
        log.info("[PERF:BATCH:TOTAL] thread={} | phase=END | ts={} | elapsed_ms={}",
                threadName, batchEndTs, (batchEndTs - batchStartTs));

        log.info("====================================================================");
        log.info(">>> 측정 결과 요약 (Slice 청크 처리) <<<");
        log.info("  CHUNK_SIZE          = {} 건/청크", CHUNK_SIZE);
        log.info("  PIPELINE_BATCH_SIZE = {} 건/파이프라인", PIPELINE_BATCH_SIZE);
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
    // Redis Operation - executePipelined(RedisCallback) 기반
    // =================================================================================

    // flushOnClose() 설정 시 동작 흐름:
    //   1. executePipelined() → connection.openPipeline() 호출
    //   2. 콜백 내 hMSet(), zAdd() 등 → write만 수행 (flush 안 함, TCP 버퍼에 축적)
    //   3. 콜백 종료 → connection.closePipeline() → 이 시점에 한 번 flush → 응답 일괄 수신
    //
    // 검증 방법: DEBUG 로그에서 "write()" (flush 없이)가 연속으로 찍히고,
    //           closePipeline 시점에 한 번 flush되는 패턴이 나오면 정상.
    // =================================================================================

    /**
     * 전세 매물 Redis 동기화 - executePipelined(RedisCallback) + flushOnClose 정책
     *
     * 매물 1건당 Redis 명령:
     * - hMSet: 1회 (매물 원본 데이터, 단일 HMSET 명령으로 전송)
     * - zAdd: 2회 (전세금 인덱스, 평수 인덱스)
     * - 합계: 3 commands/건
     *
     * PIPELINE_BATCH_SIZE=2,000건 기준: 6,000 commands가 한 번의 RTT로 전송됨
     */
    private void syncCharterToRedis(List<Property> properties) {

        log.debug("syncCharterToRedis");

        if (properties == null || properties.isEmpty()) return;

        long syncStartTs = System.currentTimeMillis();
        int totalSuccess = 0;

        for (int batchStart = 0; batchStart < properties.size(); batchStart += PIPELINE_BATCH_SIZE) {

            int end = Math.min(batchStart + PIPELINE_BATCH_SIZE, properties.size());
            List<Property> batch = properties.subList(batchStart, end);

            try {
                log.info(">>> [DEBUG:PIPELINE:CHARTER] BATCH START (range: {}-{})", batchStart, end);

                redisHandler.redisTemplate.executePipelined((RedisCallback<Object>) connection -> {

                    // ====== [DEBUG] Pipeline + autoFlush 상태 확인 ======
                    log.info(">>> [DEBUG:PIPELINE:CHARTER] isPipelined={}, connectionClass={}",
                            connection.isPipelined(), connection.getClass().getName());
                    if (connection instanceof LettuceConnection lc) {
                        Object nativeConn = lc.getNativeConnection();
                        log.info(">>> [DEBUG:PIPELINE:CHARTER] nativeConnectionClass={}", nativeConn.getClass().getName());
                        if (nativeConn instanceof io.lettuce.core.api.StatefulConnection<?,?> sc) {
                            try {
                                java.lang.reflect.Field field = io.lettuce.core.RedisChannelHandler.class.getDeclaredField("autoFlushCommands");
                                field.setAccessible(true);
                                log.info(">>> [DEBUG:PIPELINE:CHARTER] autoFlushCommands={}", field.get(sc));
                            } catch (Exception e) {
                                log.warn(">>> [DEBUG] autoFlush 확인 실패: {}", e.getMessage());
                            }
                        }
                    }
                    // ====== [DEBUG] END ======

                    for (Property property : batch) {
                        String propertyId = property.getPropertyId();
                        String districtName = property.getDistrictName();
                        if (propertyId == null || districtName == null) continue;

                        // [저장소 1] 매물 원본 데이터 - hMSet은 단일 HMSET 명령으로 전송
                        byte[] hashKey = serializeKey("property:charter:" + propertyId);
                        Map<byte[], byte[]> propertyHash = serializeHashEntries(buildCharterHash(property));
                        connection.hashCommands().hMSet(hashKey, propertyHash);

                        // [저장소 2] 전세금 인덱스
                        byte[] memberKey = serializeHashValue(propertyId);
                        double charterPrice = property.getDeposit() != null
                                ? property.getDeposit().doubleValue() : 0.0;
                        connection.zSetCommands().zAdd(
                                serializeKey("idx:charterPrice:" + districtName),
                                charterPrice,
                                memberKey
                        );

                        // [저장소 3] 평수 인덱스
                        double areaScore = property.getAreaInPyeong() != null
                                ? property.getAreaInPyeong() : 0.0;
                        connection.zSetCommands().zAdd(
                                serializeKey("idx:area:" + districtName + ":전세"),
                                areaScore,
                                memberKey
                        );
                    }

                    return null;
                });

                log.info(">>> [DEBUG:PIPELINE:CHARTER] BATCH COMPLETE (range: {}-{}, closePipeline 완료)", batchStart, end);
                totalSuccess += batch.size();

            } catch (Exception e) {
                log.error("전세 배치 저장 실패 (범위: {}-{}): {}", batchStart, end, e.getMessage());
            }
        }

        long syncEndTs = System.currentTimeMillis();
        log.info("Redis [Pipeline]: 전세 매물 저장 완료 - 성공: {}건, 소요: {}ms",
                totalSuccess, (syncEndTs - syncStartTs));
    }

    /**
     * 월세 매물 Redis 동기화 - executePipelined(RedisCallback) + flushOnClose 정책
     *
     * 매물 1건당 Redis 명령:
     * - hMSet: 1회 (매물 원본 데이터)
     * - zAdd: 3회 (보증금 인덱스, 월세금 인덱스, 평수 인덱스)
     * - 합계: 4 commands/건
     *
     * PIPELINE_BATCH_SIZE=2,000건 기준: 8,000 commands가 한 번의 RTT로 전송됨
     */
    private void syncMonthlyToRedis(List<Property> properties) {
        if (properties == null || properties.isEmpty()) return;

        long syncStartTs = System.currentTimeMillis();
        int totalSuccess = 0;

        for (int batchStart = 0; batchStart < properties.size(); batchStart += PIPELINE_BATCH_SIZE) {

            int end = Math.min(batchStart + PIPELINE_BATCH_SIZE, properties.size());
            List<Property> batch = properties.subList(batchStart, end);

            try {
                log.info(">>> [DEBUG:PIPELINE:MONTHLY] BATCH START (range: {}-{})", batchStart, end);

                redisHandler.redisTemplate.executePipelined((RedisCallback<Object>) connection -> {

                    // ====== [DEBUG] Pipeline + autoFlush 상태 확인 ======
                    log.info(">>> [DEBUG:PIPELINE:MONTHLY] isPipelined={}, connectionClass={}",
                            connection.isPipelined(), connection.getClass().getName());
                    if (connection instanceof LettuceConnection lc) {
                        Object nativeConn = lc.getNativeConnection();
                        log.info(">>> [DEBUG:PIPELINE:MONTHLY] nativeConnectionClass={}", nativeConn.getClass().getName());
                        if (nativeConn instanceof io.lettuce.core.api.StatefulConnection<?,?> sc) {
                            try {
                                java.lang.reflect.Field field = io.lettuce.core.RedisChannelHandler.class.getDeclaredField("autoFlushCommands");
                                field.setAccessible(true);
                                log.info(">>> [DEBUG:PIPELINE:MONTHLY] autoFlushCommands={}", field.get(sc));
                            } catch (Exception e) {
                                log.warn(">>> [DEBUG] autoFlush 확인 실패: {}", e.getMessage());
                            }
                        }
                    }
                    // ====== [DEBUG] END ======

                    for (Property property : batch) {
                        String propertyId = property.getPropertyId();
                        String districtName = property.getDistrictName();
                        if (propertyId == null || districtName == null) continue;

                        // [저장소 1] 매물 원본 데이터
                        byte[] hashKey = serializeKey("property:monthly:" + propertyId);
                        Map<byte[], byte[]> propertyHash = serializeHashEntries(buildMonthlyHash(property));
                        connection.hashCommands().hMSet(hashKey, propertyHash);

                        byte[] memberKey = serializeHashValue(propertyId);

                        // [저장소 2] 보증금 인덱스
                        double depositPrice = property.getDeposit() != null
                                ? property.getDeposit().doubleValue() : 0.0;
                        connection.zSetCommands().zAdd(
                                serializeKey("idx:deposit:" + districtName),
                                depositPrice,
                                memberKey
                        );

                        // [저장소 3] 월세금 인덱스
                        double monthlyRentPrice = property.getMonthlyRent() != null
                                ? property.getMonthlyRent().doubleValue() : 0.0;
                        connection.zSetCommands().zAdd(
                                serializeKey("idx:monthlyRent:" + districtName + ":월세"),
                                monthlyRentPrice,
                                memberKey
                        );

                        // [저장소 4] 평수 인덱스
                        double areaScore = property.getAreaInPyeong() != null
                                ? property.getAreaInPyeong() : 0.0;
                        connection.zSetCommands().zAdd(
                                serializeKey("idx:area:" + districtName + ":월세"),
                                areaScore,
                                memberKey
                        );
                    }

                    return null;
                });

                log.info(">>> [DEBUG:PIPELINE:MONTHLY] BATCH COMPLETE (range: {}-{}, closePipeline 완료)", batchStart, end);
                totalSuccess += batch.size();

            } catch (Exception e) {
                log.error("월세 배치 저장 실패 (범위: {}-{}): {}", batchStart, end, e.getMessage());
            }
        }

        long syncEndTs = System.currentTimeMillis();
        log.info("Redis [Pipeline]: 월세 매물 저장 완료 - 성공: {}건, 소요: {}ms",
                totalSuccess, (syncEndTs - syncStartTs));
    }

    // =================================================================================
    // Hash 데이터 빌드 (Map<String, Object> → serializeHashEntries로 byte[] 변환)
    // =================================================================================

    private Map<String, Object> buildCharterHash(Property property) {
        Map<String, Object> hash = new HashMap<>();
        hash.put("propertyId", nvl(property.getPropertyId()));
        hash.put("aptNm", nvl(property.getAptNm()));
        hash.put("excluUseAr", property.getExcluUseAr() != null ? property.getExcluUseAr().toString() : "0.0");
        hash.put("floor", property.getFloor() != null ? property.getFloor().toString() : "0");
        hash.put("buildYear", property.getBuildYear() != null ? property.getBuildYear().toString() : "0");
        hash.put("dealDate", nvl(property.getDealDate()));
        hash.put("leaseType", "전세");
        hash.put("umdNm", nvl(property.getUmdNm()));
        hash.put("jibun", nvl(property.getJibun()));
        hash.put("sggCd", nvl(property.getSggCd()));
        hash.put("address", nvl(property.getAddress()));
        hash.put("areaInPyeong", property.getAreaInPyeong() != null ? property.getAreaInPyeong().toString() : "0.0");
        hash.put("rgstDate", nvl(property.getRgstDate()));
        hash.put("districtName", nvl(property.getDistrictName()));
        hash.put("deposit", property.getDeposit() != null ? property.getDeposit().toString() : "0");
        return hash;
    }

    private Map<String, Object> buildMonthlyHash(Property property) {
        Map<String, Object> hash = buildCharterHash(property);
        hash.put("leaseType", "월세");  // 덮어쓰기
        hash.put("monthlyRent", property.getMonthlyRent() != null ? property.getMonthlyRent().toString() : "0");
        return hash;
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }

    // =================================================================================
    // 직렬화 헬퍼 메서드 (RedisCallback은 byte[] 수준 API이므로 필요)
    // =================================================================================

    @SuppressWarnings("unchecked")
    private byte[] serializeKey(String key) {
        RedisSerializer<String> keySerializer =
                (RedisSerializer<String>) redisHandler.redisTemplate.getKeySerializer();
        return keySerializer.serialize(key);
    }

    @SuppressWarnings("unchecked")
    private byte[] serializeHashKey(String hashKey) {
        RedisSerializer<String> hashKeySerializer =
                (RedisSerializer<String>) redisHandler.redisTemplate.getHashKeySerializer();
        return hashKeySerializer.serialize(hashKey);
    }

    @SuppressWarnings("unchecked")
    private byte[] serializeHashValue(Object value) {
        RedisSerializer<Object> hashValueSerializer =
                (RedisSerializer<Object>) redisHandler.redisTemplate.getHashValueSerializer();
        return hashValueSerializer.serialize(value);
    }

    private Map<byte[], byte[]> serializeHashEntries(Map<String, Object> entries) {
        Map<byte[], byte[]> rawMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : entries.entrySet()) {
            rawMap.put(
                    serializeHashKey(entry.getKey()),
                    serializeHashValue(entry.getValue())
            );
        }
        return rawMap;
    }

    // =================================================================================
    // Redis 저장 검증
    // =================================================================================

    private void verifyRedisStorage(List<Property> properties, String leaseType, int sampleSize) {
        log.info("=== Redis 저장 상세 검증 시작 ({}) - 샘플 {}건 ===", leaseType, sampleSize);

        for (int i = 0; i < sampleSize; i++) {
            Property p = properties.get(i);
            String propertyId = p.getPropertyId();
            String districtName = p.getDistrictName();

            // 1. Hash Key 검증
            String redisKey = "property:" + leaseType + ":" + propertyId;
            Map<Object, Object> storedData = redisHandler.redisTemplate.opsForHash().entries(redisKey);

            // 2. Index Key 검증
            if ("charter".equals(leaseType)) {
                String priceIdx = "idx:charterPrice:" + districtName;
                String areaIdx = "idx:area:" + districtName + ":전세";

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

        // =====================================================================
        // [Pipeline 변환] 모든 그룹의 bounds 해시를 먼저 수집한 뒤 한 번의 Pipeline으로 전송
        // 변경 전: 그룹별 개별 putAll() → 그룹 수만큼 RTT 발생
        // 변경 후: 전체 그룹을 한 Pipeline에 적재 → 1 RTT
        // =====================================================================
        Map<String, Map<String, Object>> boundsEntries = new LinkedHashMap<>();

        for (Map.Entry<String, List<Property>> entry : groupedProperties.entrySet()) {
            String groupKey = entry.getKey();
            List<Property> groupProperties = entry.getValue();

            try {
                if (groupProperties.size() < 1) continue;

                NormalizationBounds bounds = calculateBoundsForGroupFixed(groupProperties, groupKey);
                if (bounds == null) continue;

                // Redis에 저장할 해시 데이터를 수집만 하고, 실제 전송은 아래 Pipeline에서 일괄 처리
                Map<String, Object> boundsHash = buildBoundsHash(groupKey, bounds, groupProperties, currentTime);
                if (boundsHash != null) {
                    String[] keyParts = groupKey.split(":");
                    String districtName = keyParts[0];
                    String leaseType = keyParts[1];
                    String redisKey = "bounds:" + districtName + ":" + leaseType;
                    boundsEntries.put(redisKey, boundsHash);
                }

            } catch (Exception e) {
                log.error("그룹 [{}] 정규화 범위 계산 실패", groupKey, e);
            }
        }

        // Pipeline 일괄 전송
        if (!boundsEntries.isEmpty()) {
            try {
                log.info(">>> [DEBUG:PIPELINE:BOUNDS] BATCH START (총 {}개 그룹)", boundsEntries.size());

                redisHandler.redisTemplate.executePipelined((RedisCallback<Object>) connection -> {

                    // ====== [DEBUG] Pipeline 상태 확인 ======
                    log.info(">>> [DEBUG:PIPELINE:BOUNDS] isPipelined = {}, connection class = {}",
                            connection.isPipelined(), connection.getClass().getName());
                    // ====== [DEBUG] END ======

                    for (Map.Entry<String, Map<String, Object>> entry : boundsEntries.entrySet()) {
                        byte[] key = serializeKey(entry.getKey());
                        Map<byte[], byte[]> rawHash = serializeHashEntries(entry.getValue());
                        connection.hashCommands().hMSet(key, rawHash);
                    }

                    return null;
                });

                log.info(">>> [DEBUG:PIPELINE:BOUNDS] BATCH COMPLETE ({}개 그룹, closePipeline 완료)", boundsEntries.size());

            } catch (Exception e) {
                log.error("Bounds Pipeline 저장 실패", e);
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

    /**
     * Bounds 해시 데이터 빌드 (Redis 전송 없이 Map만 반환)
     *
     * Pipeline에서 일괄 전송하기 위해 데이터 수집 전용으로 분리.
     * 기존 storeBoundsToRedisFixed()의 해시 빌드 로직만 추출한 것.
     */
    private Map<String, Object> buildBoundsHash(String groupKey, NormalizationBounds bounds,
                                                List<Property> groupProperties, String currentTime) {
        String[] keyParts = groupKey.split(":");
        String districtName = keyParts[0];
        String leaseType = keyParts[1];
        int propertyCount = groupProperties.size();

        Map<String, Object> boundsHash = new HashMap<>();

        if ("전세".equals(leaseType)) {
            boundsHash.put("minPrice", String.valueOf(bounds.minPrice));
            boundsHash.put("maxPrice", String.valueOf(bounds.maxPrice));
            boundsHash.put("minArea", String.valueOf(bounds.minArea));
            boundsHash.put("maxArea", String.valueOf(bounds.maxArea));
            boundsHash.put("propertyCount", String.valueOf(propertyCount));
            boundsHash.put("lastUpdated", currentTime);
            return boundsHash;

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

            boundsHash.put("minDeposit", String.valueOf(bounds.minPrice));
            boundsHash.put("maxDeposit", String.valueOf(bounds.maxPrice));
            boundsHash.put("minMonthlyRent", String.valueOf(minMonthlyRent));
            boundsHash.put("maxMonthlyRent", String.valueOf(maxMonthlyRent));
            boundsHash.put("minArea", String.valueOf(bounds.minArea));
            boundsHash.put("maxArea", String.valueOf(bounds.maxArea));
            boundsHash.put("propertyCount", String.valueOf(propertyCount));
            boundsHash.put("lastUpdated", currentTime);

            log.info("Redis [Bounds] 준비 완료 - Key: bounds:{}:{}, Count: {}, Deposit[{}-{}], Rent[{}-{}], Area[{}-{}]",
                    districtName, leaseType, propertyCount, bounds.minPrice, bounds.maxPrice,
                    minMonthlyRent, maxMonthlyRent, bounds.minArea, bounds.maxArea);
            return boundsHash;
        }

        return null;
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

    /**
     * 안전성 점수 Redis 저장 - executePipelined(RedisCallback) 기반
     *
     * 25개 자치구 × 1 hMSet = 25 commands → 1 RTT
     * 변경 전: 자치구별 개별 putAll() → 25 RTT
     */
    private void storeSafetyScoresToRedis(Map<String, Double> safetyScoreMap) {
        String currentTime = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        try {
            log.info(">>> [DEBUG:PIPELINE:SAFETY] BATCH START (총 {}개 자치구)", safetyScoreMap.size());

            redisHandler.redisTemplate.executePipelined((RedisCallback<Object>) connection -> {

                // ====== [DEBUG] Pipeline + autoFlush 상태 확인 ======
                log.info(">>> [DEBUG:PIPELINE:SAFETY] isPipelined={}, connectionClass={}",
                        connection.isPipelined(), connection.getClass().getName());
                if (connection instanceof LettuceConnection lc) {
                    Object nativeConn = lc.getNativeConnection();
                    log.info(">>> [DEBUG:PIPELINE:SAFETY] nativeConnectionClass={}", nativeConn.getClass().getName());
                    if (nativeConn instanceof io.lettuce.core.api.StatefulConnection<?,?> sc) {
                        try {
                            java.lang.reflect.Field field = io.lettuce.core.RedisChannelHandler.class.getDeclaredField("autoFlushCommands");
                            field.setAccessible(true);
                            log.info(">>> [DEBUG:PIPELINE:SAFETY] autoFlushCommands={}", field.get(sc));
                        } catch (Exception e) {
                            log.warn(">>> [DEBUG] autoFlush 확인 실패: {}", e.getMessage());
                        }
                    }
                }
                // ====== [DEBUG] END ======

                for (Map.Entry<String, Double> entry : safetyScoreMap.entrySet()) {
                    Map<String, Object> safetyHash = new HashMap<>();
                    safetyHash.put("districtName", entry.getKey());
                    safetyHash.put("safetyScore", String.valueOf(entry.getValue()));
                    safetyHash.put("lastUpdated", currentTime);
                    safetyHash.put("version", "1.0");

                    byte[] key = serializeKey("safety:" + entry.getKey());
                    Map<byte[], byte[]> rawHash = serializeHashEntries(safetyHash);
                    connection.hashCommands().hMSet(key, rawHash);
                }

                return null;
            });

            log.info(">>> [DEBUG:PIPELINE:SAFETY] BATCH COMPLETE ({}개 자치구, closePipeline 완료)", safetyScoreMap.size());

        } catch (Exception e) {
            log.error("Safety Score Pipeline 저장 실패", e);
        }
    }
}