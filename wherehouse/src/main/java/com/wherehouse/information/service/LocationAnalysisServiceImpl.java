package com.wherehouse.information.service;

import com.wherehouse.information.dao.ArrestRateRepository;
import com.wherehouse.information.dao.CctvGeoRepository;
import com.wherehouse.information.dao.PoliceOfficeGeoRepository;
import com.wherehouse.information.entity.ArrestRate;
import com.wherehouse.information.entity.CctvGeo;
import com.wherehouse.information.entity.PoliceOfficeGeo;
import com.wherehouse.information.model.controller.LocationAnalysisRequestDTO;
import com.wherehouse.information.model.controller.LocationAnalysisResponseDTO;
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

/**
 * 위치 분석 서비스 구현체
 *
 * 처리 단계 (6.4.4 실시간 서비스 처리 단계):
 * R-01: '9-Block' 그리드 범위 계산
 * R-02: 단계별 캐시 조회
 * R-03: 선택된 데이터베이스 조회
 * R-04: 외부 API 호출 및 개별 데이터 캐싱
 * R-05: 데이터 통합, 필터링, 최종 응답 생성 대기
 * R-06: 최종 점수 계산
 * R-07: 최종 응답 생성 및 캐싱
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

        // TODO: R-03~R-07 구현 예정

        return null; // 임시
    }

    /**
     * R-01: '9-Block' 그리드 범위 계산
     *
     * 처리 내용:
     * 1. 사용자가 요청한 좌표를 기반으로 중심 셀의 지오해시 ID를 계산
     * 2. 중심 셀을 포함하여, 주변 8개 셀의 지오해시 ID를 계산 (총 9개)
     * 3. 계산된 9개 셀의 지오해시 ID 목록을 다음 단계에 전달
     *
     * 설계 근거:
     * - 이 과정은 사용자의 분석 반경이 여러 격자에 걸쳐 있을 가능성을 해결한다.
     *
     * @param request 사용자 요청 DTO (위도, 경도 포함)
     * @return 9개의 지오해시 ID 목록 (중심 1개 + 인접 8개)
     */
    private List<String> calculate9BlockGrid(LocationAnalysisRequestDTO request) {
        log.info("[R-01] 9-Block 그리드 범위 계산 시작");

        double latitude = request.getLatitude();
        double longitude = request.getLongitude();

        // GeohashService를 통해 9개 격자 ID 계산
        List<String> nineBlockIds = geohashService.calculate9BlockGeohashes(latitude, longitude);

        log.info("[R-01] 계산된 9-Block 그리드: {}", nineBlockIds);
        log.info("[R-01] 중심 격자 ID: {}", nineBlockIds.get(0));
        log.info("[R-01] 9-Block 그리드 범위 계산 완료");

        return nineBlockIds;
    }

    /**
     * R-02: 단계별 캐시 조회
     *
     * 처리 내용:
     * 1. (1단계 캐시) R-01에서 계산한 중심 셀의 지오해시 ID를 키로 하여,
     *    최종 응답(DTO) 전체가 캐시에 있는지 확인. 캐시 히트 시 즉시 반환하고 절차 종료
     *
     * 2. (2단계 캐시) 1단계에서 캐시 미스가 발생하면, 9개의 격자 ID 각각에 대해
     *    개별 데이터(예: CCTV 목록)가 캐시되어 있는지 확인
     *    - 개별 캐시 히트 시: 해당 격자의 데이터는 캐시된 값을 사용
     *    - 개별 캐시 미스 시: DB 조회로 전환 (R-03 단계)
     *
     * 캐시 키 구조:
     * - 1단계: "dto:" + 중심 격자 ID (예: "dto:wydm7p1")
     * - 2단계: "data:" + 격자 ID + ":" + 데이터 종류 (예: "data:wydm7p1:cctv")
     *
     * TTL:
     * - 1단계: 5분 (짧은 시간 내 동일 지역 재요청 대응)
     * - 2단계: 24시간 (공공 데이터는 자주 변경되지 않음)
     *
     * Redis 사용:
     * - RedisSingleDataService를 통해 캐시 조회/저장
     * - JSON 직렬화를 통해 복잡한 객체 저장
     *
     * @param nineBlockGeohashes 9개의 격자 ID 목록
     * @param request 사용자 요청 DTO
     * @return 캐시 조회 결과 (히트 여부, 캐시된 데이터 포함)
     */
    private CacheResult performCacheLookup(List<String> nineBlockGeohashes,
                                           LocationAnalysisRequestDTO request) {
        log.info("[R-02] 단계별 캐시 조회 시작");

        String centerGeohashId = nineBlockGeohashes.get(0);
        String level1CacheKey = "dto:" + centerGeohashId;

        // 1단계 캐시 조회: 전체 DTO
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

        // 2단계 캐시 조회: 개별 격자 데이터
        log.info("[R-02-2단계] 개별 격자 데이터 캐시 조회 시작 (9개 격자)");

        CacheResult result = new CacheResult();
        result.setLevel1Hit(false);
        result.setNineBlockGeohashes(nineBlockGeohashes);

        // 각 격자별로 CCTV, 파출소 데이터 캐시 확인
        for (String geohashId : nineBlockGeohashes) {
            // CCTV 데이터 캐시 조회
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

            // 파출소 데이터 캐시 조회
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
     * 캐시 조회 결과를 담는 내부 클래스
     */
    private static class CacheResult {

        private boolean level1Hit = false;
        private LocationAnalysisResponseDTO cachedResponse;
        private List<String> nineBlockGeohashes;

        // 2단계 캐시: 격자별 캐시된 데이터
        private final java.util.Map<String, List<CctvGeo>> cachedCctvData = new java.util.HashMap<>();
        private final java.util.Map<String, List<PoliceOfficeGeo>> cachedPoliceData = new java.util.HashMap<>();

        // 2단계 캐시 미스: DB 조회가 필요한 격자 ID 목록
        private final List<String> cctvMisses = new java.util.ArrayList<>();
        private final List<String> policeMisses = new java.util.ArrayList<>();

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

        public java.util.Map<String, List<CctvGeo>> getCachedCctvData() {
            return cachedCctvData;
        }

        public java.util.Map<String, List<PoliceOfficeGeo>> getCachedPoliceData() {
            return cachedPoliceData;
        }

        public List<String> getCctvMisses() {
            return cctvMisses;
        }

        public List<String> getPoliceMisses() {
            return policeMisses;
        }
    }
}