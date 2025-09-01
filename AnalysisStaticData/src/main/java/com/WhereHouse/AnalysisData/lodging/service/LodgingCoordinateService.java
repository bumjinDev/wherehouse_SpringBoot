package com.WhereHouse.AnalysisData.lodging.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 숙박업 주소 기반 좌표 계산 서비스
 *
 * 기존 COORD_X, COORD_Y 값이 부정확하여 주소 정보를 바탕으로
 * 위도, 경도 좌표를 재계산하는 서비스를 제공한다.
 * 실제 구현 시에는 Geocoding API를 활용하여 정확한 좌표를 계산한다.
 *
 * @author Safety Analysis System
 * @since 1.0
 */
@Service
@Slf4j
public class LodgingCoordinateService {

    /**
     * 도로명주소 기반 좌표 계산
     *
     * @param roadAddress 도로명 주소
     * @return 위도, 경도 배열 [latitude, longitude] 또는 null
     */
    public Double[] calculateCoordinatesFromRoadAddress(String roadAddress) {
        if (roadAddress == null || roadAddress.trim().isEmpty()) {
            return null;
        }

        try {
            // TODO: 실제 Geocoding API 호출 또는 좌표 매핑 로직 구현

            log.debug("좌표 계산 대상 도로명주소: {}", roadAddress);
            return calculateDummyCoordinates(roadAddress);

        } catch (Exception e) {
            log.error("도로명주소 좌표 계산 실패 - 주소: {}, 오류: {}", roadAddress, e.getMessage());
            return null;
        }
    }

    /**
     * 지번주소 기반 좌표 계산
     *
     * @param fullAddress 지번 주소
     * @return 위도, 경도 배열 [latitude, longitude] 또는 null
     */
    public Double[] calculateCoordinatesFromFullAddress(String fullAddress) {
        if (fullAddress == null || fullAddress.trim().isEmpty()) {
            return null;
        }

        try {
            log.debug("좌표 계산 대상 지번주소: {}", fullAddress);
            return calculateDummyCoordinates(fullAddress);

        } catch (Exception e) {
            log.error("지번주소 좌표 계산 실패 - 주소: {}, 오류: {}", fullAddress, e.getMessage());
            return null;
        }
    }

    /**
     * 임시 좌표 계산 로직 (실제 구현 시 제거 필요)
     *
     * 주소 문자열의 해시값을 기반으로 전국 범위의 임의 좌표를 생성한다.
     * 실제 서비스에서는 Geocoding API를 통해 정확한 좌표를 계산해야 한다.
     *
     * @param address 주소 문자열
     * @return 임시 좌표 배열
     */
    private Double[] calculateDummyCoordinates(String address) {
        // 한국 중심 좌표: 36.5, 127.5
        double baseLatitude = 36.5;
        double baseLongitude = 127.5;

        // 주소 해시값을 기반으로 임의 좌표 생성 (전국 범위 ±3도)
        int hashCode = address.hashCode();
        double latitudeOffset = (hashCode % 6000 - 3000) / 1000.0; // -3 ~ +3
        double longitudeOffset = ((hashCode / 6000) % 6000 - 3000) / 1000.0; // -3 ~ +3

        double latitude = baseLatitude + latitudeOffset;
        double longitude = baseLongitude + longitudeOffset;

        return new Double[]{latitude, longitude};
    }
}