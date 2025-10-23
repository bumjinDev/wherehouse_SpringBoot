package com.wherehouse.information.util;

import ch.hsr.geohash.GeoHash;
import com.wherehouse.logger.PerformanceLogger;
import com.wherehouse.logger.result.R01.R01GeohashCalculationResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Geohash 기반 9-Block 그리드 계산 서비스
 *
 * 역할:
 * - 사용자 클릭 좌표를 기준으로 9개 격자(3x3) ID 생성
 * - 중심 격자 1개 + 인접 8개 격자 = 총 9개
 *
 * 설계 근거:
 * - 7자리 정밀도 Geohash: 약 150m x 150m 격자
 * - 9-Block 그리드: 약 450m x 450m 영역 커버
 * - 사용자 요청 반경 500m를 효율적으로 포함
 *
 * 사용 위치:
 * - LocationAnalysisServiceImpl: DB 조회 범위 설정
 */
@Service
public class GeohashService {

    private static final int GEOHASH_PRECISION = 7;  // 7자리 정밀도 (약 150m x 150m)

    /**
     * 좌표를 7자리 Geohash ID로 변환
     *
     * @param latitude  위도
     * @param longitude 경도
     * @return 7자리 Geohash ID (예: "wydm7p1")
     */
    public String encode(double latitude, double longitude) {
        GeoHash geoHash = GeoHash.withCharacterPrecision(latitude, longitude, GEOHASH_PRECISION);
        return geoHash.toBase32();
    }

    /**
     * 9-Block 그리드 Geohash ID 목록 생성
     *
     * @param latitude  중심 좌표 위도
     * @param longitude 중심 좌표 경도
     * @return 9개의 Geohash ID 목록 (중심 1개 + 인접 8개)
     * <p>
     * 그리드 구조:
     * [북서] [북쪽] [북동]
     * [서쪽] [중심] [동쪽]
     * [남서] [남쪽] [남동]
     * <p>
     * 반환 예시:
     * ["wydm7p1",  // 중심
     * "wydm7p4",  // 북쪽
     * "wydm7p5",  // 북동
     * "wydm7p3",  // 동쪽
     * "wydm7p2",  // 남동
     * "wydm7nz",  // 남쪽
     * "wydm7nx",  // 남서
     * "wydm7p0",  // 서쪽
     * "wydm7p6"]  // 북서
     */
    public List<String> calculate9BlockGeohashes(double latitude, double longitude) {

        // R-01 계측 시작
        PerformanceLogger perfLogger = PerformanceLogger.start(
                "R-01",                          // step
                "calculate9BlockGeohashes",       // action
                "Utility",                        // layer
                "GeohashService",                 // class
                "calculate9BlockGeohashes"        // method
        );

        // 로깅 객체 1차 생성
        R01GeohashCalculationResult r01GeohashCalculationResult = R01GeohashCalculationResult.builder()
                .latitude(latitude)
                .longitude(longitude)
                .precision(GEOHASH_PRECISION)
                .isSuccess(true)
                .errorMessage(null)  // 명시적으로 null 설정
                .build();

        /* 9개 격자 ID 반환할 객체 */
        List<String> nineBlockIds = new ArrayList<>();

        try {
            // 중심 격자 Geohash 생성
            GeoHash centerHash = GeoHash.withCharacterPrecision(latitude, longitude, GEOHASH_PRECISION);

            // 1. 중심 격자 추가
            nineBlockIds.add(centerHash.toBase32());
            // 2. 8방향 인접 격자 추가
            GeoHash[] adjacents = centerHash.getAdjacent();

            for (GeoHash adjacent : adjacents) {
                nineBlockIds.add(adjacent.toBase32());
            }

            // 로깅 객체 내 멤버 변수 내 결과 값에 대한 추가 설정
            r01GeohashCalculationResult.setCenterHash(centerHash.toBase32());
            r01GeohashCalculationResult.setAdjacentHashes(nineBlockIds);
            r01GeohashCalculationResult.setSuccess(true);
            r01GeohashCalculationResult.setErrorMessage(null);

            perfLogger.setResultData(r01GeohashCalculationResult);

        } catch (Exception e) {

            r01GeohashCalculationResult.setCenterHash(null);
            r01GeohashCalculationResult.setAdjacentHashes(null);
            r01GeohashCalculationResult.setSuccess(false);
            r01GeohashCalculationResult.setErrorMessage(e.getMessage());
            perfLogger.setResultData(r01GeohashCalculationResult);

        } finally {
            perfLogger.end();
        }

        return nineBlockIds;
    }



    /**
     * 두 좌표 간 거리 계산 (Haversine 공식)
     *
     * @param lat1 첫 번째 좌표 위도
     * @param lon1 첫 번째 좌표 경도
     * @param lat2 두 번째 좌표 위도
     * @param lon2 두 번째 좌표 경도
     * @return 두 좌표 간 거리 (미터)
     *
     * 사용 목적:
     * - 9-Block 그리드에서 조회한 데이터를 정확한 반경(500m)으로 필터링
     * - 가장 가까운 파출소 찾기
     */
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS = 6371000; // 지구 반지름 (미터)

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }


}