package com.wherehouse.recommand.controller;

import com.wherehouse.recommand.model.RecommendationRequestDto;
import com.wherehouse.recommand.model.RecommendationResponseDto;
import com.wherehouse.recommand.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * 지역구 추천 API - POST 방식
     * @RequestBody + @Valid → MethodArgumentNotValidException 발생
     */
    @PostMapping("/districts")
    public ResponseEntity<RecommendationResponseDto> getDistrictRecommendations(
            @Valid @RequestBody RecommendationRequestDto request) {

        log.info("=== 지역구 추천 요청 시작 (POST) ===");
        log.info("요청 파라미터: {}", request);

        try {
            // 실제 RecommendationService 호출
            RecommendationResponseDto response = recommendationService.getDistrictRecommendations(request);

            log.info("=== 지역구 추천 요청 완료 - 상태: {} ===", response.getSearchStatus());
            return ResponseEntity.ok(response);

        } catch (Exception e) {

            log.error("지역구 추천 처리 중 오류 발생", e);

            // 오류 발생 시 기본 응답 반환
            RecommendationResponseDto errorResponse = RecommendationResponseDto.builder()
                    .searchStatus("NO_RESULTS")
                    .message("시스템 오류로 인해 추천 결과를 가져올 수 없습니다. 잠시 후 다시 시도해 주세요.")
                    .recommendedDistricts(java.util.Collections.emptyList())
                    .build();

            return ResponseEntity.ok(errorResponse);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}