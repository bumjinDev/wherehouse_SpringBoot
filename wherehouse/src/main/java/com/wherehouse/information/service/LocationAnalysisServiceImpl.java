package com.wherehouse.information.service;

import com.wherehouse.information.dao.ArrestRateRepository;
import com.wherehouse.information.dao.CctvGeoRepository;
import com.wherehouse.information.dao.PoliceOfficeGeoRepository;
import com.wherehouse.information.entity.ArrestRate;
import com.wherehouse.information.entity.CctvGeo;
import com.wherehouse.information.entity.PoliceOfficeGeo;
import com.wherehouse.information.model.*;
import com.wherehouse.information.util.GeohashService;
import com.wherehouse.information.util.KakaoApiService;
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
import java.util.concurrent.CompletableFuture;

/**
 * 위치 분석 서비스 구현체
 *
 * 사용자가 선택한 좌표 기반으로 주변 안전 인프라(CCTV, 파출소)와 편의시설을 분석하여
 * 종합 안전 점수 및 편의성 점수를 제공하는 핵심 비즈니스 로직 구현
 *
 * 처리 단계 (6.4.4 실시간 서비스 처리 단계)
 * - R-01: '9-Block' 그리드 범위 계산 ✅ 구현 완료
 * - R-02: 단계별 캐시 조회 ✅ 구현 완료
 * - R-03: 선택된 데이터베이스 조회 ✅ 구현 완료
 * - R-04: 외부 API 호출 및 개별 데이터 캐싱 ✅ 구현 완료
 * - R-05: 데이터 통합, 필터링, 최종 응답 생성 대기 ⏳ 개발 예정
 * - R-06: 최종 점수 계산 ⏳ 개발 예정
 * - R-07: 최종 응답 생성 및 캐싱 ⏳ 개발 예정
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
        log.info("[R-01] 9-Block 그리드 범위 계산 시작");
        log.info("[R-01] 요청 좌표: lat={}, lon={}",
                request.getLatitude(), request.getLongitude());

        double latitude = request.getLatitude();
        double longitude = request.getLongitude();

        // GeohashService를 통해 중심 격자 + 인접 8개 격자 = 총 9개 격자 ID 생성
        List<String> nineBlockIds = geohashService.calculate9BlockGeohashes(latitude, longitude);

        log.info("[R-01] 계산된 9-Block 그리드: {}", nineBlockIds);
        log.info("[R-01] 중심 격자 ID: {}", nineBlockIds.get(0));
        log.info("[R-01] 9-Block 그리드 범위 계산 완료");

        return nineBlockIds;
    }

    /**
     * R-02: 단계별 캐시 조회
     *
     * 2단계 캐싱 전략을 통해 응답 속도를 최적화한다.
     * 1단계 캐시 히트 시 전체 프로세스를 즉시 종료하며, 미스 시 2단계 캐시로 넘어가
     * 격자별 컴포넌트 데이터를 부분적으로 재사용한다.
     *
     * 캐싱 전략 계층
     * 1. 1단계 (Response Cache): 최종 응답 DTO 전체를 중심 격자 ID 기준으로 캐싱
     *    - TTL: 5분 (동일 지역 반복 요청 패턴 대응)
     *    - 키 형식: "dto:{centerGeohashId}"
     *    - 히트 시: 즉시 반환, 전체 프로세스 스킵
     * 2. 2단계 (Component Cache): 격자별 CCTV/파출소 원본 데이터 캐싱
     *    - TTL: 24시간 (공공 데이터 변경 주기 고려)
     *    - 키 형식: "data:{geohashId}:{dataType}"
     *    - 히트: 캐시된 데이터 재사용, 미스: DB 조회(R-03)로 전환
     *
     * 데이터 직렬화
     * - Jackson ObjectMapper를 사용한 JSON 직렬화/역직렬화
     * - Redis에 문자열 형태로 저장 (RedisSingleDataService 활용)
     *
     * @param nineBlockGeohashes R-01에서 계산된 9개 격자 ID 목록
     * @param request 사용자 요청 DTO (현재 단계에서는 미사용, 향후 확장 대비)
     * @return 캐시 조회 결과 객체 (히트 여부, 캐시된 데이터, 미스 격자 목록 포함)
     */
    private CacheResult performCacheLookup(List<String> nineBlockGeohashes,
                                           LocationAnalysisRequestDTO request) {
        log.info("[R-02] 단계별 캐시 조회 시작");

        String centerGeohashId = nineBlockGeohashes.get(0);
        String level1CacheKey = "dto:" + centerGeohashId;

        // 1단계 캐시 조회: 중심 격자 ID를 키로 최종 응답 DTO 전체 조회 시도
        log.info("[R-02-1단계] 전체 DTO 캐시 조회 - Key: {}", level1CacheKey);

        try {
            String cachedJson = redisSingleDataService.getSingleData(level1CacheKey);

            if (cachedJson != null && !cachedJson.isEmpty()) {
                log.info("[R-02-1단계] 캐시 히트! JSON 역직렬화 시도");
                LocationAnalysisResponseDTO cachedDto = objectMapper.readValue(
                        cachedJson,
                        LocationAnalysisResponseDTO.class
                );
                log.info("[R-02-1단계] 캐시 히트 성공! 즉시 반환");
                return CacheResult.level1Hit(cachedDto);
            }
        } catch (Exception e) {
            log.warn("[R-02-1단계] 캐시 조회 중 오류 발생: {}", e.getMessage());
        }

        log.info("[R-02-1단계] 캐시 미스. 2단계 캐시 조회 진행");

        // 2단계 캐시 조회: 9개 격자 각각에 대해 CCTV/파출소 컴포넌트 데이터 조회
        log.info("[R-02-2단계] 개별 격자 데이터 캐시 조회 시작 (9개 격자)");

        CacheResult result = new CacheResult();
        result.setLevel1Hit(false);
        result.setNineBlockGeohashes(nineBlockGeohashes);

        // 각 격자별로 CCTV, 파출소 데이터의 캐시 존재 여부 확인
        for (String geohashId : nineBlockGeohashes) {
            // CCTV 데이터 캐시 키 생성 및 조회 (형식: "data:{geohashId}:cctv")
            String cctvCacheKey = "data:" + geohashId + ":cctv";
            log.debug("[R-02-2단계] CCTV 캐시 조회 - Key: {}", cctvCacheKey);

            try {
                String cctvJson = redisSingleDataService.getSingleData(cctvCacheKey);

                if (cctvJson != null && !cctvJson.isEmpty()) {
                    List<CctvGeo> cachedCctv = objectMapper.readValue(
                            cctvJson,
                            new TypeReference<List<CctvGeo>>() {}
                    );
                    log.debug("[R-02-2단계] CCTV 캐시 히트 - GeohashId: {}, 개수: {}",
                            geohashId, cachedCctv.size());
                    result.addCachedCctv(geohashId, cachedCctv);
                } else {
                    log.debug("[R-02-2단계] CCTV 캐시 미스 - GeohashId: {}", geohashId);
                    result.addCctvMiss(geohashId);
                }
            } catch (Exception e) {
                log.warn("[R-02-2단계] CCTV 캐시 조회 중 오류 - GeohashId: {}, 오류: {}",
                        geohashId, e.getMessage());
                result.addCctvMiss(geohashId);
            }

            // 파출소 데이터 캐시 키 생성 및 조회 (형식: "data:{geohashId}:police")
            String policeCacheKey = "data:" + geohashId + ":police";
            log.debug("[R-02-2단계] 파출소 캐시 조회 - Key: {}", policeCacheKey);

            try {
                String policeJson = redisSingleDataService.getSingleData(policeCacheKey);

                if (policeJson != null && !policeJson.isEmpty()) {
                    List<PoliceOfficeGeo> cachedPolice = objectMapper.readValue(
                            policeJson,
                            new TypeReference<List<PoliceOfficeGeo>>() {}
                    );
                    log.debug("[R-02-2단계] 파출소 캐시 히트 - GeohashId: {}, 개수: {}",
                            geohashId, cachedPolice.size());
                    result.addCachedPolice(geohashId, cachedPolice);
                } else {
                    log.debug("[R-02-2단계] 파출소 캐시 미스 - GeohashId: {}", geohashId);
                    result.addPoliceMiss(geohashId);
                }
            } catch (Exception e) {
                log.warn("[R-02-2단계] 파출소 캐시 조회 중 오류 - GeohashId: {}, 오류: {}",
                        geohashId, e.getMessage());
                result.addPoliceMiss(geohashId);
            }
        }

        log.info("[R-02-2단계] 개별 격자 데이터 캐시 조회 완료");
        log.info("[R-02-2단계] CCTV 캐시 히트: {}개, 미스: {}개",
                result.getCachedCctvData().size(), result.getCctvMisses().size());
        log.info("[R-02-2단계] 파출소 캐시 히트: {}개, 미스: {}개",
                result.getCachedPoliceData().size(), result.getPoliceMisses().size());
        log.info("[R-02] 단계별 캐시 조회 완료");

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
     * 3. 조회된 데이터를 격자별로 그룹화 (groupCctvByGeohash, groupPoliceByGeohash)
     * 4. 각 격자 데이터를 즉시 Redis 2단계 캐시에 저장 (TTL: 24시간)
     *
     * 인덱스 활용 전략
     * - 쿼리 패턴: WHERE geohash_id IN (...)
     * - 사용 인덱스: IDX_CCTV_GEO_GEOHASH, IDX_POLICE_GEO_GEOHASH (B-Tree)
     * - 성능: 9개 격자 조회 시 9번의 Index Range Scan (각 밀리초 단위)
     *
     * 설계 근거
     * - 선택적 조회: 캐시 미스 격자만 조회하여 DB 부하 최소화
     * - 즉시 캐싱: 조회 직후 캐싱으로 다음 요청에서 재사용 보장
     * - 전체 컬럼 조회: ADDRESS, LATITUDE, LONGITUDE 등 모든 필드 포함
     *
     * 성능 최적화 여지 (TODO)
     * 현재 병목 구간: CCTV 조회와 파출소 조회가 순차 실행됨
     * - 현재 소요 시간: CCTV(50ms) + 파출소(30ms) = 80ms
     * - 병렬 처리 시: max(CCTV(50ms), 파출소(30ms)) = 50ms (약 37.5% 개선)
     * - 개선 방법: CompletableFuture.supplyAsync()로 두 조회를 별도 스레드에서 실행
     * - 참고: R-04에서 이미 동일한 병렬 처리 패턴 적용 중
     *
     * @param cacheResult R-02에서 반환된 캐시 조회 결과 객체 (히트 데이터 + 미스 목록)
     * @return DB 조회 결과 객체 (캐시 데이터 + 새로 조회한 데이터 통합)
     */
    private DatabaseQueryResult performDatabaseQuery(CacheResult cacheResult) {
        log.info("[R-03] 선택된 데이터베이스 조회 시작");

        DatabaseQueryResult dbResult = new DatabaseQueryResult();

        // 1. R-02에서 캐시 히트된 데이터를 결과 객체에 먼저 추가 (캐시 재사용)
        dbResult.getCctvData().putAll(cacheResult.getCachedCctvData());
        dbResult.getPoliceData().putAll(cacheResult.getCachedPoliceData());

        // 2. CCTV 데이터 DB 조회 (캐시 미스 격자만 대상, IN 절로 일괄 조회)
        // TODO: [병목 구간 1] CompletableFuture.supplyAsync()로 비동기 실행 가능
        List<String> cctvMisses = cacheResult.getCctvMisses();
        if (!cctvMisses.isEmpty()) {
            log.info("[R-03] CCTV 데이터 DB 조회 시작 - 대상 격자: {}개", cctvMisses.size());

            try {
                // B-Tree 인덱스(IDX_CCTV_GEO_GEOHASH)를 활용한 IN 절 조회
                List<CctvGeo> cctvList = cctvGeoRepository.findByGeohashIdIn(cctvMisses);
                log.info("[R-03] CCTV DB 조회 완료 - 조회된 데이터: {}건", cctvList.size());

                // 조회된 CCTV 리스트를 geohash_id 기준으로 그룹화 (Map<GeohashId, List<CctvGeo>>)
                Map<String, List<CctvGeo>> groupedCctv = groupCctvByGeohash(cctvList);

                // 격자별로 분류된 데이터를 순회하며 결과 객체에 추가 + Redis 2단계 캐시 저장
                for (Map.Entry<String, List<CctvGeo>> entry : groupedCctv.entrySet()) {
                    String geohashId = entry.getKey();
                    List<CctvGeo> data = entry.getValue();

                    // DatabaseQueryResult 객체에 해당 격자의 CCTV 데이터 추가
                    dbResult.getCctvData().put(geohashId, data);

                    // Redis 2단계 캐시에 저장 (키: "data:{geohashId}:cctv", TTL: 24시간)
                    cacheGeohashData(geohashId, "cctv", data);
                    log.debug("[R-03] CCTV 캐싱 완료 - GeohashId: {}, 개수: {}건", geohashId, data.size());
                }

            } catch (Exception e) {
                log.error("[R-03] CCTV DB 조회 중 오류 발생", e);
                dbResult.addError("CCTV 데이터 조회 실패: " + e.getMessage());
            }
        } else {
            log.info("[R-03] CCTV 데이터는 모두 캐시에서 조회됨");
        }

        // 3. 파출소 데이터 DB 조회 (캐시 미스 격자만 대상, IN 절로 일괄 조회)
        // TODO: [병목 구간 2] CompletableFuture.supplyAsync()로 CCTV 조회와 병렬 실행 가능
        List<String> policeMisses = cacheResult.getPoliceMisses();
        if (!policeMisses.isEmpty()) {
            log.info("[R-03] 파출소 데이터 DB 조회 시작 - 대상 격자: {}개", policeMisses.size());

            try {
                // B-Tree 인덱스(IDX_POLICE_GEO_GEOHASH)를 활용한 IN 절 조회
                List<PoliceOfficeGeo> policeList = policeOfficeGeoRepository.findByGeohashIdIn(policeMisses);
                log.info("[R-03] 파출소 DB 조회 완료 - 조회된 데이터: {}건", policeList.size());

                // 조회된 파출소 리스트를 geohash_id 기준으로 그룹화 (Map<GeohashId, List<PoliceOfficeGeo>>)
                Map<String, List<PoliceOfficeGeo>> groupedPolice = groupPoliceByGeohash(policeList);

                // 격자별로 분류된 데이터를 순회하며 결과 객체에 추가 + Redis 2단계 캐시 저장
                for (Map.Entry<String, List<PoliceOfficeGeo>> entry : groupedPolice.entrySet()) {
                    String geohashId = entry.getKey();
                    List<PoliceOfficeGeo> data = entry.getValue();

                    // DatabaseQueryResult 객체에 해당 격자의 파출소 데이터 추가
                    dbResult.getPoliceData().put(geohashId, data);

                    // Redis 2단계 캐시에 저장 (키: "data:{geohashId}:police", TTL: 24시간)
                    cacheGeohashData(geohashId, "police", data);
                    log.debug("[R-03] 파출소 캐싱 완료 - GeohashId: {}, 개수: {}건", geohashId, data.size());
                }

            } catch (Exception e) {
                log.error("[R-03] 파출소 DB 조회 중 오류 발생", e);
                dbResult.addError("파출소 데이터 조회 실패: " + e.getMessage());
            }
        } else {
            log.info("[R-03] 파출소 데이터는 모두 캐시에서 조회됨");
        }

        log.info("[R-03] 데이터베이스 조회 완료");
        log.info("[R-03] CCTV 총 격자: {}개, 파출소 총 격자: {}개",
                dbResult.getCctvData().size(), dbResult.getPoliceData().size());

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
     * 모든 외부 호출은 CompletableFuture를 사용한 비동기 병렬 처리로 실행되어
     * 전체 응답 시간을 단일 최장 작업 시간에 수렴시킨다.
     *
     * 병렬 처리 작업 목록
     * 1. 주소 변환 API: 좌표 → 도로명/지번 주소 (카카오맵 Reverse Geocoding)
     * 2. 편의시설 검색 API: 반경 내 15개 카테고리 편의시설 조회 (카카오맵 로컬 검색)
     * 3. 검거율 조회: 주소에서 추출한 '구' 기준 내부 DB 조회 (addressFuture 완료 대기)
     *
     * 캐싱 전략
     * - 주소 변환: 키 "address:{lat}:{lon}", TTL 24시간
     * - 편의시설: 키 "amenity:{lat}:{lon}:{radius}", TTL 24시간
     * - 검거율: 캐싱 없음 (내부 DB 조회, 충분히 빠름)
     *
     * 장애 격리 (Fault Isolation)
     * 각 작업이 독립적으로 실행되므로 일부 API 실패 시에도 전체 프로세스가 중단되지 않는다.
     * 실패한 작업은 ExternalApiResult의 errors 리스트에 기록되며, 성공한 작업의 데이터는 정상 반환된다.
     *
     * 성능 이점
     * - 순차 실행 시: 주소(100ms) + 편의시설(300ms) + 검거율(20ms) = 420ms
     * - 병렬 실행 시: max(100ms, 300ms, 20ms) = 300ms (약 28.6% 개선)
     *
     * @param request 사용자 요청 DTO (위도, 경도, 반경 포함)
     * @return 외부 API 호출 결과 객체 (주소, 편의시설, 검거율 + 오류 목록)
     */
    private ExternalApiResult performExternalApiCalls(LocationAnalysisRequestDTO request) {
        log.info("[R-04] 외부 API 호출 시작");

        double latitude = request.getLatitude();
        double longitude = request.getLongitude();
        int radius = request.getRadius();

        ExternalApiResult result = new ExternalApiResult();

        // 1. 주소 변환 API 비동기 호출 (카카오맵 Reverse Geocoding)
        CompletableFuture<Void> addressFuture = CompletableFuture.runAsync(() -> {
            try {
                log.info("[R-04] 주소 변환 API 호출 시작 - 좌표: ({}, {})", latitude, longitude);

                // 캐시 키 생성 (좌표 기반, 형식: "address:{latitude}:{longitude}")
                String cacheKey = "address:" + latitude + ":" + longitude;

                // Redis 2단계 캐시 조회 시도
                String cachedAddress = redisSingleDataService.getSingleData(cacheKey);

                if (cachedAddress != null && !cachedAddress.isEmpty()) {
                    log.info("[R-04] 주소 변환 캐시 히트");
                    // JSON 역직렬화하여 AddressDto 객체로 변환 후 결과에 설정
                    result.setAddress(objectMapper.readValue(cachedAddress,
                            AddressDto.class));
                } else {
                    log.info("[R-04] 주소 변환 캐시 미스 - API 호출");
                    // 카카오맵 Reverse Geocoding API 호출 (좌표 → 주소 변환)
                    AddressDto addressDto =
                            kakaoApiService.getAddress(latitude, longitude);

                    result.setAddress(addressDto);

                    // API 응답을 JSON 직렬화 후 Redis 2단계 캐시에 저장 (TTL: 24시간)
                    String addressJson = objectMapper.writeValueAsString(addressDto);
                    redisSingleDataService.setSingleData(cacheKey, addressJson, LEVEL2_CACHE_TTL);
                    log.info("[R-04] 주소 변환 결과 캐싱 완료");
                }

            } catch (Exception e) {
                log.error("[R-04] 주소 변환 API 호출 중 오류 발생", e);
                result.addError("주소 변환 실패: " + e.getMessage());
            }
        });

        // 2. 편의시설 조회 API 비동기 호출 (카카오맵 로컬 검색, 15개 카테고리)
        CompletableFuture<Void> amenityFuture = CompletableFuture.runAsync(() -> {
            try {
                log.info("[R-04] 편의시설 조회 API 호출 시작 - 반경: {}m", radius);

                // 캐시 키 생성 (좌표 + 반경 기반, 형식: "amenity:{lat}:{lon}:{radius}")
                String cacheKey = "amenity:" + latitude + ":" + longitude + ":" + radius;

                // Redis 2단계 캐시 조회 시도
                String cachedAmenity = redisSingleDataService.getSingleData(cacheKey);

                if (cachedAmenity != null && !cachedAmenity.isEmpty()) {
                    log.info("[R-04] 편의시설 캐시 히트");
                    // JSON 역직렬화하여 Map<카테고리코드, List<장소>> 형태로 변환 후 결과에 설정
                    result.setAmenityData(objectMapper.readValue(cachedAmenity,
                            new TypeReference<Map<String, List<Map<String, Object>>>>() {}));
                } else {
                    log.info("[R-04] 편의시설 캐시 미스 - API 호출");
                    // 카카오맵 로컬 검색 API 호출 (15개 카테고리를 병렬로 조회)
                    Map<String, List<Map<String, Object>>> amenityData =
                            kakaoApiService.searchAllAmenities(latitude, longitude, radius);

                    result.setAmenityData(amenityData);

                    // API 응답을 JSON 직렬화 후 Redis 2단계 캐시에 저장 (TTL: 24시간)
                    String amenityJson = objectMapper.writeValueAsString(amenityData);
                    redisSingleDataService.setSingleData(cacheKey, amenityJson, LEVEL2_CACHE_TTL);
                    log.info("[R-04] 편의시설 결과 캐싱 완료 - 총 카테고리: {}개", amenityData.size());
                }

            } catch (Exception e) {
                log.error("[R-04] 편의시설 조회 API 호출 중 오류 발생", e);
                result.addError("편의시설 조회 실패: " + e.getMessage());
            }
        });

        // 3. 검거율 조회 비동기 실행 (내부 DB 조회, addressFuture 완료 대기 필요)
        CompletableFuture<Void> arrestRateFuture = CompletableFuture.runAsync(() -> {
            try {
                log.info("[R-04] 검거율 조회 시작");

                // 주소 변환 결과가 필요하므로 addressFuture의 완료를 대기 (동기 블로킹)
                addressFuture.join();

                if (result.getAddress() != null) {
                    String address = result.getAddress().getRoadAddress();

                    // 주소 문자열에서 '구' 단위 추출 (예: "서울특별시 중구 세종대로 110" → "중구")
                    String gu = extractGu(address);

                    if (gu != null) {
                        log.info("[R-04] 추출된 구: {}", gu);

                        // ArrestRate 테이블에서 해당 '구'의 검거율 데이터 조회
                        java.util.Optional<ArrestRate> arrestRateOpt = arrestRateRepository.findByAddr(gu);

                        if (arrestRateOpt.isPresent()) {
                            result.setArrestRate(arrestRateOpt.get().getRate());
                            log.info("[R-04] 검거율 조회 성공 - {}: {}", gu, arrestRateOpt.get().getRate());
                        } else {
                            log.warn("[R-04] 검거율 데이터 없음 - 구: {}", gu);
                            result.setArrestRate(0.0);
                        }
                    } else {
                        log.warn("[R-04] 주소에서 구 추출 실패: {}", address);
                        result.setArrestRate(0.0);
                    }
                } else {
                    log.warn("[R-04] 주소 변환 결과 없음 - 검거율 조회 불가");
                    result.setArrestRate(0.0);
                }

            } catch (Exception e) {
                log.error("[R-04] 검거율 조회 중 오류 발생", e);
                result.addError("검거율 조회 실패: " + e.getMessage());
                result.setArrestRate(0.0);
            }
        });

        // 4. 모든 비동기 작업 완료 대기 (CompletableFuture.allOf()로 통합 대기)
        try {
            log.info("[R-04] 모든 외부 API 호출 완료 대기 중...");
            CompletableFuture.allOf(addressFuture, amenityFuture, arrestRateFuture).join();
            log.info("[R-04] 모든 외부 API 호출 완료");
        } catch (Exception e) {
            log.error("[R-04] 외부 API 호출 대기 중 오류 발생", e);
            result.addError("외부 API 호출 대기 실패: " + e.getMessage());
        }

        // 5. 최종 결과 로깅 (성공/실패 여부 및 데이터 존재 여부 확인)
        log.info("[R-04] 외부 API 호출 결과:");
        log.info("[R-04] - 주소: {}", result.getAddress() != null ? "성공" : "실패");
        log.info("[R-04] - 편의시설: {}", result.getAmenityData() != null ?
                result.getAmenityData().size() + "개 카테고리" : "실패");
        log.info("[R-04] - 검거율: {}", result.getArrestRate());

        if (result.hasErrors()) {
            log.warn("[R-04] 발생한 오류: {}", result.getErrors());
        }

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

        log.info("[R-05] 데이터 통합 및 필터링 시작");

        double userLatitude = request.getLatitude();
        double userLongitude = request.getLongitude();
        int radius = request.getRadius();

        IntegratedDataResult result = new IntegratedDataResult();

        // 1. CCTV 데이터 통합 및 필터링
        log.info("[R-05] CCTV 데이터 통합 및 반경 필터링 시작");
        List<CctvGeo> allCctvList = new ArrayList<>();

        for (String geohashId : nineBlockGeohashes) {
            List<CctvGeo> cctvInGrid = dbResult.getCctvData().get(geohashId);
            if (cctvInGrid != null) {
                allCctvList.addAll(cctvInGrid);
            }
        }

        log.info("[R-05] 9개 격자에서 통합된 전체 CCTV 개수: {}개", allCctvList.size());

        List<CctvGeo> filteredCctvList = new ArrayList<>();
        int totalCameraCount = 0;

        for (CctvGeo cctv : allCctvList) {
            double distance = geohashService.calculateDistance(
                    userLatitude, userLongitude,
                    cctv.getLatitude(), cctv.getLongitude());

            if (distance <= radius) {
                filteredCctvList.add(cctv);
                totalCameraCount += cctv.getCameraCount();
            }
        }

        result.setFilteredCctvList(filteredCctvList);
        result.setTotalCameraCount(totalCameraCount);

        log.info("[R-05] 반경 {}m 내 필터링된 CCTV: {}개 (총 카메라 대수: {}대)",
                radius, filteredCctvList.size(), totalCameraCount);

        // 2. 파출소 데이터 통합 및 가장 가까운 파출소 선정
        log.info("[R-05] 파출소 데이터 통합 및 최근접 파출소 선정 시작");
        List<PoliceOfficeGeo> allPoliceList = new ArrayList<>();

        for (String geohashId : nineBlockGeohashes) {
            List<PoliceOfficeGeo> policeInGrid = dbResult.getPoliceData().get(geohashId);
            if (policeInGrid != null) {
                allPoliceList.addAll(policeInGrid);
            }
        }

        log.info("[R-05] 9개 격자에서 통합된 전체 파출소 개수: {}개", allPoliceList.size());

        PoliceOfficeGeo nearestPolice = null;
        double minDistance = Double.MAX_VALUE;

        for (PoliceOfficeGeo police : allPoliceList) {
            double distance = geohashService.calculateDistance(
                    userLatitude, userLongitude,
                    police.getLatitude(), police.getLongitude());

            if (distance < minDistance) {
                minDistance = distance;
                nearestPolice = police;
            }
        }

        result.setNearestPolice(nearestPolice);
        result.setDistanceToNearestPolice(minDistance);

        if (nearestPolice != null) {
            log.info("[R-05] 가장 가까운 파출소: {} (거리: {}m)",
                    nearestPolice.getAddress(), Math.round(minDistance));
        } else {
            log.warn("[R-05] 주변에 파출소가 없습니다.");
        }

        // 3. 외부 API 데이터 통합
        log.info("[R-05] 외부 API 데이터 통합 시작");

        // R-04에서 카카오맵 Reverse Geocoding API를 통해 받은 주소 정보를
        // IntegratedDataResult 객체의 address 필드에 그대로 복사
        // AddressDto 구조: {roadAddress: "도로명주소", jibunAddress: "지번주소"}
        result.setAddress(apiResult.getAddress());

        // R-04에서 내부 DB(ArrestRateRepository)에서 조회한 해당 구의 검거율 값을
        // IntegratedDataResult 객체의 arrestRate 필드에 복사
        // 검거율 범위: 0.0 ~ 1.0 (예: 0.866910799는 86.69%)
        result.setArrestRate(apiResult.getArrestRate());

        // 4. 편의시설 데이터 처리 - AmenityDetailDto 리스트 생성
        // R-04에서 카카오맵 로컬 API로 검색한 15개 카테고리의 편의시설 데이터가 있는지 확인
        if (apiResult.getAmenityData() != null) {

            log.info("[R-05] 편의시설 데이터 거리 계산 및 DTO 변환 시작");

            // 최종적으로 IntegratedDataResult에 저장할 편의시설 상세 정보 리스트
            // 각 카테고리별로 하나의 AmenityDetailDto 객체가 생성됨
            List<AmenityDetailDto> amenityDetailsList = new ArrayList<>();

            // 카테고리 코드를 한글 이름으로 변환하기 위한 매핑 테이블 생성
            // 카카오맵 API는 categoryGroupCode(예: "CS2")만 제공하고
            // categoryGroupName은 제공하지 않으므로 우리가 직접 매핑
            // 예: "CS2" → "편의점", "SW8" → "지하철역"
            Map<String, String> categoryNames = createCategoryNameMap();

            // apiResult.getAmenityData()의 구조:
            // Map<String, List<Map<String, Object>>>
            // = Map<카테고리코드, List<장소정보>>
            // 예: {"CS2": [{name:"GS25", latitude:37.5, longitude:126.9, distance:120}, ...]}
            for (Map.Entry<String, List<Map<String, Object>>> entry : apiResult.getAmenityData().entrySet()) {

                // 현재 처리중인 카테고리 코드 (예: "CS2", "FD6" 등)
                String categoryCode = entry.getKey();

                // 해당 카테고리에 속한 장소들의 리스트
                // 각 장소는 Map<String, Object> 형태로 name, latitude, longitude 등을 포함
                List<Map<String, Object>> places = entry.getValue();

                // 카카오 API가 빈 결과를 반환했을 경우 다음 카테고리로 스킵
                if (places == null || places.isEmpty()) {
                    continue;
                }

                // 각 장소별 거리 계산 및 PlaceDto 변환
                // PlaceDto는 프론트엔드에 전달하기 위한 간소화된 장소 정보 객체
                List<PlaceDto> placeDtoList = new ArrayList<>();

                // 카테고리 내 모든 장소를 순회하며 처리
                for (Map<String, Object> place : places) {

                    // 카카오 API 응답에서 장소명 추출 (예: "GS25 서소문점")
                    String name = (String) place.get("name");

                    // 위도/경도 추출. Number 타입으로 캐스팅 후 double로 변환
                    // API는 때로 Integer나 Double로 반환하므로 Number로 통일 처리
                    double placeLatitude = ((Number) place.get("latitude")).doubleValue();
                    double placeLongitude = ((Number) place.get("longitude")).doubleValue();

                    // 사용자 현재 위치에서 해당 장소까지의 직선 거리 계산
                    // Haversine 공식 사용하여 지구 곡률을 고려한 정확한 거리 계산
                    double distance = geohashService.calculateDistance(
                            userLatitude, userLongitude,
                            placeLatitude, placeLongitude);

                    // 사용자가 요청한 반경 내에 있는 장소만 처리
                    // CCTV와 동일하게 반경 필터링 적용
                    if (distance <= radius) {
                        // PlaceDto 객체 생성 및 필드 설정
                        PlaceDto placeDto = new PlaceDto();
                        placeDto.setName(name);
                        placeDto.setLatitude(placeLatitude);
                        placeDto.setLongitude(placeLongitude);

                        // 거리는 미터 단위 정수로 반올림하여 저장 (예: 123.7m → 124m)
                        placeDto.setDistance((int) Math.round(distance));

                        // 생성된 PlaceDto를 해당 카테고리의 장소 리스트에 추가
                        placeDtoList.add(placeDto);
                    }
                    // 반경 밖의 장소는 무시 (리스트에 추가하지 않음)
                }

                // 거리 기준 오름차순 정렬
                // 사용자에게 가장 가까운 장소를 먼저 보여주기 위함
                // 람다식 사용: p1의 거리와 p2의 거리를 비교
                placeDtoList.sort((p1, p2) -> Integer.compare(p1.getDistance(), p2.getDistance()));

                // 카테고리별 종합 정보를 담는 AmenityDetailDto 객체 생성
                AmenityDetailDto amenityDetail = new AmenityDetailDto();

                // 카테고리 코드 설정 (예: "CS2")
                amenityDetail.setCategoryCode(categoryCode);

                // 카테고리 한글명 설정. 매핑 테이블에 없으면 코드 그대로 사용
                // 예: "CS2" → "편의점", 매핑 없으면 "CS2" 그대로
                amenityDetail.setCategoryName(categoryNames.getOrDefault(categoryCode, categoryCode));

                // 해당 카테고리의 총 장소 개수 설정
                amenityDetail.setCount(placeDtoList.size());

                // 가장 가까운 장소까지의 거리 설정
                // 정렬했으므로 첫 번째 요소가 가장 가까운 장소
                // 리스트가 비어있으면 0으로 설정 (방어 코드)
                amenityDetail.setClosestDistance(placeDtoList.isEmpty() ? 0 : placeDtoList.get(0).getDistance());

                // 해당 카테고리의 모든 장소 정보 리스트 설정
                amenityDetail.setPlaces(placeDtoList);

                // 완성된 카테고리 정보를 전체 편의시설 리스트에 추가
                amenityDetailsList.add(amenityDetail);

                // 디버그 로그: 각 카테고리별 처리 결과 출력
                log.debug("[R-05] - {}: {}개 장소 (최근접 거리: {}m)",
                        categoryCode,
                        placeDtoList.size(),
                        amenityDetail.getClosestDistance());
            }

            // 모든 카테고리의 편의시설 정보를 IntegratedDataResult에 저장
            // 이 데이터는 R-06에서 편의성 점수 계산에 사용됨
            result.setAmenityDetails(amenityDetailsList);

            log.info("[R-05] 편의시설 상세 정보 통합 완료: {}개 카테고리",
                    amenityDetailsList.size());

        } else {
            // R-04에서 편의시설 API 호출이 실패했거나 결과가 없는 경우
            log.warn("[R-05] 편의시설 데이터가 없습니다.");

            // null 대신 빈 리스트로 초기화하여 NullPointerException 방지
            result.setAmenityDetails(new ArrayList<>());
        }

        log.info("[R-05] 데이터 통합 및 필터링 완료");

        // 통합된 모든 데이터를 담은 IntegratedDataResult 객체 반환
        // 이 객체는 R-06의 점수 계산 단계로 전달됨
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
        log.info("[R-06] 최종 점수 계산 시작");

        ScoringResult result = new ScoringResult();

        // 1. 안전 점수 계산
        double safetyScore = calculateSafetyScore(integratedResult);
        result.setSafetyScore(safetyScore);

        // 2. 편의성 점수 계산
        double convenienceScore = calculateConvenienceScore(integratedResult);
        result.setConvenienceScore(convenienceScore);

        // 3. 종합 점수 계산 (안전 50%, 편의성 50%)
        // JS의 totalScore 계산 방식과 동일
        double totalScore = (safetyScore + convenienceScore) / 2;
        result.setTotalScore(totalScore);

        log.info("[R-06] 점수 계산 완료 - 안전: {}, 편의성: {}, 종합: {}",
                String.format("%.2f", safetyScore),
                String.format("%.2f", convenienceScore),
                String.format("%.2f", totalScore));

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
            // 거리가 800m 이하일 때만 점수 부여, 800m 초과 시 0점
            if (distance <= 800) {
                distanceScore = (800 - distance) / 800 * 100;
            } else {
                distanceScore = 0;
            }
        }

        // 2. CCTV 점수 계산 (JS와 동일하게 30개 기준)
        int cctvCount = data.getFilteredCctvList().size();
        double cctvScore = Math.min(cctvCount / 30.0 * 100, 100);  // 최대 100점

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
                    // JS: 인구 만명당 개수 × 10
                    categoryScore = Math.min((count / (population / 10000.0)) * 10, 100);
                    break;

                case "CE7":  // 카페
                    // JS: 개수 × 2
                    categoryScore = Math.min(count * 2, 100);
                    break;

                case "FD6":  // 음식점
                    // JS: 개수 × 1
                    categoryScore = Math.min(count * 1, 100);
                    break;

                case "SW8":  // 지하철역
                    // JS: 거리 기반 (500m 이내 100점)
                    if (closestDistance <= 500) {
                        categoryScore = 100;
                    } else if (closestDistance <= 1000) {
                        categoryScore = 100 - ((closestDistance - 500) / 500.0 * 50);
                    } else {
                        categoryScore = Math.max(0, 50 - ((closestDistance - 1000) / 1000.0 * 50));
                    }
                    break;

                case "MT1":  // 대형마트
                    // JS: 개수 × 10
                    categoryScore = Math.min(count * 10, 100);
                    break;

                case "BK9":  // 은행
                    // JS: 개수 × 5
                    categoryScore = Math.min(count * 5, 100);
                    break;

                case "PM9":  // 약국
                    // 개수 × 8 (중요도 고려)
                    categoryScore = Math.min(count * 8, 100);
                    break;

                case "HP8":  // 병원
                    // 거리와 개수 복합 고려
                    double distanceScore = closestDistance <= 1000 ? 50 : 25;
                    double countScore = Math.min(count * 10, 50);
                    categoryScore = distanceScore + countScore;
                    break;

                case "PO3":  // 공공기관
                    // 개수 × 3
                    categoryScore = Math.min(count * 3, 100);
                    break;

                case "CT1":  // 문화시설
                    // 개수 × 4
                    categoryScore = Math.min(count * 4, 100);
                    break;

                case "PK6":  // 주차장
                    // 개수 × 3
                    categoryScore = Math.min(count * 3, 100);
                    break;

                case "OL7":  // 주유소
                    // 개수 × 5
                    categoryScore = Math.min(count * 5, 100);
                    break;

                case "SC4":  // 학교
                    // 개수 × 2
                    categoryScore = Math.min(count * 2, 100);
                    break;

                case "AC5":  // 학원
                    // 개수 × 1
                    categoryScore = Math.min(count * 1, 100);
                    break;

                case "AT4":  // 관광명소
                    // 개수 × 3
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

        log.info("[R-07] 최종 응답 생성 시작");

        LocationAnalysisResponseDTO response = new LocationAnalysisResponseDTO();

        // 분석 상태 설정
        response.setAnalysisStatus("SUCCESS");

        // 좌표 정보 설정
        CoordinateDto coordinate = new CoordinateDto();
        coordinate.setLatitude(request.getLatitude());
        coordinate.setLongitude(request.getLongitude());
        response.setCoordinate(coordinate);

        // 주소 정보 설정
        response.setAddress(integratedResult.getAddress());

        // 안전성 점수 정보 설정
        SafetyScoreDto safetyScore = new SafetyScoreDto();
        safetyScore.setTotal((int) Math.round(scoringResult.getSafetyScore()));

        // 파출소 거리 및 상세 정보 설정
        if (integratedResult.getNearestPolice() != null) {
            // 거리 정보
            safetyScore.setPoliceDistance((int) Math.round(integratedResult.getDistanceToNearestPolice()));

            // 파출소 상세 정보 추가
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

        // 편의성 점수 정보 설정
        ConvenienceScoreDto convenienceScore = new ConvenienceScoreDto();
        convenienceScore.setTotal((int) Math.round(scoringResult.getConvenienceScore()));
        convenienceScore.setAmenityDetails(convertToAmenityDetailDtos(integratedResult.getAmenityDetails()));   // 추후 내부 dto 통합 필요..
        response.setConvenienceScore(convenienceScore);

        // 종합 점수 설정
        response.setOverallScore((int) Math.round(scoringResult.getTotalScore()));

        // 추천사항 생성
        List<String> recommendations = generateRecommendations(scoringResult, integratedResult);
        response.setRecommendations(recommendations);

        // 경고사항 생성
        List<String> warnings = generateWarnings(scoringResult, integratedResult);
        response.setWarnings(warnings);

        // 1단계 캐시에 저장 (TTL: 5분)
        try {
            String cacheKey = "dto:" + centerGeohashId;
            String jsonData = objectMapper.writeValueAsString(response);
            redisSingleDataService.setSingleData(cacheKey, jsonData, LEVEL1_CACHE_TTL);
            log.info("[R-07] 최종 응답 캐싱 완료 - Key: {}", cacheKey);
        } catch (Exception e) {
            log.warn("[R-07] 최종 응답 캐싱 실패: {}", e.getMessage());
        }

        log.info("[R-07] 최종 응답 생성 완료");
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
}