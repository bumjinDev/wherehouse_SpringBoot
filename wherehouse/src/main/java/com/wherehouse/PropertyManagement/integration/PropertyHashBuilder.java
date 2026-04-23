package com.wherehouse.PropertyManagement.integration;

import com.wherehouse.PropertyManagement.entity.PropertyCharterEntity;
import com.wherehouse.PropertyManagement.entity.PropertyMonthlyEntity;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Entity → Redis Hash Map 변환 공통 헬퍼.
 *
 * 설계 명세서: 섹션 8.2.1 (매물 상세 Hash 필드 확장).
 * Charter/Monthly 양쪽의 필드 매핑 규약을 단일 진원지로 보유한다.
 * RdbSyncListener.buildCharterHash / buildMonthlyHash 와 동일 구조 + 신규 5필드.
 *
 * NULL 처리: RDB 컬럼이 NULL 인 경우 Hash 필드에 빈 문자열("")을 저장 (섹션 8.2.1).
 */
@Component
public class PropertyHashBuilder {

    public Map<String, Object> buildCharterHash(PropertyCharterEntity e) {
        Map<String, Object> h = new HashMap<>();
        h.put("propertyId", nvl(e.getPropertyId()));
        h.put("aptNm", nvl(e.getAptNm()));
        h.put("excluUseAr", nvl(e.getExcluUseAr()));
        h.put("floor", nvl(e.getFloor()));
        h.put("buildYear", nvl(e.getBuildYear()));
        h.put("dealDate", nvl(e.getDealDate()));
        h.put("leaseType", "전세");
        h.put("umdNm", nvl(e.getUmdNm()));
        h.put("jibun", nvl(e.getJibun()));
        h.put("sggCd", nvl(e.getSggCd()));
        h.put("address", nvl(e.getAddress()));
        h.put("areaInPyeong", nvl(e.getAreaInPyeong()));
        h.put("rgstDate", nvl(e.getRgstDate()));
        h.put("districtName", nvl(e.getDistrictName()));
        h.put("deposit", nvl(e.getDeposit()));
        h.put("dataSource", e.getDataSource() != null ? e.getDataSource().name() : "");
        h.put("status", e.getStatus() != null ? e.getStatus().name() : "");
        h.put("registeredUserId", nvl(e.getRegisteredUserId()));
        h.put("registeredAt", e.getRegisteredAt() != null ? e.getRegisteredAt().toString() : "");
        h.put("modifiedAt", e.getModifiedAt() != null ? e.getModifiedAt().toString() : "");
        return h;
    }

    public Map<String, Object> buildMonthlyHash(PropertyMonthlyEntity e) {
        Map<String, Object> h = new HashMap<>();
        h.put("propertyId", nvl(e.getPropertyId()));
        h.put("aptNm", nvl(e.getAptNm()));
        h.put("excluUseAr", nvl(e.getExcluUseAr()));
        h.put("floor", nvl(e.getFloor()));
        h.put("buildYear", nvl(e.getBuildYear()));
        h.put("dealDate", nvl(e.getDealDate()));
        h.put("leaseType", "월세");
        h.put("umdNm", nvl(e.getUmdNm()));
        h.put("jibun", nvl(e.getJibun()));
        h.put("sggCd", nvl(e.getSggCd()));
        h.put("address", nvl(e.getAddress()));
        h.put("areaInPyeong", nvl(e.getAreaInPyeong()));
        h.put("rgstDate", nvl(e.getRgstDate()));
        h.put("districtName", nvl(e.getDistrictName()));
        h.put("deposit", nvl(e.getDeposit()));
        h.put("monthlyRent", nvl(e.getMonthlyRent()));
        h.put("dataSource", e.getDataSource() != null ? e.getDataSource().name() : "");
        h.put("status", e.getStatus() != null ? e.getStatus().name() : "");
        h.put("registeredUserId", nvl(e.getRegisteredUserId()));
        h.put("registeredAt", e.getRegisteredAt() != null ? e.getRegisteredAt().toString() : "");
        h.put("modifiedAt", e.getModifiedAt() != null ? e.getModifiedAt().toString() : "");
        return h;
    }

    private String nvl(Object v) {
        return v != null ? v.toString() : "";
    }
}