package com.wherehouse.information.service;

import com.wherehouse.information.dao.ArrestRateRepository;
import com.wherehouse.information.dao.CctvGeoRepository;
import com.wherehouse.information.dao.PoliceOfficeGeoRepository;
import com.wherehouse.information.entity.ArrestRate;
import com.wherehouse.information.entity.CctvGeo;
import com.wherehouse.information.entity.PoliceOfficeGeo;
import com.wherehouse.information.model.*;
import com.wherehouse.information.util.*;
import com.wherehouse.information.util.KakaoApiService;
import com.wherehouse.logger.PerformanceLogger;
import com.wherehouse.logger.result.R01.R01GridResult;
import com.wherehouse.logger.result.R02.R02CacheResult;
import com.wherehouse.logger.result.R02.R02L1CacheResult;
import com.wherehouse.logger.result.R02.R02L2CacheResult;
import com.wherehouse.logger.result.R03.R03CacheWriteResult;
import com.wherehouse.logger.result.R03.R03CctvQueryResult;
import com.wherehouse.logger.result.R03.R03DbResult;
import com.wherehouse.logger.result.R04.R04AddressApiResult;
import com.wherehouse.logger.result.R04.R04AmenityApiResult;
import com.wherehouse.logger.result.R04.R04ApiResult;
import com.wherehouse.logger.result.R04.R04ArrestRateResult;
import com.wherehouse.logger.result.R05.R05AmenityFilterResult;
import com.wherehouse.logger.result.R05.R05CctvFilterResult;
import com.wherehouse.logger.result.R05.R05FilterResult;
import com.wherehouse.logger.result.R05.R05PoliceQueryResult;
import com.wherehouse.logger.result.R06.R06ConvenienceScoreResult;
import com.wherehouse.logger.result.R06.R06SafetyScoreResult;
import com.wherehouse.logger.result.R06.R06ScoreResult;
import com.wherehouse.logger.result.R07.R07CacheWriteResult;
import com.wherehouse.logger.result.R07.R07ResponseResult;
import com.wherehouse.redis.service.RedisSingleDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 위치 분석 서비스 구현체
 *
 * 사용자가 선택한 좌표 기반으로 주변 안전 인프라(CCTV, 파출소)와 편의시설을 분석하여
 * 종합 안전 점수 및 편의성 점수를 제공하는 핵심 비즈니스 로직 구현
 *
 * 처리 단계 (6.4.4절 실시간 서비스 처리 단계)
 * - R-01: '9-Block' 그리드 범위 계산 구현 완료
 * - R-02: 단계별 캐시 조회 구현 완료
 * - R-03: 선택된 데이터베이스 조회 구현 완료
 * - R-04: 외부 API 호출 및 개별 데이터 캐싱 구현 완료
 * - R-05: 데이터 통합, 필터링, 최종 응답 생성 대기 개발 예정
 * - R-06: 최종 점수 계산 개발 예정
 * - R-07: 최종 응답 생성 및 캐싱 개발 예정
 *
 * @author wherehouse-team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocationAnalysisServiceImpl implements ILocationAnalysisService {

    private final GeohashService geohashService;
    private final CctvGeoRepository cctvGeoRepository;
    private final PoliceOfficeGeoRepository policeOfficeGeoRepository;
    private final ArrestRateRepository arrestRateRepository;
    private final KakaoApiService kakaoApiService;
    private final RedisSingleDataService redisSingleDataService;
    private final ObjectMapper objectMapper;

    // Redis 캐시 TTL 설정
    private static final Duration LEVEL1_CACHE_TTL = Duration.ofMinutes(5);  // 1단계: 5분
    private static final Duration LEVEL2_CACHE_TTL = Duration.ofHours(24);   // 2단계: 24시간

    @Override
    public LocationAnalysisResponseDTO analyzeLocation(LocationAnalysisRequestDTO request) {
        log.info("=== 위치 분석 시작 ===");
        log.info("요청 좌표: latitude={}, longitude={}, radius={}",
                request.getLatitude(), request.getLongitude(), request.getRadius());

        // R-01: '9-Block' 그리드 범위 계산
        List<String> nineBlockGeohashes = calculate9BlockGrid(request);

        // R-02: 단계별 캐시 조회
        CacheResult cacheResult = performCacheLookup(nineBlockGeohashes, request);

        // 1단계 캐시 히트 시 즉시 반환
        if (cacheResult.isLevel1Hit()) {
            log.info("=== 1단계 캐시 히트: 즉시 반환 ===");
            return cacheResult.getCachedResponse();
        }

        // R-03: 선택된 데이터베이스 조회
        DatabaseQueryResult dbResult = performDatabaseQuery(cacheResult);

        // R-04: 외부 API 호출 및 개별 데이터 캐싱
        ExternalApiResult apiResult = performExternalApiCalls(request);

        // R-05: 데이터 통합, 필터링
        IntegratedDataResult integratedResult = integrateAndFilterData(
                request, dbResult, apiResult, nineBlockGeohashes);

        // R-06: 최종 점수 계산
        ScoringResult scoringResult = calculateScores(integratedResult);

        // R-07: 최종 응답 생성 및 캐싱
        LocationAnalysisResponseDTO response = buildFinalResponse(
                request, integratedResult, scoringResult, nineBlockGeohashes.get(0));

        return response;
    }

    /**
     * R-01: '9-Block' 그리드 범위 계산
     *
     * 사용자가 요청한 좌표를 기반으로 Geohash 기반 9개 격자(3x3 그리드) ID를 생성한다.
     * 이는 사용자의 분석 반경이 단일 격자를 초과하여 인접 격자에 걸쳐 있을 가능성을
     * 구조적으로 해결하기 위한 검색 범위 확장 메커니즘이다.
     *
     * 처리 흐름
     * 1. 요청 좌표를 7자리 정밀도 Geohash로 인코딩하여 중심 격자 ID 생성
     * 2. 중심 격자 주변 8방향의 인접 격자 ID 계산 (총 9개)
     * 3. 계산된 격자 ID 목록을 다음 단계(R-02)로 전달
     *
     * 설계 근거
     * - 7자리 정밀도 Geohash: 약 150m × 150m 격자 단위
     * - 9-Block 그리드: 약 450m × 450m 영역 커버 (사용자 요청 반경 500m 포함)
     * - 경계 문제 해결: 좌표가 격자 경계 근처에 있어도 모든 데이터 포착 보장
     *
     * @param request 사용자 요청 DTO (위도, 경도 포함)
     * @return 9개의 Geohash ID 목록 (중심 1개 + 인접 8개)
     */
    private List<String> calculate9BlockGrid(LocationAnalysisRequestDTO request) {

        // R-01 전체 Step 계측 시작
        PerformanceLogger perfLogger = PerformanceLogger.start(
                "R-01",                              // step
                "calculate9BlockGrid",               // action
                "Service",                           // layer
                "LocationAnalysisServiceImpl",       // class
                "calculate9BlockGrid"                // method
        );

        /* 본 루직 결과 담을 빈 dto */
        R01GridResult result = R01GridResult.builder()
                .requestLatitude(request.getLatitude())
                .requestLongitude(request.getLongitude())
                .requestRadius(request.getRadius())
                .isSuccess(false)  // 기본값
                .build();

        try {

            log.info("[R-01] 9-Block 그리드 범위 계산 시작");

            log.info("[R-01] 요청 좌표: lat={}, lon={}",
                    request.getLatitude(), request.getLongitude());

            double latitude = request.getLatitude();
            double longitude = request.getLongitude();

            // GeohashService를 통해 중심 격자 + 인접 8개 격자 = 총 9개 격자 ID 생성
            List<String> nineBlockIds = geohashService.calculate9BlockGeohashes(latitude, longitude);

            /* 로깅 : 성공 결과 dto 에 넣을 값 설정 */
            result.setCenterGeohashId(nineBlockIds.get(0));
            result.setNineBlockGeohashes(nineBlockIds);
            result.setTotalGridCount(nineBlockIds.size());
            result.setSuccess(true);

            perfLogger.setResultData(result);

            log.info("[R-01] 계산된 9-Block 그리드: {}", nineBlockIds);
            log.info("[R-01] 중심 격자 ID: {}", nineBlockIds.get(0));


            // 본 로직 : 결과 반환
            return nineBlockIds;

        } catch(Exception e) {
            log.error("[R-01] 9-Block 그리드 계산 실패", e);

            /* 로깅 : 실패 결과 dto 에 넣을 값 설정 */
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            perfLogger.setResultData(result);

            throw e;

        } finally {

            /* R-01 전체 Step 계측 종료 */
            perfLogger.end();
        }
    }

    private CacheResult performCacheLookup(List<String> nineBlockGeohashes,
                                           LocationAnalysisRequestDTO request) {

        PerformanceLogger perfLogger = PerformanceLogger.start(
                "R-02",                              // step
                "performCacheLookup",                // action
                "Service",                           // layer
                "LocationAnalysisServiceImpl",       // class
                "performCacheLookup"                 // method
        );

        /* 본 로직 결과 담을 r-02 로직에 대한 dto 초기화. */
        R02CacheResult r02CacheResult = R02CacheResult.builder()
                .centerGeohashId(nineBlockGeohashes.get(0))
                .nineBlockGeohashes(nineBlockGeohashes)
                .l1CacheHit(false)
                .l2CacheRequired(false)
                .isSuccess(false)
                .errorMessage(null)
                .build();

        log.info("[R-02] 단계별 캐시 조회 시작");

        String centerGeohashId = nineBlockGeohashes.get(0);
        String level1CacheKey = "dto:" + centerGeohashId;

        // 1단계 캐시 조회: 중심 격자 ID를 키로 최종 응답 DTO 전체 조회 시도
        log.info("[R-02-1단계] 전체 DTO 캐시 조회 - Key: {}", level1CacheKey);

        // ===== [시간 측정 변수 선언] =====
        long l1CacheGetDurationNs = 0;
        Long l1JsonDeserializeDurationNs = null;
        // =====

        try {
            // ===== [Action 1: L1 캐시 조회 시간 측정] =====
            long l1StartNs = System.nanoTime();
            String cachedJson = redisSingleDataService.getSingleData(level1CacheKey);
            long l1EndNs = System.nanoTime();
            l1CacheGetDurationNs = l1EndNs - l1StartNs;
            // =====

            /* 1Layer Cache hit */
            if (cachedJson != null && !cachedJson.isEmpty()) {

                log.info("[R-02-1단계] 캐시 히트! JSON 역직렬화 시도");

                // ===== [Action 3: L1 JSON 역직렬화 시간 측정] =====
                long deserializeStartNs = System.nanoTime();
                LocationAnalysisResponseDTO cachedDto = objectMapper.readValue(
                        cachedJson,
                        LocationAnalysisResponseDTO.class
                );
                long deserializeEndNs = System.nanoTime();
                l1JsonDeserializeDurationNs = deserializeEndNs - deserializeStartNs;
                // =====

                // L1 캐시 히트 결과를 생성
                R02L1CacheResult r02L1CacheResult = R02L1CacheResult.builder()
                        .cacheKey(level1CacheKey)
                        .hit(true)
                        .valueSize(cachedJson.length())
                        .l1CacheGetDurationNs(l1CacheGetDurationNs)  // [추가]
                        .l1JsonDeserializeDurationNs(l1JsonDeserializeDurationNs)  // [추가]
                        .build();

                // R02CacheResult 저장
                r02CacheResult.setL1CacheHit(true);
                r02CacheResult.setL1CacheResult(r02L1CacheResult);
                r02CacheResult.setL2CacheRequired(false);
                r02CacheResult.setL2CacheResults(null);
                r02CacheResult.setL2TotalHits(0);
                r02CacheResult.setL2TotalMisses(0);
                r02CacheResult.setSuccess(true);
                r02CacheResult.setErrorMessage(null);

                log.info("[R-02-1단계] 캐시 히트 성공! 즉시 반환 (L1 조회: {}ms, 역직렬화: {}ms)",
                        l1CacheGetDurationNs / 1_000_000.0,
                        l1JsonDeserializeDurationNs / 1_000_000.0);

                // 로깅 후 종료
                perfLogger.setResultData(r02CacheResult);
                perfLogger.end();

                return CacheResult.level1Hit(cachedDto);
            }

        } catch (Exception e) {
            log.warn("[R-02-1단계] 캐시 조회 중 오류 발생: {}", e.getMessage());

            // L1 캐시 조회 실패 시에도 L2로 진행하므로 여기서 로깅하지 않음
            // 단, 에러 메시지는 기록
            r02CacheResult.setErrorMessage(e.getMessage());
        }

        /* 1차 캐시 미스 - L1 결과 설정 */
        R02L1CacheResult r02L1CacheResult = R02L1CacheResult.builder()
                .cacheKey(level1CacheKey)
                .hit(false)
                .valueSize(0)
                .l1CacheGetDurationNs(l1CacheGetDurationNs)  // [추가] 미스 시에도 조회 시간 기록
                .l1JsonDeserializeDurationNs(null)
                .build();

        r02CacheResult.setL1CacheHit(false);
        r02CacheResult.setL1CacheResult(r02L1CacheResult);

        log.info("[R-02-1단계] 캐시 미스. 2단계 캐시 조회 진행 (L1 조회: {}ms)",
                l1CacheGetDurationNs / 1_000_000.0);

        // 2단계 캐시 조회: 9개 격자 각각에 대해 CCTV 컴포넌트 데이터 조회
        log.info("[R-02-2단계] 개별 격자 데이터 캐시 조회 시작 (9개 격자)");

        // 실제 응답으로 포함할 캐시 저장 객체
        CacheResult result = new CacheResult();
        result.setLevel1Hit(false);
        result.setNineBlockGeohashes(nineBlockGeohashes);

        // L2 캐시 결과를 담을 리스트
        List<R02L2CacheResult> l2CacheResults = new ArrayList<>();
        int l2Hits = 0;
        int l2Misses = 0;

        // ===== [Action 2: L2 캐시 전체 조회 시간 측정 시작 - B-03 병목 핵심!] =====
        long l2TotalStartNs = System.nanoTime();
        long l2JsonDeserializeTotalNs = 0;  // L2 전체 역직렬화 시간 누적
        // =====

        // 각 격자 별로 CCTV 데이터 캐시 존재 여부 확인
        for (String geohashId : nineBlockGeohashes) {
            // CCTV 데이터 캐시 키 생성 및 조회 (형식: "data:{geohashId}:cctv")
            String cctvCacheKey = "data:" + geohashId + ":cctv";
            log.debug("[R-02-2단계] CCTV 캐시 격자 별 조회 - Key: {}", cctvCacheKey);

            try {
                /* Redis 내 해당 geoHash 값에 따른 캐싱 값 존재 여부 확인 */
                // ===== [격자별 조회 시간 측정 - 선택적] =====
                long gridStartNs = System.nanoTime();
                String cctvJson = redisSingleDataService.getSingleData(cctvCacheKey);
                long gridEndNs = System.nanoTime();
                long gridDurationNs = gridEndNs - gridStartNs;
                // =====

                if (cctvJson != null && !cctvJson.isEmpty()) {

                    // ===== [JSON 역직렬화 시간 측정] =====
                    long deserializeStartNs = System.nanoTime();
                    List<CctvGeo> cachedCctv = objectMapper.readValue(
                            cctvJson,
                            new TypeReference<List<CctvGeo>>() {}
                    );
                    long deserializeEndNs = System.nanoTime();
                    long deserializeDurationNs = deserializeEndNs - deserializeStartNs;
                    l2JsonDeserializeTotalNs += deserializeDurationNs;
                    // =====

                    log.debug("[R-02-2단계] CCTV 캐시 히트 - GeohashId: {}, 개수: {}, 조회: {}ms, 역직렬화: {}ms",
                            geohashId, cachedCctv.size(),
                            gridDurationNs / 1_000_000.0,
                            deserializeDurationNs / 1_000_000.0);

                    // 캐시 데이터 저장
                    result.addCachedCctv(geohashId, cachedCctv);

                    /* 로깅: 캐시 히트에 따른 해당 격자 ID에 대한 R02L2CacheResult 객체 생성 */
                    l2CacheResults.add(
                            R02L2CacheResult.builder()
                                    .geohashId(geohashId)
                                    .cacheKey(cctvCacheKey)
                                    .hit(true)
                                    .dataType("cctv")
                                    .dataCount(cachedCctv.size())
                                    .dataSize(cctvJson.length())  // bytes
                                    .l2CacheGetDurationNs(gridDurationNs)  // [추가]
                                    .build()
                    );

                    l2Hits++;

                } else {
                    log.debug("[R-02-2단계] CCTV 캐시 미스 - GeohashId: {}, 조회: {}ms",
                            geohashId,
                            gridDurationNs / 1_000_000.0);
                    result.addCctvMiss(geohashId);

                    /* 로깅: 캐시 미스 */
                    l2CacheResults.add(
                            R02L2CacheResult.builder()
                                    .geohashId(geohashId)
                                    .cacheKey(cctvCacheKey)
                                    .hit(false)
                                    .dataType("cctv")
                                    .dataCount(0)
                                    .dataSize(0)
                                    .l2CacheGetDurationNs(gridDurationNs)  // [추가]
                                    .build()
                    );

                    l2Misses++;
                }

            } catch (Exception e) {
                log.warn("[R-02-2단계] CCTV 캐시 조회 중 오류 - GeohashId: {}, 오류: {}",
                        geohashId, e.getMessage());
                result.addCctvMiss(geohashId);

                /* 로깅: 에러 발생 시에도 미스로 기록 */
                l2CacheResults.add(
                        R02L2CacheResult.builder()
                                .geohashId(geohashId)
                                .cacheKey(cctvCacheKey)
                                .hit(false)
                                .dataType("cctv")
                                .dataCount(0)
                                .dataSize(0)
                                .l2CacheGetDurationNs(null)  // [추가] 에러 시 null
                                .build()
                );

                l2Misses++;

                // 에러 메시지 누적 (첫 번째 에러만 기록)
                if (r02CacheResult.getErrorMessage() == null) {
                    r02CacheResult.setErrorMessage(e.getMessage());
                }
            }
        }

        // ===== [Action 2: L2 캐시 전체 조회 시간 측정 완료 - B-03 병목 핵심!] =====
        long l2TotalEndNs = System.nanoTime();
        long l2TotalDurationNs = l2TotalEndNs - l2TotalStartNs;
        // =====

        // L2 캐시 결과 설정
        r02CacheResult.setL2CacheRequired(true);
        r02CacheResult.setL2CacheResults(l2CacheResults);
        r02CacheResult.setL2TotalHits(l2Hits);
        r02CacheResult.setL2TotalMisses(l2Misses);
        r02CacheResult.setL2CacheTotalDurationNs(l2TotalDurationNs);  // [추가] B-03 병목 핵심 지표!
        r02CacheResult.setL2JsonDeserializeTotalDurationNs(l2JsonDeserializeTotalNs);  // [추가]

        // L2 캐시 조회가 완료되었으므로 성공으로 설정
        if (l2Hits > 0 || l2Misses > 0) {
            r02CacheResult.setSuccess(true);
        }

        log.info("[R-02-2단계] 개별 격자 데이터 캐시 조회 완료");
        log.info("[R-02-2단계] CCTV 캐시 히트: {}개, 미스: {}개", l2Hits, l2Misses);
        log.info("[R-02-2단계] L2 전체 조회 시간: {}ms, 역직렬화: {}ms",
                l2TotalDurationNs / 1_000_000.0,
                l2JsonDeserializeTotalNs / 1_000_000.0);
        log.info("[R-02] 단계별 캐시 조회 완료");

        // 로깅 후 종료
        perfLogger.setResultData(r02CacheResult);
        perfLogger.end();

        return result;
    }

    /**
     * R-03: 선택된 데이터베이스 조회
     *
     * R-02에서 2단계 캐시 미스가 발생한 격자에 대해서만 DB 조회를 수행한다.
     * B-Tree 인덱스가 적용된 geohash_id 컬럼을 활용하여 수백만 건의 데이터에서도
     * 밀리초 단위의 빠른 조회를 보장한다.
     *
     * 처리 흐름
     * 1. R-02에서 캐시 히트된 데이터를 결과 객체에 먼저 추가
     * 2. 캐시 미스 격자 목록에 대해서만 DB 조회 실행 (선택적 조회)
     * 3. 조회된 데이터를 격자별로 그룹화 (groupCctvByGeohash)
     * 4. 각 격자 데이터를 즉시 Redis 2단계 캐시에 저장 (TTL: 24시간)
     *
     * 인덱스 활용 전략
     * - 쿼리 패턴: WHERE geohash_id IN (...)
     * - 사용 인덱스: IDX_CCTV_GEO_GEOHASH (B-Tree)
     * - 성능: 9개 격자 조회 시 9번의 Index Range Scan (각 밀리초 단위)
     *
     * 설계 근거
     * - 선택적 조회: 캐시 미스 격자만 조회하여 DB 부하 최소화
     * - 즉시 캐싱: 조회 직후 캐싱으로 다음 요청에서 재사용 보장
     * - 전체 컬럼 조회: ADDRESS, LATITUDE, LONGITUDE 등 모든 필드 포함
     *
     * 주의사항
     * - 파출소 데이터는 희소성으로 인해 Geohash 기반 조회가 부적합하므로
     *   이 단계에서는 CCTV 데이터만 처리하고, 파출소는 R-05에서 직접 쿼리
     *
     * @param cacheResult R-02에서 반환된 캐시 조회 결과 객체 (히트 데이터 + 미스 목록)
     * @return DB 조회 결과 객체 (캐시 데이터 + 새로 조회한 데이터 통합)
     */
    private DatabaseQueryResult performDatabaseQuery(CacheResult cacheResult) {

        PerformanceLogger perfLogger = PerformanceLogger.start(
                "R-03",
                "performDatabaseQuery",
                "Service",
                "LocationAnalysisServiceImpl",
                "performDatabaseQuery"
        );

        log.info("[R-03] 선택된 데이터베이스 조회 시작");

        DatabaseQueryResult dbResult = new DatabaseQueryResult();

        // R-03 로깅 DTO 초기화
        List<String> cctvMisses = cacheResult.getCctvMisses();
        R03DbResult r03DbResult = R03DbResult.builder()
                .inputCctvMissGrids(cctvMisses)
                .cctvQueryResult(null)
                .cctvCacheWrites(null)
                .isSuccess(false)
                .errorMessage(null)
                .build();

        // 1. R-02에서 캐시 히트된 데이터를 결과 객체에 먼저 추가 (캐시 재사용)
        dbResult.getCctvData().putAll(cacheResult.getCachedCctvData());
        // 파출소는 R-05에서 직접 쿼리하므로 여기서는 제외

        // 2. CCTV 데이터 DB 조회 (캐시 미스 격자만 대상, IN 절로 일괄 조회)
        if (!cctvMisses.isEmpty()) {
            log.info("[R-03] CCTV 데이터 DB 조회 시작 - 대상 격자: {}개", cctvMisses.size());

            // 서브 루틴 1: CCTV DB 쿼리 실행 로깅 준비
            R03CctvQueryResult cctvQueryResult = R03CctvQueryResult.builder()
                    .queryGeohashIds(cctvMisses)
                    .totalRowsReturned(0)
                    .rowsPerGrid(new HashMap<>())
                    .queryExecutionTimeNs(0)
                    .isSuccess(false)
                    .errorMessage(null)
                    .build();

            long queryStartNs = System.nanoTime();

            try {
                // B-Tree 인덱스(IDX_CCTV_GEO_GEOHASH)를 활용한 IN 절 조회
                List<CctvGeo> cctvList = cctvGeoRepository.findByGeohashIdIn(cctvMisses);

                long queryEndNs = System.nanoTime();
                long queryDurationNs = queryEndNs - queryStartNs;

                log.info("[R-03] CCTV DB 조회 완료 - 조회된 데이터: {}건", cctvList.size());

                // 조회된 CCTV 리스트를 geohash_id 기준으로 그룹화 (Map<GeohashId, List<CctvGeo>>)
                Map<String, List<CctvGeo>> groupedCctv = groupCctvByGeohash(cctvList);

                // 격자별 행 수 계산 (로깅용)
                Map<String, Integer> rowsPerGrid = new HashMap<>();
                for (Map.Entry<String, List<CctvGeo>> entry : groupedCctv.entrySet()) {
                    rowsPerGrid.put(entry.getKey(), entry.getValue().size());
                }

                // 쿼리 결과 로깅 DTO 설정
                cctvQueryResult.setTotalRowsReturned(cctvList.size());
                cctvQueryResult.setRowsPerGrid(rowsPerGrid);
                cctvQueryResult.setQueryExecutionTimeNs(queryDurationNs);
                cctvQueryResult.setSuccess(true);
                cctvQueryResult.setErrorMessage(null);

                // 서브 루틴 2: L2 캐시 쓰기 로깅 준비
                List<R03CacheWriteResult> cacheWriteResults = new ArrayList<>();

                // 격자별로 분류된 데이터를 순회하며 결과 객체에 추가 + Redis 2단계 캐시 저장
                for (Map.Entry<String, List<CctvGeo>> entry : groupedCctv.entrySet()) {
                    String geohashId = entry.getKey();
                    List<CctvGeo> data = entry.getValue();

                    // DatabaseQueryResult 객체에 해당 격자의 CCTV 데이터 추가
                    dbResult.getCctvData().put(geohashId, data);

                    // 캐시 쓰기 로깅 객체 생성
                    String cacheKey = "data:" + geohashId + ":cctv";
                    R03CacheWriteResult cacheWriteResult = R03CacheWriteResult.builder()
                            .geohashId(geohashId)
                            .cacheKey(cacheKey)
                            .dataCount(data.size())
                            .dataSize(0)
                            .isSuccess(false)
                            .errorMessage(null)
                            .build();

                    try {
                        // JSON 직렬화 (dataSize 측정용)
                        String jsonData = objectMapper.writeValueAsString(data);

                        // 기존 메서드 그대로 호출 (비즈니스 로직 변경 없음)
                        cacheGeohashData(geohashId, "cctv", data);

                        // 캐시 쓰기 성공 로깅
                        cacheWriteResult.setDataSize(jsonData.length());
                        cacheWriteResult.setSuccess(true);
                        cacheWriteResult.setErrorMessage(null);

                    } catch (Exception e) {
                        // 캐시 쓰기 실패 로깅
                        cacheWriteResult.setSuccess(false);
                        cacheWriteResult.setErrorMessage(e.getMessage());
                    }

                    cacheWriteResults.add(cacheWriteResult);
                    log.debug("[R-03] CCTV 캐싱 완료 - GeohashId: {}, 개수: {}건", geohashId, data.size());
                }

                // R-03 결과 설정
                r03DbResult.setCctvQueryResult(cctvQueryResult);
                r03DbResult.setCctvCacheWrites(cacheWriteResults);
                r03DbResult.setSuccess(true);
                r03DbResult.setErrorMessage(null);

            } catch (Exception e) {
                log.error("[R-03] CCTV DB 조회 중 오류 발생", e);
                dbResult.addError("CCTV 데이터 조회 실패: " + e.getMessage());

                // 쿼리 실패 로깅
                cctvQueryResult.setSuccess(false);
                cctvQueryResult.setErrorMessage(e.getMessage());

                r03DbResult.setCctvQueryResult(cctvQueryResult);
                r03DbResult.setCctvCacheWrites(new ArrayList<>());
                r03DbResult.setSuccess(false);
                r03DbResult.setErrorMessage(e.getMessage());
            }

        } else {
            log.info("[R-03] CCTV 데이터는 모두 캐시에서 조회됨");

            // 캐시에서 모두 조회된 경우에도 성공으로 기록
            r03DbResult.setCctvQueryResult(null);
            r03DbResult.setCctvCacheWrites(new ArrayList<>());
            r03DbResult.setSuccess(true);
            r03DbResult.setErrorMessage(null);
        }

        // 파출소 데이터 처리는 R-05로 이관됨
        log.info("[R-03] 데이터베이스 조회 완료");
        log.info("[R-03] CCTV 총 격자: {}개", dbResult.getCctvData().size());

        // 로깅 후 종료
        perfLogger.setResultData(r03DbResult);
        perfLogger.end();

        return dbResult;
    }

    /**
     * CCTV 리스트를 geohash_id별로 그룹화
     *
     * DB에서 IN 절로 조회한 CCTV 리스트를 격자 ID를 키로 하는 Map으로 변환한다.
     * 이는 각 격자별 데이터를 개별 캐싱하고 결과 객체에 추가하기 위한 전처리 단계다.
     *
     * @param cctvList DB에서 조회된 CCTV 엔티티 리스트 (여러 격자의 데이터가 혼재)
     * @return 격자 ID를 키로 하는 Map (예: "wydm7p1" -> [CctvGeo1, CctvGeo2, ...])
     *
     * @see #performDatabaseQuery(CacheResult)
     * @todo 대용량 데이터 처리 시 Stream API의 Collectors.groupingBy() 또는 병렬 스트림 고려
     */
    private Map<String, List<CctvGeo>> groupCctvByGeohash(List<CctvGeo> cctvList) {
        Map<String, List<CctvGeo>> grouped = new HashMap<>();

        // 각 CCTV를 순회하며 geohash_id를 추출하여 해당 격자의 리스트에 추가
        for (CctvGeo cctv : cctvList) {
            String geohashId = cctv.getGeohashId();

            // 해당 격자 ID의 리스트가 Map에 없으면 새로운 ArrayList 생성
            if (!grouped.containsKey(geohashId)) {
                grouped.put(geohashId, new ArrayList<>());
            }

            // 해당 격자 리스트에 현재 CCTV 객체 추가
            grouped.get(geohashId).add(cctv);
        }

        return grouped;
    }

    /**
     * 파출소 리스트를 geohash_id별로 그룹화
     *
     * DB에서 IN 절로 조회한 파출소 리스트를 격자 ID를 키로 하는 Map으로 변환한다.
     * 이는 각 격자별 데이터를 개별 캐싱하고 결과 객체에 추가하기 위한 전처리 단계다.
     *
     * @param policeList DB에서 조회된 파출소 엔티티 리스트 (여러 격자의 데이터가 혼재)
     * @return 격자 ID를 키로 하는 Map (예: "wydm7p1" -> [PoliceGeo1, PoliceGeo2, ...])
     *
     * @see #performDatabaseQuery(CacheResult)
     * @todo 대용량 데이터 처리 시 Stream API의 Collectors.groupingBy() 또는 병렬 스트림 고려
     */
    private Map<String, List<PoliceOfficeGeo>> groupPoliceByGeohash(List<PoliceOfficeGeo> policeList) {
        Map<String, List<PoliceOfficeGeo>> grouped = new HashMap<>();

        // 각 파출소를 순회하며 geohash_id를 추출하여 해당 격자의 리스트에 추가
        for (PoliceOfficeGeo police : policeList) {
            String geohashId = police.getGeohashId();

            // 해당 격자 ID의 리스트가 Map에 없으면 새로운 ArrayList 생성
            if (!grouped.containsKey(geohashId)) {
                grouped.put(geohashId, new ArrayList<>());
            }

            // 해당 격자 리스트에 현재 파출소 객체 추가
            grouped.get(geohashId).add(police);
        }

        return grouped;
    }

    /**
     * R-04: 외부 API 호출 및 개별 데이터 캐싱
     *
     * 카카오맵 API를 통해 주소 변환, 편의시설 검색을 수행하고 내부 DB에서 검거율을 조회한다.
     *
     * *** 성능 측정을 위해 순차 실행으로 변경됨 ***
     * - 각 작업의 순수 실행 시간을 정확히 측정하기 위해 비동기 처리 제거
     * - 병목 지점 식별 후 추후 비동기 처리로 최적화 예정
     *
     * 순차 처리 작업 목록
     * 1. 주소 변환 API: 좌표 → 도로명/지번 주소 (카카오맵 Reverse Geocoding)
     * 2. 편의시설 검색 API: 반경 내 15개 카테고리 편의시설 조회 (카카오맵 로컬 검색)
     * 3. 검거율 조회: 주소에서 추출한 '구' 기준 내부 DB 조회 (주소 변환 완료 후 실행)
     *
     * 캐싱 전략
     * - 주소 변환: 키 "address:{lat}:{lon}", TTL 24시간
     * - 편의시설: 키 "amenity:{lat}:{lon}:{radius}", TTL 24시간
     * - 검거율: 키 "arrest_rate:{구이름}", TTL 24시간
     *
     * 장애 격리 (Fault Isolation)
     * 각 작업이 독립적으로 실행되므로 일부 API 실패 시에도 전체 프로세스가 중단되지 않는다.
     * 실패한 작업은 ExternalApiResult의 errors 리스트에 기록되며, 성공한 작업의 데이터는 정상 반환된다.
     *
     * 예상 성능 (순차 실행)
     * - 주소 변환: 약 100ms
     * - 편의시설 검색: 약 300ms
     * - 검거율 조회: 약 20ms (캐시 미스), 약 1-2ms (캐시 히트)
     * - 총 소요 시간: 약 420ms (캐시 미스), 약 401ms (캐시 히트)
     *
     * @param request 사용자 요청 DTO (위도, 경도, 반경 포함)
     * @return 외부 API 호출 결과 객체 (주소, 편의시설, 검거율 + 오류 목록)
     */
    private ExternalApiResult performExternalApiCalls(LocationAnalysisRequestDTO request) {

        PerformanceLogger perfLogger = PerformanceLogger.start(
                "R-04",
                "performExternalApiCalls",
                "Service",
                "LocationAnalysisServiceImpl",
                "performExternalApiCalls"
        );

        log.info("[R-04] 외부 API 호출 시작 (순차 실행)");

        double latitude = request.getLatitude();
        double longitude = request.getLongitude();
        int radius = request.getRadius();

        ExternalApiResult result = new ExternalApiResult();

        // R-04 메인 로깅 DTO 초기화
        R04ApiResult r04ApiResult = R04ApiResult.builder()
                .latitude(latitude)
                .longitude(longitude)
                .radius(radius)
                .totalSequentialTasks(3)
                .totalExecutionTimeNs(0)
                .addressApiResult(null)
                .amenityApiResult(null)
                .arrestRateResult(null)
                .isSuccess(false)
                .errorMessage(null)
                .build();

        // ============================================
        // 1. 주소 변환 API 호출 (카카오맵 Reverse Geocoding)
        // ============================================

        R04AddressApiResult addressApiResult = R04AddressApiResult.builder()
                .cached(false)
                .cacheKey(null)
                .roadAddress(null)
                .jibunAddress(null)
                .responseSize(null)
                .executionTimeNs(0)
                .isSuccess(false)
                .errorMessage(null)
                .build();

        long addressStartNs = System.nanoTime();

        try {
            log.info("[R-04] 주소 변환 API 호출 시작 - 좌표: ({}, {})", latitude, longitude);

            // 캐시 키 생성 (좌표 기반, 형식: "address:{latitude}:{longitude}")
            String cacheKey = "address:" + latitude + ":" + longitude;
            addressApiResult.setCacheKey(cacheKey);

            // Redis 2단계 캐시 조회 시도
            String cachedAddress = redisSingleDataService.getSingleData(cacheKey);

            if (cachedAddress != null && !cachedAddress.isEmpty()) {
                log.info("[R-04] 주소 변환 캐시 히트");

                // JSON 역직렬화하여 AddressDto 객체로 변환 후 결과에 설정
                AddressDto addressDto = objectMapper.readValue(cachedAddress, AddressDto.class);
                result.setAddress(addressDto);

                // 로깅 DTO 설정 (캐시 히트)
                addressApiResult.setCached(true);
                addressApiResult.setRoadAddress(addressDto.getRoadAddress());
                addressApiResult.setJibunAddress(addressDto.getJibunAddress());
                addressApiResult.setResponseSize(cachedAddress.length());
                addressApiResult.setSuccess(true);

            } else {
                log.info("[R-04] 주소 변환 캐시 미스 - API 호출");

                Callable Callable =null;
                Future Future = null;

                // 카카오맵 Reverse Geocoding API 호출 (좌표 → 주소 변환)
                AddressDto addressDto = kakaoApiService.getAddress(latitude, longitude);
                result.setAddress(addressDto);

                // API 응답을 JSON 직렬화 후 Redis 2단계 캐시에 저장 (TTL: 24시간)
                String addressJson = objectMapper.writeValueAsString(addressDto);
                redisSingleDataService.setSingleData(cacheKey, addressJson, LEVEL2_CACHE_TTL);

                log.info("[R-04] 주소 변환 결과 캐싱 완료");

                // 로깅 DTO 설정 (캐시 미스)
                addressApiResult.setCached(false);
                addressApiResult.setRoadAddress(addressDto.getRoadAddress());
                addressApiResult.setJibunAddress(addressDto.getJibunAddress());
                addressApiResult.setResponseSize(addressJson.length());
                addressApiResult.setSuccess(true);
            }

        } catch (Exception e) {
            log.error("[R-04] 주소 변환 API 호출 중 오류 발생", e);
            result.addError("주소 변환 실패: " + e.getMessage());

            // 로깅 DTO 설정 (실패)
            addressApiResult.setSuccess(false);
            addressApiResult.setErrorMessage(e.getMessage());
        }

        long addressEndNs = System.nanoTime();
        addressApiResult.setExecutionTimeNs(addressEndNs - addressStartNs);

        // ============================================
        // 2. 편의시설 조회 API 호출 (카카오맵 로컬 검색, 15개 카테고리)
        // ============================================

        R04AmenityApiResult amenityApiResult = R04AmenityApiResult.builder()
                .cached(false)
                .cacheKey(null)
                .categoryCount(0)
                .placesByCategory(new HashMap<>())
                .totalPlaces(0)
                .responseSize(null)
                .executionTimeNs(0)
                .isSuccess(false)
                .errorMessage(null)
                .build();

        long amenityStartNs = System.nanoTime();

        /* 모든 편의시설(Kakao Map API 15개 코드) */

        try {
            log.info("[R-04] 편의시설 조회 API 호출 시작 - 반경: {}m", radius);

            // 캐시 키 생성 (좌표 + 반경 기반, 형식: "amenity:{lat}:{lon}:{radius}")
            String cacheKey = "amenity:" + latitude + ":" + longitude + ":" + radius;
            amenityApiResult.setCacheKey(cacheKey);

            // Redis 2단계 캐시 조회 시도
            String cachedAmenity = redisSingleDataService.getSingleData(cacheKey);

            Map<String, List<Map<String, Object>>> amenityData;

            if (cachedAmenity != null && !cachedAmenity.isEmpty()) {
                log.info("[R-04] 편의시설 캐시 히트");

                // JSON 역직렬화하여 Map<카테고리코드, List<장소>> 형태로 변환 후 결과에 설정
                amenityData = objectMapper.readValue(cachedAmenity,
                        new TypeReference<Map<String, List<Map<String, Object>>>>() {});
                result.setAmenityData(amenityData);

                // 로깅 DTO 설정 (캐시 히트)
                amenityApiResult.setCached(true);
                amenityApiResult.setResponseSize(cachedAmenity.length());

            } else {
                log.info("[R-04] 편의시설 캐시 미스 - API 호출");

                // 카카오맵 로컬 검색 API 호출 (15개 카테고리를 병렬로 조회)
                amenityData = kakaoApiService.searchAllAmenities(latitude, longitude, radius);
                result.setAmenityData(amenityData);

                // API 응답을 JSON 직렬화 후 Redis 2단계 캐시에 저장 (TTL: 24시간)
                String amenityJson = objectMapper.writeValueAsString(amenityData);
                redisSingleDataService.setSingleData(cacheKey, amenityJson, LEVEL2_CACHE_TTL);

                log.info("[R-04] 편의시설 결과 캐싱 완료 - 총 카테고리: {}개", amenityData.size());

                // 로깅 DTO 설정 (캐시 미스)
                amenityApiResult.setCached(false);
                amenityApiResult.setResponseSize(amenityJson.length());
            }

            // 카테고리별 장소 개수 계산 (공통)
            Map<String, Integer> placesByCategory = new HashMap<>();
            int totalPlaces = 0;

            for (Map.Entry<String, List<Map<String, Object>>> entry : amenityData.entrySet()) {

                String categoryCode = entry.getKey();
                int count = entry.getValue().size();
                placesByCategory.put(categoryCode, count);
                totalPlaces += count;
            }

            amenityApiResult.setCategoryCount(amenityData.size());
            amenityApiResult.setPlacesByCategory(placesByCategory);
            amenityApiResult.setTotalPlaces(totalPlaces);
            amenityApiResult.setSuccess(true);

        } catch (Exception e) {
            log.error("[R-04] 편의시설 조회 API 호출 중 오류 발생", e);
            result.addError("편의시설 조회 실패: " + e.getMessage());

            // 로깅 DTO 설정 (실패)
            amenityApiResult.setSuccess(false);
            amenityApiResult.setErrorMessage(e.getMessage());
        }

        long amenityEndNs = System.nanoTime();
        amenityApiResult.setExecutionTimeNs(amenityEndNs - amenityStartNs);

        // ============================================
        // 3. 검거율 조회 (내부 DB 조회, 주소 변환 결과 필요) - Redis 캐싱 추가
        // ============================================

        R04ArrestRateResult arrestRateResult = R04ArrestRateResult.builder()
                .cached(false)
                .cacheKey(null)
                .guName(null)
                .arrestRate(null)
                .dataFound(false)
                .executionTimeNs(0)
                .isSuccess(false)
                .errorMessage(null)
                .build();

        long arrestRateStartNs = System.nanoTime();

        try {
            log.info("[R-04] 검거율 조회 시작");

            if (result.getAddress() != null) {
                String address = result.getAddress().getRoadAddress();

                // 주소 문자열에서 '구' 단위 추출 (예: "서울특별시 중구 세종대로 110" → "중구")
                String gu = extractGu(address);

                if (gu != null) {
                    log.info("[R-04] 추출된 구: {}", gu);

                    arrestRateResult.setGuName(gu);

                    // 캐시 키 생성 (구 단위 기반, 형식: "arrest_rate:{구이름}")
                    String cacheKey = "arrest_rate:" + gu;
                    arrestRateResult.setCacheKey(cacheKey);

                    // Redis 캐시 조회 시도
                    String cachedRate = redisSingleDataService.getSingleData(cacheKey);

                    if (cachedRate != null && !cachedRate.isEmpty()) {
                        log.info("[R-04] 검거율 캐시 히트 - 구: {}", gu);

                        double rate = Double.parseDouble(cachedRate);
                        result.setArrestRate(rate);

                        // 로깅 DTO 설정 (캐시 히트)
                        arrestRateResult.setCached(true);
                        arrestRateResult.setArrestRate(rate);
                        arrestRateResult.setDataFound(true);
                        arrestRateResult.setSuccess(true);

                    } else {
                        log.info("[R-04] 검거율 캐시 미스 - DB 조회");

                        // ArrestRate 테이블에서 해당 '구'의 검거율 데이터 조회
                        java.util.Optional<ArrestRate> arrestRateOpt = arrestRateRepository.findByAddr(gu);

                        if (arrestRateOpt.isPresent()) {
                            double rate = arrestRateOpt.get().getRate();
                            result.setArrestRate(rate);

                            // Redis 캐시에 저장 (TTL: 24시간)
                            redisSingleDataService.setSingleData(cacheKey, String.valueOf(rate), LEVEL2_CACHE_TTL);
                            log.info("[R-04] 검거율 조회 성공 및 캐싱 완료 - {}: {}", gu, rate);

                            // 로깅 DTO 설정 (캐시 미스, 데이터 존재)
                            arrestRateResult.setCached(false);
                            arrestRateResult.setArrestRate(rate);
                            arrestRateResult.setDataFound(true);
                            arrestRateResult.setSuccess(true);

                        } else {
                            log.warn("[R-04] 검거율 데이터 없음 - 구: {}", gu);
                            result.setArrestRate(0.0);

                            // 로깅 DTO 설정 (캐시 미스, 데이터 없음)
                            arrestRateResult.setCached(false);
                            arrestRateResult.setArrestRate(0.0);
                            arrestRateResult.setDataFound(false);
                            arrestRateResult.setSuccess(true);
                        }
                    }
                } else {
                    log.warn("[R-04] 주소에서 구 추출 실패: {}", address);
                    result.setArrestRate(0.0);

                    // 로깅 DTO 설정 (구 추출 실패)
                    arrestRateResult.setArrestRate(0.0);
                    arrestRateResult.setDataFound(false);
                    arrestRateResult.setSuccess(true);
                }
            } else {
                log.warn("[R-04] 주소 변환 결과 없음 - 검거율 조회 불가");
                result.setArrestRate(0.0);

                // 로깅 DTO 설정 (주소 없음)
                arrestRateResult.setArrestRate(0.0);
                arrestRateResult.setDataFound(false);
                arrestRateResult.setSuccess(true);
            }

        } catch (Exception e) {
            log.error("[R-04] 검거율 조회 중 오류 발생", e);
            result.addError("검거율 조회 실패: " + e.getMessage());
            result.setArrestRate(0.0);

            // 로깅 DTO 설정 (실패)
            arrestRateResult.setArrestRate(0.0);
            arrestRateResult.setSuccess(false);
            arrestRateResult.setErrorMessage(e.getMessage());
        }

        long arrestRateEndNs = System.nanoTime();
        arrestRateResult.setExecutionTimeNs(arrestRateEndNs - arrestRateStartNs);

        // ============================================
        // R-04 메인 로깅 DTO 최종 설정
        // ============================================

        long totalExecutionTimeNs = addressApiResult.getExecutionTimeNs()
                + amenityApiResult.getExecutionTimeNs()
                + arrestRateResult.getExecutionTimeNs();

        r04ApiResult.setAddressApiResult(addressApiResult);
        r04ApiResult.setAmenityApiResult(amenityApiResult);
        r04ApiResult.setArrestRateResult(arrestRateResult);
        r04ApiResult.setTotalExecutionTimeNs(totalExecutionTimeNs);
        r04ApiResult.setSuccess(true);
        r04ApiResult.setErrorMessage(null);

        // ============================================
        // 최종 결과 로깅
        // ============================================

        log.info("[R-04] 외부 API 호출 결과:");
        log.info("[R-04] - 주소: {}", result.getAddress() != null ? "성공" : "실패");
        log.info("[R-04] - 편의시설: {}", result.getAmenityData() != null ?
                result.getAmenityData().size() + "개 카테고리" : "실패");
        log.info("[R-04] - 검거율: {}", result.getArrestRate());

        if (result.hasErrors()) {
            log.warn("[R-04] 발생한 오류: {}", result.getErrors());
        }

        // 로깅 후 종료
        perfLogger.setResultData(r04ApiResult);
        perfLogger.end();

        return result;
    }

    /**
     * R-05: 데이터 통합, 필터링, 최종 응답 생성 대기
     *
     * R-03과 R-04에서 조회한 모든 데이터를 통합하고, 사용자가 요청한 반경 내의 데이터만 필터링한다.
     * 9-Block 그리드로 조회한 데이터는 약 450m × 450m 영역을 포함하므로,
     * 정확한 반경(예: 500m) 필터링을 위해 Haversine 공식 기반 거리 계산이 필수적이다.
     *
     * 처리 흐름
     * 1. 9개 격자에서 조회한 모든 CCTV 데이터를 단일 리스트로 통합
     * 2. 각 CCTV와 요청 좌표 간 실제 거리 계산 (Haversine 공식)
     * 3. 반경 내 CCTV만 필터링 및 카운트 집계
     * 4. 9개 격자에서 조회한 모든 파출소 데이터를 단일 리스트로 통합
     * 5. 각 파출소와 요청 좌표 간 거리 계산 후 가장 가까운 파출소 선정
     * 6. 외부 API 결과(주소, 편의시설, 검거율)와 통합
     * 7. 편의시설 각 장소별 거리 계산 및 카테고리별 AmenityDetailDto 생성
     *
     * @param request 사용자 요청 DTO (위도, 경도, 반경)
     * @param dbResult R-03에서 반환된 DB 조회 결과 (격자별 CCTV/파출소)
     * @param apiResult R-04에서 반환된 외부 API 호출 결과 (주소, 편의시설, 검거율)
     * @param nineBlockGeohashes R-01에서 계산된 9개 격자 ID 목록
     * @return 통합 및 필터링된 데이터 결과 객체
     */
    private IntegratedDataResult integrateAndFilterData(
            LocationAnalysisRequestDTO request,
            DatabaseQueryResult dbResult,
            ExternalApiResult apiResult,
            List<String> nineBlockGeohashes) {

        PerformanceLogger perfLogger = PerformanceLogger.start(
                "R-05",
                "integrateAndFilterData",
                "Service",
                "LocationAnalysisServiceImpl",
                "integrateAndFilterData"
        );

        log.info("[R-05] 데이터 통합 및 필터링 시작");

        double userLatitude = request.getLatitude();
        double userLongitude = request.getLongitude();
        int radius = request.getRadius();

        IntegratedDataResult result = new IntegratedDataResult();

        // R-05 메인 로깅 DTO 초기화
        R05FilterResult r05FilterResult = R05FilterResult.builder()
                .requestRadius(radius)
                .cctvFilter(null)
                .policeQuery(null)
                .amenityFilter(null)
                .isSuccess(false)
                .errorMessage(null)
                .build();

        // ============================================
        // 1. CCTV 데이터 통합 및 필터링
        // ============================================

        log.info("[R-05] CCTV 데이터 통합 및 반경 필터링 시작");

        R05CctvFilterResult cctvFilterResult = R05CctvFilterResult.builder()
                .totalCctvBeforeFilter(0)
                .totalCctvAfterFilter(0)
                .totalCameraCount(0)
                .filterRate(0.0)
                .filterExecutionTimeNs(0)
                .isSuccess(false)
                .build();

        List<CctvGeo> allCctvList = new ArrayList<>();

        for (String geohashId : nineBlockGeohashes) {
            List<CctvGeo> cctvInGrid = dbResult.getCctvData().get(geohashId);
            if (cctvInGrid != null) {
                allCctvList.addAll(cctvInGrid);
            }
        }

        log.info("[R-05] 9개 격자에서 통합된 전체 CCTV 개수: {}개", allCctvList.size());

        cctvFilterResult.setTotalCctvBeforeFilter(allCctvList.size());

        List<CctvGeo> filteredCctvList = new ArrayList<>();
        int totalCameraCount = 0;

        // CCTV 필터링 루프 시간 측정 시작
        long cctvFilterStartNs = System.nanoTime();

        for (CctvGeo cctv : allCctvList) {
            double distance = geohashService.calculateDistance(
                    userLatitude, userLongitude,
                    cctv.getLatitude(), cctv.getLongitude());

            if (distance <= radius) {
                filteredCctvList.add(cctv);
                totalCameraCount += cctv.getCameraCount();
            }
        }

        // CCTV 필터링 루프 시간 측정 종료
        long cctvFilterEndNs = System.nanoTime();
        cctvFilterResult.setFilterExecutionTimeNs(cctvFilterEndNs - cctvFilterStartNs);

        result.setFilteredCctvList(filteredCctvList);
        result.setTotalCameraCount(totalCameraCount);

        cctvFilterResult.setTotalCctvAfterFilter(filteredCctvList.size());
        cctvFilterResult.setTotalCameraCount(totalCameraCount);

        if (allCctvList.size() > 0) {
            cctvFilterResult.setFilterRate((double) filteredCctvList.size() / allCctvList.size());
        } else {
            cctvFilterResult.setFilterRate(0.0);
        }

        cctvFilterResult.setSuccess(true);

        log.info("[R-05] 반경 {}m 내 필터링된 CCTV: {}개 (총 카메라 대수: {}대)",
                radius, filteredCctvList.size(), totalCameraCount);

        // ============================================
        // 2. 파출소 데이터 - 격자 상관없이 가장 가까운 파출소 1개만 찾기
        // ============================================

        log.info("[R-05] 가장 가까운 파출소 검색 시작");

        R05PoliceQueryResult policeQueryResult = R05PoliceQueryResult.builder()
                .queryDurationNs(0)
                .found(false)
                .nearestAddress(null)
                .nearestDistance(null)
                .isSuccess(false)
                .errorMessage(null)
                .build();

        PoliceOfficeGeo nearestPolice = null;
        double minDistance = Double.MAX_VALUE;

        long policeQueryStartNs = System.nanoTime();

        try {
            // DB에서 직접 가장 가까운 파출소 1개 조회
            List<PoliceOfficeGeo> nearestPoliceList = policeOfficeGeoRepository
                    .findNearestPoliceStations(userLatitude, userLongitude, 1);

            long policeQueryEndNs = System.nanoTime();
            policeQueryResult.setQueryDurationNs(policeQueryEndNs - policeQueryStartNs);

            if (!nearestPoliceList.isEmpty()) {
                nearestPolice = nearestPoliceList.get(0);
                minDistance = geohashService.calculateDistance(
                        userLatitude, userLongitude,
                        nearestPolice.getLatitude(), nearestPolice.getLongitude());

                log.info("[R-05] 가장 가까운 파출소 발견: {} (거리: {}m)",
                        nearestPolice.getAddress(), Math.round(minDistance));

                policeQueryResult.setFound(true);
                policeQueryResult.setNearestAddress(nearestPolice.getAddress());
                policeQueryResult.setNearestDistance(minDistance);
                policeQueryResult.setSuccess(true);

            } else {
                policeQueryResult.setFound(false);
                policeQueryResult.setNearestDistance(Double.MAX_VALUE);
                policeQueryResult.setSuccess(true);
            }

        } catch (Exception e) {
            log.error("[R-05] 파출소 검색 중 오류 발생", e);

            long policeQueryEndNs = System.nanoTime();
            policeQueryResult.setQueryDurationNs(policeQueryEndNs - policeQueryStartNs);

            policeQueryResult.setSuccess(false);
            policeQueryResult.setErrorMessage(e.getMessage());
        }

        result.setNearestPolice(nearestPolice);
        result.setDistanceToNearestPolice(minDistance);

        if (nearestPolice == null) {
            log.warn("[R-05] 파출소를 찾을 수 없습니다.");
        }

        // ============================================
        // 3. 외부 API 데이터 통합
        // ============================================

        log.info("[R-05] 외부 API 데이터 통합 시작");

        result.setAddress(apiResult.getAddress());
        result.setArrestRate(apiResult.getArrestRate());

        // ============================================
        // 4. 편의시설 데이터 처리 - AmenityDetailDto 리스트 생성
        // ============================================

        R05AmenityFilterResult amenityFilterResult = R05AmenityFilterResult.builder()
                .placesBeforeFilter(new HashMap<>())
                .placesAfterFilter(new HashMap<>())
                .totalBeforeFilter(0)
                .totalAfterFilter(0)
                .filterRate(0.0)
                .filterExecutionTimeNs(0)
                .isSuccess(false)
                .build();

        if (apiResult.getAmenityData() != null) {

            log.info("[R-05] 편의시설 데이터 거리 계산 및 DTO 변환 시작");

            List<AmenityDetailDto> amenityDetailsList = new ArrayList<>();
            Map<String, String> categoryNames = createCategoryNameMap();

            Map<String, Integer> placesBeforeFilter = new HashMap<>();
            Map<String, Integer> placesAfterFilter = new HashMap<>();
            int totalBeforeFilter = 0;
            int totalAfterFilter = 0;

            // 편의시설 필터링 루프 시간 측정 시작
            long amenityFilterStartNs = System.nanoTime();

            for (Map.Entry<String, List<Map<String, Object>>> entry : apiResult.getAmenityData().entrySet()) {

                String categoryCode = entry.getKey();
                List<Map<String, Object>> places = entry.getValue();

                if (places == null || places.isEmpty()) {
                    continue;
                }

                int beforeCount = places.size();
                placesBeforeFilter.put(categoryCode, beforeCount);
                totalBeforeFilter += beforeCount;

                List<PlaceDto> placeDtoList = new ArrayList<>();

                for (Map<String, Object> place : places) {

                    String name = (String) place.get("name");
                    double placeLatitude = ((Number) place.get("latitude")).doubleValue();
                    double placeLongitude = ((Number) place.get("longitude")).doubleValue();

                    double distance = geohashService.calculateDistance(
                            userLatitude, userLongitude,
                            placeLatitude, placeLongitude);

                    if (distance <= radius) {
                        PlaceDto placeDto = new PlaceDto();
                        placeDto.setName(name);
                        placeDto.setLatitude(placeLatitude);
                        placeDto.setLongitude(placeLongitude);
                        placeDto.setDistance((int) Math.round(distance));

                        placeDtoList.add(placeDto);
                    }
                }

                int afterCount = placeDtoList.size();
                placesAfterFilter.put(categoryCode, afterCount);
                totalAfterFilter += afterCount;

                placeDtoList.sort((p1, p2) -> Integer.compare(p1.getDistance(), p2.getDistance()));

                AmenityDetailDto amenityDetail = new AmenityDetailDto();
                amenityDetail.setCategoryCode(categoryCode);
                amenityDetail.setCategoryName(categoryNames.getOrDefault(categoryCode, categoryCode));
                amenityDetail.setCount(placeDtoList.size());
                amenityDetail.setClosestDistance(placeDtoList.isEmpty() ? 0 : placeDtoList.get(0).getDistance());
                amenityDetail.setPlaces(placeDtoList);

                amenityDetailsList.add(amenityDetail);

                log.debug("[R-05] - {}: {}개 장소 (최근접 거리: {}m)",
                        categoryCode,
                        placeDtoList.size(),
                        amenityDetail.getClosestDistance());
            }

            // 편의시설 필터링 루프 시간 측정 종료
            long amenityFilterEndNs = System.nanoTime();
            amenityFilterResult.setFilterExecutionTimeNs(amenityFilterEndNs - amenityFilterStartNs);

            result.setAmenityDetails(amenityDetailsList);

            amenityFilterResult.setPlacesBeforeFilter(placesBeforeFilter);
            amenityFilterResult.setPlacesAfterFilter(placesAfterFilter);
            amenityFilterResult.setTotalBeforeFilter(totalBeforeFilter);
            amenityFilterResult.setTotalAfterFilter(totalAfterFilter);

            if (totalBeforeFilter > 0) {
                amenityFilterResult.setFilterRate((double) totalAfterFilter / totalBeforeFilter);
            } else {
                amenityFilterResult.setFilterRate(0.0);
            }

            amenityFilterResult.setSuccess(true);

            log.info("[R-05] 편의시설 상세 정보 통합 완료: {}개 카테고리",
                    amenityDetailsList.size());

        } else {
            log.warn("[R-05] 편의시설 데이터가 없습니다.");

            result.setAmenityDetails(new ArrayList<>());

            amenityFilterResult.setSuccess(true);
        }

        log.info("[R-05] 데이터 통합 및 필터링 완료");

        // ============================================
        // R-05 메인 로깅 DTO 최종 설정
        // ============================================

        r05FilterResult.setCctvFilter(cctvFilterResult);
        r05FilterResult.setPoliceQuery(policeQueryResult);
        r05FilterResult.setAmenityFilter(amenityFilterResult);
        r05FilterResult.setSuccess(true);
        r05FilterResult.setErrorMessage(null);

        perfLogger.setResultData(r05FilterResult);
        perfLogger.end();

        return result;
    }

    /**
     * 카테고리 코드 -> 카테고리명 매핑 생성
     *
     * 카카오맵 API는 categoryGroupCode만 제공하고 이름은 제공하지 않으므로
     * 설계 명세서 13.3.1절에 정의된 15개 카테고리를 직접 매핑
     */
    private Map<String, String> createCategoryNameMap() {
        Map<String, String> map = new HashMap<>();
        map.put("SW8", "지하철역");
        map.put("CS2", "편의점");
        map.put("FD6", "음식점");
        map.put("CE7", "카페");
        map.put("MT1", "대형마트");
        map.put("BK9", "은행");
        map.put("PO3", "공공기관");
        map.put("CT1", "문화시설");
        map.put("HP8", "병원");
        map.put("PM9", "약국");
        map.put("PK6", "주차장");
        map.put("OL7", "주유소");
        map.put("SC4", "학교");
        map.put("AC5", "학원");
        map.put("AT4", "관광명소");
        return map;
    }

    /**
     * R-06: 최종 점수 계산
     *
     * 통합된 데이터를 기반으로 안전 점수와 편의성 점수를 계산한다.
     * 각 점수는 0-100 범위로 정규화되며, 가중치를 통해 최종 점수를 산출한다.
     *
     * 안전 점수 계산 요소:
     * - 파출소 거리 (30%)
     * - CCTV 수 (40%)
     * - 검거율 (30%)
     *
     * 편의성 점수 계산 요소:
     * - 편의시설 카테고리별 가중치 적용
     * - 거리 기반 감점 (가까울수록 높은 점수)
     *
     * @param integratedResult R-05에서 통합된 데이터
     * @return 계산된 점수 결과 객체
     */
    private ScoringResult calculateScores(IntegratedDataResult integratedResult) {

        PerformanceLogger perfLogger = PerformanceLogger.start(
                "R-06",
                "calculateScores",
                "Service",
                "LocationAnalysisServiceImpl",
                "calculateScores"
        );

        log.info("[R-06] 최종 점수 계산 시작");

        ScoringResult result = new ScoringResult();

        // R-06 메인 로깅 DTO 초기화
        R06ScoreResult r06ScoreResult = R06ScoreResult.builder()
                .safetyScore(null)
                .convenienceScore(null)
                .overallScore(0.0)
                .isSuccess(false)
                .errorMessage(null)
                .build();

        // 안전 점수 로깅 DTO (필드 레벨)
        R06SafetyScoreResult r06SafetyScoreResult = null;

        // 편의성 점수 로깅 DTO (필드 레벨)
        R06ConvenienceScoreResult r06ConvenienceScoreResult = null;

        try {
            // 1. 안전 점수 계산
            double safetyScore = calculateSafetyScore(integratedResult);
            result.setSafetyScore(safetyScore);

            // 안전 점수 로깅 DTO 생성 (calculateSafetyScore 실행 후)
            r06SafetyScoreResult = createSafetyScoreResult(integratedResult, safetyScore);

            // 2. 편의성 점수 계산
            double convenienceScore = calculateConvenienceScore(integratedResult);
            result.setConvenienceScore(convenienceScore);

            // 편의성 점수 로깅 DTO 생성 (calculateConvenienceScore 실행 후)
            r06ConvenienceScoreResult = createConvenienceScoreResult(integratedResult, convenienceScore);

            // 3. 종합 점수 계산 (안전 50%, 편의성 50%)
            double totalScore = (safetyScore + convenienceScore) / 2;
            result.setTotalScore(totalScore);

            log.info("[R-06] 점수 계산 완료 - 안전: {}, 편의성: {}, 종합: {}",
                    String.format("%.2f", safetyScore),
                    String.format("%.2f", convenienceScore),
                    String.format("%.2f", totalScore));

            // R-06 메인 로깅 DTO 최종 설정
            r06ScoreResult.setSafetyScore(r06SafetyScoreResult);
            r06ScoreResult.setConvenienceScore(r06ConvenienceScoreResult);
            r06ScoreResult.setOverallScore(totalScore);
            r06ScoreResult.setSuccess(true);
            r06ScoreResult.setErrorMessage(null);

        } catch (Exception e) {
            log.error("[R-06] 점수 계산 중 오류 발생", e);

            r06ScoreResult.setSuccess(false);
            r06ScoreResult.setErrorMessage(e.getMessage());
        }

        perfLogger.setResultData(r06ScoreResult);
        perfLogger.end();

        return result;
    }

    /**
     * 안전 점수 계산
     *
     * JS의 실제 구현을 따라 계산:
     * - 거리 점수 = (800 - 거리) / 800 × 100
     * - CCTV 점수 = CCTV수 / 30 × 100
     * - 검거율 점수 = 검거율 × 100
     *
     * 가중치:
     * - 거리: 30%
     * - CCTV: 40%
     * - 검거율: 30%
     *
     * @param data 통합 데이터
     * @return 0-100 범위의 안전 점수
     */
    private double calculateSafetyScore(IntegratedDataResult data) {
        // 1. 파출소 거리 점수 계산 (JS와 동일하게 800m 기준)
        double distanceScore = 0;
        if (data.getNearestPolice() != null) {
            double distance = data.getDistanceToNearestPolice();
            if (distance <= 800) {
                distanceScore = (800 - distance) / 800 * 100;
            } else {
                distanceScore = 0;
            }
        }

        // 2. CCTV 점수 계산 (JS와 동일하게 30개 기준)
        int cctvCount = data.getFilteredCctvList().size();
        double cctvScore = Math.min(cctvCount / 30.0 * 100, 100);

        // 3. 검거율 점수 계산 (검거율 × 100)
        double arrestScore = data.getArrestRate() * 100;

        // 4. 가중치 적용 (JS의 weight 객체와 동일)
        double safetyScore = (distanceScore * 0.3) + (cctvScore * 0.4) + (arrestScore * 0.3);

        log.debug("안전 점수 계산 상세:");
        log.debug("- 파출소 거리 점수: {} (거리: {}m)", distanceScore,
                data.getNearestPolice() != null ? Math.round(data.getDistanceToNearestPolice()) : "없음");
        log.debug("- CCTV 점수: {} (개수: {}개)", cctvScore, cctvCount);
        log.debug("- 검거율 점수: {} (검거율: {})", arrestScore, data.getArrestRate());
        log.debug("- 최종 안전 점수: {}", safetyScore);

        return safetyScore;
    }

    /**
     * 안전 점수 로깅 DTO 생성 헬퍼 메서드
     */
    private R06SafetyScoreResult createSafetyScoreResult(IntegratedDataResult data, double finalScore) {
        double distanceScore = 0;
        double distance = 0;

        if (data.getNearestPolice() != null) {
            distance = data.getDistanceToNearestPolice();
            if (distance <= 800) {
                distanceScore = (800 - distance) / 800 * 100;
            }
        } else {
            distance = Double.MAX_VALUE;
        }

        int cctvCount = data.getFilteredCctvList().size();
        double cctvScore = Math.min(cctvCount / 30.0 * 100, 100);
        double arrestScore = data.getArrestRate() * 100;

        return R06SafetyScoreResult.builder()
                .policeDistanceScore(distanceScore)
                .policeDistance(distance)
                .cctvScore(cctvScore)
                .cctvCount(cctvCount)
                .arrestRateScore(arrestScore)
                .arrestRate(data.getArrestRate())
                .policeWeight(0.3)
                .cctvWeight(0.4)
                .arrestWeight(0.3)
                .finalScore(finalScore)
                .isSuccess(true)
                .build();
    }

    /**
     * 편의성 점수 계산
     *
     * JS의 amenity.js 로직을 참고하여 구현
     * 각 카테고리별로 다른 가중치와 계산 방식 적용
     *
     * @param data 통합 데이터
     * @return 0-100 범위의 편의성 점수
     */
    private double calculateConvenienceScore(IntegratedDataResult data) {
        if (data.getAmenityDetails() == null || data.getAmenityDetails().isEmpty()) {
            return 0;
        }

        // JS에서 하드코딩된 구별 인구 데이터
        Map<String, Integer> populationData = new HashMap<>();
        populationData.put("종로구", 149608);
        populationData.put("중구", 131214);
        populationData.put("용산구", 217154);
        populationData.put("성동구", 281259);
        populationData.put("광진구", 345652);
        populationData.put("동대문구", 358603);
        populationData.put("중랑구", 385349);
        populationData.put("성북구", 435037);
        populationData.put("강북구", 299374);
        populationData.put("도봉구", 306032);
        populationData.put("노원구", 496552);
        populationData.put("은평구", 465350);
        populationData.put("서대문구", 318622);
        populationData.put("마포구", 372745);
        populationData.put("양천구", 434351);
        populationData.put("강서구", 562194);
        populationData.put("구로구", 411916);
        populationData.put("금천구", 239070);
        populationData.put("영등포구", 397173);
        populationData.put("동작구", 387352);
        populationData.put("관악구", 495620);
        populationData.put("서초구", 413076);
        populationData.put("강남구", 563215);
        populationData.put("송파구", 656310);
        populationData.put("강동구", 451474);

        // 현재 구 이름 가져오기 (주소에서 추출)
        String currentGu = null;
        if (data.getAddress() != null) {
            currentGu = extractGu(data.getAddress().getRoadAddress());
        }

        // 현재 구의 인구수 (기본값: 400000)
        int population = populationData.getOrDefault(currentGu, 400000);

        double totalScore = 0;
        int categoryCount = 0;

        // 각 카테고리별 점수 계산
        for (AmenityDetailDto amenity : data.getAmenityDetails()) {
            double categoryScore = 0;
            String code = amenity.getCategoryCode();
            int count = amenity.getCount();
            int closestDistance = amenity.getClosestDistance();

            switch (code) {
                case "CS2":  // 편의점
                    categoryScore = Math.min((count / (population / 10000.0)) * 10, 100);
                    break;

                case "CE7":  // 카페
                    categoryScore = Math.min(count * 2, 100);
                    break;

                case "FD6":  // 음식점
                    categoryScore = Math.min(count * 1, 100);
                    break;

                case "SW8":  // 지하철역
                    if (closestDistance <= 500) {
                        categoryScore = 100;
                    } else if (closestDistance <= 1000) {
                        categoryScore = 100 - ((closestDistance - 500) / 500.0 * 50);
                    } else {
                        categoryScore = Math.max(0, 50 - ((closestDistance - 1000) / 1000.0 * 50));
                    }
                    break;

                case "MT1":  // 대형마트
                    categoryScore = Math.min(count * 10, 100);
                    break;

                case "BK9":  // 은행
                    categoryScore = Math.min(count * 5, 100);
                    break;

                case "PM9":  // 약국
                    categoryScore = Math.min(count * 8, 100);
                    break;

                case "HP8":  // 병원
                    double distanceScore = closestDistance <= 1000 ? 50 : 25;
                    double countScore = Math.min(count * 10, 50);
                    categoryScore = distanceScore + countScore;
                    break;

                case "PO3":  // 공공기관
                    categoryScore = Math.min(count * 3, 100);
                    break;

                case "CT1":  // 문화시설
                    categoryScore = Math.min(count * 4, 100);
                    break;

                case "PK6":  // 주차장
                    categoryScore = Math.min(count * 3, 100);
                    break;

                case "OL7":  // 주유소
                    categoryScore = Math.min(count * 5, 100);
                    break;

                case "SC4":  // 학교
                    categoryScore = Math.min(count * 2, 100);
                    break;

                case "AC5":  // 학원
                    categoryScore = Math.min(count * 1, 100);
                    break;

                case "AT4":  // 관광명소
                    categoryScore = Math.min(count * 3, 100);
                    break;

                default:
                    categoryScore = Math.min(count * 2, 100);
            }

            totalScore += categoryScore;
            categoryCount++;

            log.debug("편의시설 점수 - {}: {} (개수: {}, 최근접: {}m, 점수: {})",
                    amenity.getCategoryName(), code, count, closestDistance, categoryScore);
        }

        // 전체 평균 점수 계산
        double convenienceScore = categoryCount > 0 ? totalScore / categoryCount : 0;

        log.debug("편의성 점수 계산 완료: {} (총 {} 카테고리, {} 인구: {})",
                convenienceScore, categoryCount, currentGu, population);

        return convenienceScore;
    }

    /**
     * 편의성 점수 로깅 DTO 생성 헬퍼 메서드
     */
    private R06ConvenienceScoreResult createConvenienceScoreResult(IntegratedDataResult data, double finalScore) {
        if (data.getAmenityDetails() == null || data.getAmenityDetails().isEmpty()) {
            return R06ConvenienceScoreResult.builder()
                    .categoryScores(new HashMap<>())
                    .categoryNames(new HashMap<>())
                    .currentGu(null)
                    .guPopulation(null)
                    .finalScore(0.0)
                    .isSuccess(true)
                    .build();
        }

        Map<String, Integer> populationData = new HashMap<>();
        populationData.put("종로구", 149608);
        populationData.put("중구", 131214);
        populationData.put("용산구", 217154);
        populationData.put("성동구", 281259);
        populationData.put("광진구", 345652);
        populationData.put("동대문구", 358603);
        populationData.put("중랑구", 385349);
        populationData.put("성북구", 435037);
        populationData.put("강북구", 299374);
        populationData.put("도봉구", 306032);
        populationData.put("노원구", 496552);
        populationData.put("은평구", 465350);
        populationData.put("서대문구", 318622);
        populationData.put("마포구", 372745);
        populationData.put("양천구", 434351);
        populationData.put("강서구", 562194);
        populationData.put("구로구", 411916);
        populationData.put("금천구", 239070);
        populationData.put("영등포구", 397173);
        populationData.put("동작구", 387352);
        populationData.put("관악구", 495620);
        populationData.put("서초구", 413076);
        populationData.put("강남구", 563215);
        populationData.put("송파구", 656310);
        populationData.put("강동구", 451474);

        String currentGu = null;
        if (data.getAddress() != null) {
            currentGu = extractGu(data.getAddress().getRoadAddress());
        }

        int population = populationData.getOrDefault(currentGu, 400000);

        Map<String, Double> categoryScores = new HashMap<>();
        Map<String, String> categoryNames = createCategoryNameMap();

        for (AmenityDetailDto amenity : data.getAmenityDetails()) {
            double categoryScore = 0;
            String code = amenity.getCategoryCode();
            int count = amenity.getCount();
            int closestDistance = amenity.getClosestDistance();

            switch (code) {
                case "CS2":
                    categoryScore = Math.min((count / (population / 10000.0)) * 10, 100);
                    break;
                case "CE7":
                    categoryScore = Math.min(count * 2, 100);
                    break;
                case "FD6":
                    categoryScore = Math.min(count * 1, 100);
                    break;
                case "SW8":
                    if (closestDistance <= 500) {
                        categoryScore = 100;
                    } else if (closestDistance <= 1000) {
                        categoryScore = 100 - ((closestDistance - 500) / 500.0 * 50);
                    } else {
                        categoryScore = Math.max(0, 50 - ((closestDistance - 1000) / 1000.0 * 50));
                    }
                    break;
                case "MT1":
                    categoryScore = Math.min(count * 10, 100);
                    break;
                case "BK9":
                    categoryScore = Math.min(count * 5, 100);
                    break;
                case "PM9":
                    categoryScore = Math.min(count * 8, 100);
                    break;
                case "HP8":
                    double distanceScore = closestDistance <= 1000 ? 50 : 25;
                    double countScore = Math.min(count * 10, 50);
                    categoryScore = distanceScore + countScore;
                    break;
                case "PO3":
                    categoryScore = Math.min(count * 3, 100);
                    break;
                case "CT1":
                    categoryScore = Math.min(count * 4, 100);
                    break;
                case "PK6":
                    categoryScore = Math.min(count * 3, 100);
                    break;
                case "OL7":
                    categoryScore = Math.min(count * 5, 100);
                    break;
                case "SC4":
                    categoryScore = Math.min(count * 2, 100);
                    break;
                case "AC5":
                    categoryScore = Math.min(count * 1, 100);
                    break;
                case "AT4":
                    categoryScore = Math.min(count * 3, 100);
                    break;
                default:
                    categoryScore = Math.min(count * 2, 100);
            }

            categoryScores.put(code, categoryScore);
        }

        return R06ConvenienceScoreResult.builder()
                .categoryScores(categoryScores)
                .categoryNames(categoryNames)
                .currentGu(currentGu)
                .guPopulation(population)
                .finalScore(finalScore)
                .isSuccess(true)
                .build();
    }

    /**
     * R-07: 최종 응답 생성 및 캐싱
     *
     * 모든 처리 결과를 LocationAnalysisResponseDTO로 변환하고 캐싱한다.
     *
     * @param request 원본 요청 (좌표 정보용)
     * @param integratedResult 통합된 데이터
     * @param scoringResult 계산된 점수
     * @param centerGeohashId 중심 격자 ID (캐싱용)
     * @return 최종 응답 DTO
     */
    private LocationAnalysisResponseDTO buildFinalResponse(
            LocationAnalysisRequestDTO request,
            IntegratedDataResult integratedResult,
            ScoringResult scoringResult,
            String centerGeohashId) {

        PerformanceLogger perfLogger = PerformanceLogger.start(
                "R-07",
                "buildFinalResponse",
                "Service",
                "LocationAnalysisServiceImpl",
                "buildFinalResponse"
        );

        log.info("[R-07] 최종 응답 생성 시작");

        LocationAnalysisResponseDTO response = new LocationAnalysisResponseDTO();

        // R-07 메인 로깅 DTO 초기화
        R07ResponseResult r07ResponseResult = R07ResponseResult.builder()
                .analysisStatus(null)
                .hasAddress(false)
                .hasSafetyScore(false)
                .hasConvenienceScore(false)
                .recommendations(null)
                .warnings(null)
                .cacheWrite(null)
                .responseSizeBytes(0)
                .isSuccess(false)
                .errorMessage(null)
                .build();

        try {
            // 분석 상태 설정
            response.setAnalysisStatus("SUCCESS");
            r07ResponseResult.setAnalysisStatus("SUCCESS");

            // 좌표 정보 설정
            CoordinateDto coordinate = new CoordinateDto();
            coordinate.setLatitude(request.getLatitude());
            coordinate.setLongitude(request.getLongitude());
            response.setCoordinate(coordinate);

            // 주소 정보 설정
            response.setAddress(integratedResult.getAddress());
            r07ResponseResult.setHasAddress(integratedResult.getAddress() != null);

            // 안전성 점수 정보 설정
            SafetyScoreDto safetyScore = new SafetyScoreDto();
            safetyScore.setTotal((int) Math.round(scoringResult.getSafetyScore()));

            log.info("[R-07] nearestPolice: {}", integratedResult.getNearestPolice());
            log.info("[R-07] policeDistance: {}", integratedResult.getDistanceToNearestPolice());

            // 파출소 거리 및 상세 정보 설정
            if (integratedResult.getNearestPolice() != null) {
                safetyScore.setPoliceDistance((int) Math.round(integratedResult.getDistanceToNearestPolice()));

                PoliceOfficeDto policeDto = new PoliceOfficeDto();
                policeDto.setAddress(integratedResult.getNearestPolice().getAddress());
                policeDto.setLatitude(integratedResult.getNearestPolice().getLatitude());
                policeDto.setLongitude(integratedResult.getNearestPolice().getLongitude());
                policeDto.setDistance((int) Math.round(integratedResult.getDistanceToNearestPolice()));

                safetyScore.setNearestPoliceOffice(policeDto);

            } else {
                safetyScore.setPoliceDistance(null);
                safetyScore.setNearestPoliceOffice(null);
            }

            // CCTV 정보
            safetyScore.setCctvCount(integratedResult.getFilteredCctvList().size());

            // CCTV 상세 리스트 변환
            List<CctvDetailDto> cctvDetailList = new ArrayList<>();

            for (CctvGeo cctv : integratedResult.getFilteredCctvList()) {

                CctvDetailDto cctvDetail = new CctvDetailDto();
                cctvDetail.setAddress(cctv.getAddress());
                cctvDetail.setLatitude(cctv.getLatitude());
                cctvDetail.setLongitude(cctv.getLongitude());
                cctvDetail.setCameraCount(cctv.getCameraCount());
                cctvDetailList.add(cctvDetail);
            }
            safetyScore.setCctvList(cctvDetailList);

            // 검거율
            safetyScore.setArrestRate(integratedResult.getArrestRate());

            response.setSafetyScore(safetyScore);
            r07ResponseResult.setHasSafetyScore(true);

            // 편의성 점수 정보 설정
            ConvenienceScoreDto convenienceScore = new ConvenienceScoreDto();
            convenienceScore.setTotal((int) Math.round(scoringResult.getConvenienceScore()));
            convenienceScore.setAmenityDetails(convertToAmenityDetailDtos(integratedResult.getAmenityDetails()));
            response.setConvenienceScore(convenienceScore);
            r07ResponseResult.setHasConvenienceScore(true);

            // 종합 점수 설정
            response.setOverallScore((int) Math.round(scoringResult.getTotalScore()));

            // 추천사항 생성
            List<String> recommendations = generateRecommendations(scoringResult, integratedResult);
            response.setRecommendations(recommendations);
            r07ResponseResult.setRecommendations(recommendations);

            // 경고사항 생성
            List<String> warnings = generateWarnings(scoringResult, integratedResult);
            response.setWarnings(warnings);
            r07ResponseResult.setWarnings(warnings);

            // 1단계 캐시에 저장 (TTL: 5분)
            R07CacheWriteResult cacheWriteResult = R07CacheWriteResult.builder()
                    .cacheKey(null)
                    .dataSize(0)
                    .ttlSeconds(0)
                    .isSuccess(false)
                    .errorMessage(null)
                    .build();

            try {
                String cacheKey = "dto:" + centerGeohashId;
                String jsonData = objectMapper.writeValueAsString(response);
                int dataSize = jsonData.getBytes().length;

                redisSingleDataService.setSingleData(cacheKey, jsonData, LEVEL1_CACHE_TTL);

                log.info("[R-07] 최종 응답 캐싱 완료 - Key: {}", cacheKey);

                // 캐시 쓰기 성공
                cacheWriteResult.setCacheKey(cacheKey);
                cacheWriteResult.setDataSize(dataSize);
                cacheWriteResult.setTtlSeconds(LEVEL1_CACHE_TTL.getSeconds());
                cacheWriteResult.setSuccess(true);
                cacheWriteResult.setErrorMessage(null);

                // 응답 크기 설정
                r07ResponseResult.setResponseSizeBytes(dataSize);

            } catch (Exception e) {
                log.warn("[R-07] 최종 응답 캐싱 실패: {}", e.getMessage());

                // 캐시 쓰기 실패
                cacheWriteResult.setSuccess(false);
                cacheWriteResult.setErrorMessage(e.getMessage());
            }

            r07ResponseResult.setCacheWrite(cacheWriteResult);

            log.info("[R-07] 최종 응답 생성 완료");

            // R-07 메인 로깅 DTO 최종 설정
            r07ResponseResult.setSuccess(true);
            r07ResponseResult.setErrorMessage(null);

        } catch (Exception e) {
            log.error("[R-07] 최종 응답 생성 중 오류 발생", e);

            r07ResponseResult.setSuccess(false);
            r07ResponseResult.setErrorMessage(e.getMessage());
        }

        perfLogger.setResultData(r07ResponseResult);
        perfLogger.end();

        return response;
    }

    /**
     * 점수 기반 추천사항 생성
     */
    private List<String> generateRecommendations(ScoringResult scores, IntegratedDataResult data) {
        List<String> recommendations = new ArrayList<>();

        if (scores.getSafetyScore() >= 80) {
            recommendations.add("이 지역은 안전 인프라가 잘 갖춰져 있습니다.");
        }

        if (scores.getConvenienceScore() >= 80) {
            recommendations.add("주변에 다양한 편의시설이 있어 생활이 편리합니다.");
        }

        // 가장 가까운 편의시설 추천
        if (data.getAmenityDetails() != null && !data.getAmenityDetails().isEmpty()) {
            AmenityDetailDto closest = data.getAmenityDetails().stream()
                    .min((a, b) -> Integer.compare(a.getClosestDistance(), b.getClosestDistance()))
                    .orElse(null);

            if (closest != null && closest.getClosestDistance() < 200) {
                recommendations.add(String.format("%s이(가) %dm 내에 있습니다.",
                        closest.getCategoryName(), closest.getClosestDistance()));
            }
        }

        return recommendations;
    }

    /**
     * 점수 기반 경고사항 생성
     */
    private List<String> generateWarnings(ScoringResult scores, IntegratedDataResult data) {
        List<String> warnings = new ArrayList<>();

        if (scores.getSafetyScore() < 40) {
            warnings.add("안전 인프라가 부족한 지역입니다. 주의가 필요합니다.");
        }

        if (data.getNearestPolice() != null && data.getDistanceToNearestPolice() > 1000) {
            warnings.add("가장 가까운 파출소가 1km 이상 떨어져 있습니다.");
        }

        if (data.getTotalCameraCount() < 10) {
            warnings.add("CCTV 설치 대수가 적은 편입니다.");
        }

        if (data.getArrestRate() < 0.5) {
            warnings.add("해당 지역의 검거율이 낮은 편입니다.");
        }

        return warnings;
    }


    // ========================================
    // 내부 클래스들 (PlaceDto, AmenityDetailDto, IntegratedDataResult)
    // ========================================

    /**
     * 개별 장소 정보 DTO (3.4.7절)
     */
    private static class PlaceDto {
        private String name;
        private double latitude;
        private double longitude;
        private int distance;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public double getLatitude() {
            return latitude;
        }

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }

        public int getDistance() {
            return distance;
        }

        public void setDistance(int distance) {
            this.distance = distance;
        }
    }

    /**
     * 편의시설 카테고리별 상세 정보 DTO (3.4.6절)
     */
    private static class AmenityDetailDto {
        private String categoryCode;
        private String categoryName;
        private int count;
        private int closestDistance;
        private List<PlaceDto> places;

        public String getCategoryCode() {
            return categoryCode;
        }

        public void setCategoryCode(String categoryCode) {
            this.categoryCode = categoryCode;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public void setCategoryName(String categoryName) {
            this.categoryName = categoryName;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public int getClosestDistance() {
            return closestDistance;
        }

        public void setClosestDistance(int closestDistance) {
            this.closestDistance = closestDistance;
        }

        public List<PlaceDto> getPlaces() {
            return places;
        }

        public void setPlaces(List<PlaceDto> places) {
            this.places = places;
        }
    }

    /**
     * 통합 및 필터링된 데이터 결과를 담는 내부 클래스
     */
    private static class IntegratedDataResult {

        private List<CctvGeo> filteredCctvList = new ArrayList<>();
        private int totalCameraCount = 0;
        private PoliceOfficeGeo nearestPolice;
        private double distanceToNearestPolice = 0.0;
        private AddressDto address;
        private double arrestRate = 0.0;

        /**
         * 편의시설 카테고리별 상세 정보 리스트
         * List<AmenityDetailDto> 형태 (설계 명세서 3.4.6절)
         */
        private List<AmenityDetailDto> amenityDetails = new ArrayList<>();

        public List<CctvGeo> getFilteredCctvList() {
            return filteredCctvList;
        }

        public void setFilteredCctvList(List<CctvGeo> filteredCctvList) {
            this.filteredCctvList = filteredCctvList;
        }

        public int getTotalCameraCount() {
            return totalCameraCount;
        }

        public void setTotalCameraCount(int totalCameraCount) {
            this.totalCameraCount = totalCameraCount;
        }

        public PoliceOfficeGeo getNearestPolice() {
            return nearestPolice;
        }

        public void setNearestPolice(PoliceOfficeGeo nearestPolice) {
            this.nearestPolice = nearestPolice;
        }

        public double getDistanceToNearestPolice() {
            return distanceToNearestPolice;
        }

        public void setDistanceToNearestPolice(double distanceToNearestPolice) {
            this.distanceToNearestPolice = distanceToNearestPolice;
        }

        public AddressDto getAddress() {
            return address;
        }

        public void setAddress(AddressDto address) {
            this.address = address;
        }

        public double getArrestRate() {
            return arrestRate;
        }

        public void setArrestRate(double arrestRate) {
            this.arrestRate = arrestRate;
        }

        public List<AmenityDetailDto> getAmenityDetails() {
            return amenityDetails;
        }

        public void setAmenityDetails(List<AmenityDetailDto> amenityDetails) {
            this.amenityDetails = amenityDetails;
        }
    }

    /**
     * 주소 문자열에서 '구' 이름 추출
     *
     * 도로명 주소를 공백 기준으로 분리하여 "구"로 끝나는 행정구역 단위를 찾는다.
     * 검거율 데이터는 '구' 단위로 저장되어 있으므로 주소에서 구를 추출해야 한다.
     *
     * @param address 전체 주소 문자열 (예: "서울특별시 중구 세종대로 110")
     * @return 추출된 '구' 이름 (예: "중구"), 추출 실패 시 null
     *
     * @see #performExternalApiCalls(LocationAnalysisRequestDTO)
     */
    private String extractGu(String address) {
        if (address == null || address.isEmpty()) {
            return null;
        }

        // 주소를 공백 기준으로 분리 (예: ["서울특별시", "중구", "세종대로", "110"])
        String[] parts = address.split(" ");

        // 배열을 순회하며 "구"로 끝나는 단어 찾기 (행정구역 단위)
        for (String part : parts) {
            if (part.endsWith("구")) {
                return part;
            }
        }

        return null;
    }

    /**
     * 리스트를 geohash_id별로 그룹화하는 제네릭 헬퍼 메서드
     *
     * 현재는 미사용 상태지만, 향후 다른 엔티티 타입의 그룹화가 필요할 경우
     * 코드 중복을 방지하기 위한 범용 유틸리티 메서드로 설계됨
     *
     * @param <T> 그룹화할 엔티티 타입
     * @param list 그룹화할 엔티티 리스트
     * @param geohashExtractor 엔티티에서 geohash_id를 추출하는 Function (람다 표현식)
     * @return 격자 ID를 키로 하는 Map
     */
    private <T> Map<String, List<T>> groupByGeohash(List<T> list,
                                                    java.util.function.Function<T, String> geohashExtractor) {
        Map<String, List<T>> grouped = new HashMap<>();

        for (T item : list) {
            String geohashId = geohashExtractor.apply(item);
            grouped.computeIfAbsent(geohashId, k -> new ArrayList<>()).add(item);
        }

        return grouped;
    }

    /**
     * 격자별 데이터를 Redis 2단계 캐시에 저장하는 헬퍼 메서드
     *
     * DB에서 조회한 격자별 데이터를 JSON 직렬화하여 Redis에 저장한다.
     * 캐시 키는 "data:{geohashId}:{dataType}" 형식으로 생성되며,
     * TTL은 24시간으로 설정된다 (공공 데이터의 변경 주기 고려)
     *
     * @param geohashId 격자 ID (예: "wydm7p1")
     * @param dataType 데이터 타입 (예: "cctv", "police")
     * @param data 캐싱할 데이터 객체 (List<CctvGeo> 또는 List<PoliceOfficeGeo>)
     *
     * @see #performDatabaseQuery(CacheResult)
     */
    private void cacheGeohashData(String geohashId, String dataType, Object data) {
        try {
            String cacheKey = "data:" + geohashId + ":" + dataType;
            String jsonData = objectMapper.writeValueAsString(data);
            redisSingleDataService.setSingleData(cacheKey, jsonData, LEVEL2_CACHE_TTL);
            log.debug("[R-03] 캐싱 성공 - Key: {}", cacheKey);
        } catch (Exception e) {
            log.warn("[R-03] 캐싱 실패 - GeohashId: {}, DataType: {}, 오류: {}",
                    geohashId, dataType, e.getMessage());
        }
    }

    // ========== 내부 클래스 (Inner Classes) ==========

    /**
     * 점수 계산 결과를 담는 내부 클래스
     *
     * R-06 단계에서 계산된 각종 점수를 저장한다.
     */
    private static class ScoringResult {
        private double safetyScore;
        private double convenienceScore;
        private double totalScore;

        public double getSafetyScore() {
            return safetyScore;
        }

        public void setSafetyScore(double safetyScore) {
            this.safetyScore = safetyScore;
        }

        public double getConvenienceScore() {
            return convenienceScore;
        }

        public void setConvenienceScore(double convenienceScore) {
            this.convenienceScore = convenienceScore;
        }

        public double getTotalScore() {
            return totalScore;
        }

        public void setTotalScore(double totalScore) {
            this.totalScore = totalScore;
        }
    }

    /**
     * 외부 API 호출 결과를 담는 내부 클래스
     *
     * R-04 단계에서 수행되는 모든 외부 API 호출 결과를 통합 관리한다.
     * 각 API 호출은 독립적으로 실행되며, 실패 시 오류 메시지만 errors 리스트에 추가되고
     * 다른 API 호출은 영향받지 않는다 (Fault Isolation)
     *
     * @see #performExternalApiCalls(LocationAnalysisRequestDTO)
     */
    private static class ExternalApiResult {

        // 카카오맵 Reverse Geocoding API 응답 (도로명 + 지번 주소)
        private AddressDto address;

        // 카카오맵 로컬 검색 API 응답 (15개 카테고리별 편의시설 목록)
        // Map<카테고리코드, List<장소정보>> 형태 (예: "CS2" -> [GS25, CU, ...])
        private Map<String, List<Map<String, Object>>> amenityData;

        // 내부 DB 조회 결과 (해당 구의 검거율, 0.0~1.0 범위)
        private double arrestRate;

        // API 호출 중 발생한 오류 메시지 목록 (장애 격리를 위한 오류 수집)
        private final List<String> errors = new ArrayList<>();

        public AddressDto getAddress() {
            return address;
        }

        public void setAddress(AddressDto address) {
            this.address = address;
        }

        public Map<String, List<Map<String, Object>>> getAmenityData() {
            return amenityData;
        }

        public void setAmenityData(Map<String, List<Map<String, Object>>> amenityData) {
            this.amenityData = amenityData;
        }

        public double getArrestRate() {
            return arrestRate;
        }

        public void setArrestRate(double arrestRate) {
            this.arrestRate = arrestRate;
        }

        public List<String> getErrors() {
            return errors;
        }

        public void addError(String error) {
            errors.add(error);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    /**
     * DB 조회 결과를 담는 내부 클래스
     *
     * R-03 단계에서 수행되는 CCTV/파출소 DB 조회 결과를 통합 관리한다.
     * 캐시 히트된 데이터와 새로 조회한 데이터가 모두 이 객체에 통합되어
     * 다음 단계(R-05)에서 일관된 인터페이스로 접근 가능하다.
     *
     * @see #performDatabaseQuery(CacheResult)
     */
    private static class DatabaseQueryResult {

        // 격자별 CCTV 데이터 (캐시 히트 + DB 조회 결과 통합)
        // Map<GeohashId, List<CctvGeo>> 형태 (예: "wydm7p1" -> [CCTV1, CCTV2, ...])
        private final Map<String, List<CctvGeo>> cctvData = new HashMap<>();

        // 격자별 파출소 데이터 (캐시 히트 + DB 조회 결과 통합)
        // Map<GeohashId, List<PoliceOfficeGeo>> 형태 (예: "wydm7p1" -> [파출소1, 파출소2, ...])
        private final Map<String, List<PoliceOfficeGeo>> policeData = new HashMap<>();

        // DB 조회 중 발생한 오류 메시지 목록
        private final List<String> errors = new ArrayList<>();

        public Map<String, List<CctvGeo>> getCctvData() {
            return cctvData;
        }

        public Map<String, List<PoliceOfficeGeo>> getPoliceData() {
            return policeData;
        }

        public List<String> getErrors() {
            return errors;
        }

        public void addError(String error) {
            errors.add(error);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    /**
     * 캐시 조회 결과를 담는 내부 클래스
     *
     * R-02 단계의 2단계 캐싱 전략 결과를 관리한다.
     * 1단계 캐시(전체 DTO) 히트 여부와 2단계 캐시(격자별 컴포넌트) 히트/미스 상태를
     * 모두 추적하여 R-03 단계에서 선택적 DB 조회를 가능하게 한다.
     *
     * @see #performCacheLookup(List, LocationAnalysisRequestDTO)
     */
    private static class CacheResult {

        // 1단계 캐시(전체 DTO) 히트 여부 플래그
        private boolean level1Hit = false;

        // 1단계 캐시 히트 시 반환할 최종 응답 DTO 객체
        private LocationAnalysisResponseDTO cachedResponse;

        // R-01에서 계산된 9개 격자 ID 목록 (중심 1개 + 인접 8개)
        private List<String> nineBlockGeohashes;

        // 2단계 캐시: 격자별로 캐시 히트된 CCTV 데이터
        // Map<GeohashId, List<CctvGeo>> 형태
        private final Map<String, List<CctvGeo>> cachedCctvData = new HashMap<>();

        // 2단계 캐시: 격자별로 캐시 히트된 파출소 데이터
        // Map<GeohashId, List<PoliceOfficeGeo>> 형태
        private final Map<String, List<PoliceOfficeGeo>> cachedPoliceData = new HashMap<>();

        // 2단계 캐시 미스: CCTV 데이터 DB 조회가 필요한 격자 ID 목록
        private final List<String> cctvMisses = new ArrayList<>();

        // 2단계 캐시 미스: 파출소 데이터 DB 조회가 필요한 격자 ID 목록
        private final List<String> policeMisses = new ArrayList<>();

        /**
         * 1단계 캐시 히트 시 사용하는 정적 팩토리 메서드
         *
         * @param dto 캐시에서 조회된 최종 응답 DTO
         * @return 1단계 히트 상태의 CacheResult 객체
         */
        public static CacheResult level1Hit(LocationAnalysisResponseDTO dto) {
            CacheResult result = new CacheResult();
            result.level1Hit = true;
            result.cachedResponse = dto;
            return result;
        }

        public boolean isLevel1Hit() {
            return level1Hit;
        }

        public void setLevel1Hit(boolean level1Hit) {
            this.level1Hit = level1Hit;
        }

        public LocationAnalysisResponseDTO getCachedResponse() {
            return cachedResponse;
        }

        public List<String> getNineBlockGeohashes() {
            return nineBlockGeohashes;
        }

        public void setNineBlockGeohashes(List<String> nineBlockGeohashes) {
            this.nineBlockGeohashes = nineBlockGeohashes;
        }

        public void addCachedCctv(String geohashId, List<CctvGeo> data) {
            cachedCctvData.put(geohashId, data);
        }

        public void addCachedPolice(String geohashId, List<PoliceOfficeGeo> data) {
            cachedPoliceData.put(geohashId, data);
        }

        public void addCctvMiss(String geohashId) {
            cctvMisses.add(geohashId);
        }

        public void addPoliceMiss(String geohashId) {
            policeMisses.add(geohashId);
        }

        public Map<String, List<CctvGeo>> getCachedCctvData() {
            return cachedCctvData;
        }

        public Map<String, List<PoliceOfficeGeo>> getCachedPoliceData() {
            return cachedPoliceData;
        }

        public List<String> getCctvMisses() {
            return cctvMisses;
        }

        public List<String> getPoliceMisses() {
            return policeMisses;
        }
    }

    private List<com.wherehouse.information.model.AmenityDetailDto>
    convertToAmenityDetailDtos(List<AmenityDetailDto> internalList) {

        List<com.wherehouse.information.model.AmenityDetailDto> result = new ArrayList<>();

        for (AmenityDetailDto internal : internalList) {
            com.wherehouse.information.model.AmenityDetailDto dto =
                    com.wherehouse.information.model.AmenityDetailDto.builder()
                            .categoryCode(internal.getCategoryCode())
                            .categoryName(internal.getCategoryName())
                            .count(internal.getCount())
                            .closestDistance(internal.getClosestDistance())
                            .places(convertToPlaceDtos(internal.getPlaces()))
                            .build();
            result.add(dto);
        }
        return result;
    }

    private List<com.wherehouse.information.model.PlaceDto>
    convertToPlaceDtos(List<PlaceDto> internalList) {

        List<com.wherehouse.information.model.PlaceDto> result = new ArrayList<>();

        for (PlaceDto internal : internalList) {
            com.wherehouse.information.model.PlaceDto dto =
                    com.wherehouse.information.model.PlaceDto.builder()
                            .name(internal.getName())
                            .latitude(internal.getLatitude())
                            .longitude(internal.getLongitude())
                            .distance(internal.getDistance())
                            .build();
            result.add(dto);
        }
        return result;
    }

    /* 최초로 상세지도 페이지 로드 시 모든 파출소 좌표 정보 가져오기. */
    @Override
    public List<PoliceOfficeResponseDTO> getAllPoliceOffices() {
        log.info("[전체 파출소 조회] 시작");

        // 모든 파출소 데이터 조회
        List<PoliceOfficeGeo> allPoliceOffices = policeOfficeGeoRepository.findAllBySeoul();

        // Entity를 DTO로 변환
        List<PoliceOfficeResponseDTO> responseDTOs = allPoliceOffices.stream()
                .map(police -> PoliceOfficeResponseDTO.builder()
                        .address(police.getAddress())
                        .latitude(police.getLatitude())
                        .longitude(police.getLongitude())
                        .build())
                .collect(Collectors.toList());

        log.info("[전체 파출소 조회] 완료 - 조회된 파출소: {}개", responseDTOs.size());

        return responseDTOs;
    }
}