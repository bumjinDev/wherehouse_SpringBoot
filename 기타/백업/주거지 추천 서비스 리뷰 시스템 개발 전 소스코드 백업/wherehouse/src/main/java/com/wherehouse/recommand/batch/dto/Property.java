package com.wherehouse.recommand.batch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 매물 정보 DTO
 * 국토교통부 API 응답을 매핑하고 Redis에 저장할 데이터 구조
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Property {

    // 매물 기본 정보
    private String propertyId;        // 매물 고유 식별자 (자동생성)
    private String aptNm;             // 아파트명
    private Double excluUseAr;        // 전용면적(㎡)
    private Integer floor;            // 층수
    private Integer buildYear;        // 건축연도

    // 거래 정보
    private String dealDate;          // 계약일자(YYYY-MM-DD)
    private Integer deposit;          // 보증금/전세금(만원)
    private Integer monthlyRent;      // 월세금(만원)
    private String leaseType;         // 임대유형(전세/월세)

    // 위치 정보
    private String umdNm;             // 법정동명
    private String jibun;             // 지번
    private String sggCd;             // 시군구코드
    private String address;           // 전체 주소 (umdNm + jibun 조합)

    // 계산된 값들
    private Double areaInPyeong;      // 전용면적(평) - excluUseAr * 0.3025
    private Double safetyScore;       // 안전성 점수 (0~100점)

    // 메타 정보
    private String rgstDate;          // 등록일자
    private String districtName;      // 지역구명 (서울시 XX구)

    /**
     * 전용면적을 평수로 변환
     */
    public void calculateAreaInPyeong() {
        if (this.excluUseAr != null) {
            this.areaInPyeong = this.excluUseAr * 0.3025;
        }
    }

    /**
     * 임대유형 결정 (월세금 존재 여부로 판단)
     */
    public void determineLeaseType() {
        if (monthlyRent != null && monthlyRent > 0) {
            this.leaseType = "월세";
        } else {
            this.leaseType = "전세";
        }
    }

    /**
     * 전체 주소 생성
     */
    public void generateAddress() {
        if (umdNm != null && jibun != null) {
            this.address = umdNm + " " + jibun;
        }
    }
}