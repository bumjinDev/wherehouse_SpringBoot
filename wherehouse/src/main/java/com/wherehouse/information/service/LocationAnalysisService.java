package com.wherehouse.information.service;

import com.wherehouse.information.model.controller.LocationAnalysisRequestDTO;
import com.wherehouse.information.model.controller.LocationAnalysisResponseDTO;

/**
 * 위치 분석 서비스 인터페이스
 * 실제 구현은 LocationAnalysisServiceImpl 클래스에서 수행
 */
public interface LocationAnalysisService {

    /**
     * 위치 분석을 수행하는 메인 메서드
     *
     * 처리 과정:
     * 1. 좌표 기반 9-Block 그리드 계산
     * 2. 캐시 조회 (1단계: 전체 DTO, 2단계: 개별 데이터)
     * 3. 캐시 미스 시 DB 조회 (CCTV_GEO, POLICE_OFFICE_GEO)
     * 4. 외부 API 호출 (카카오맵: 주소 변환, 편의시설)
     * 5. 병렬 처리 완료 대기 (CompletableFuture.allOf)
     * 6. 반경 필터링 (500m 이내만 선택)
     * 7. 점수 계산 (안전성, 편의성, 종합)
     * 8. 추천 근거 생성
     * 9. 캐시 저장 및 응답 반환
     *
     * @param request 위도, 경도, 반경 정보를 포함한 요청 DTO
     * @return 안전성, 편의성, 종합 점수 및 상세 정보를 포함한 응답 DTO
     */
    LocationAnalysisResponseDTO analyzeLocation(LocationAnalysisRequestDTO request);
}
