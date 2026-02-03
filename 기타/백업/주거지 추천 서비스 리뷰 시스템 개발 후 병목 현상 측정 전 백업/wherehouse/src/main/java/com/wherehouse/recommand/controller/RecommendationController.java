package com.wherehouse.recommand.controller;

import com.wherehouse.recommand.model.CharterRecommendationRequestDto;
import com.wherehouse.recommand.model.CharterRecommendationResponseDto;
import com.wherehouse.recommand.model.MonthlyRecommendationRequestDto;
import com.wherehouse.recommand.model.MonthlyRecommendationResponseDto;
import com.wherehouse.recommand.service.CharterRecommendationService;
import com.wherehouse.recommand.service.MonthlyRecommendationService;

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

    private final CharterRecommendationService charterRecommendationService;
    private final MonthlyRecommendationService monthlyRecommendationService;

    /**
     * 전세 지역구 추천 API - POST 방식
     * @RequestBody + @Valid → MethodArgumentNotValidException 발생
     */
    @PostMapping("/charter-districts")
    public ResponseEntity<CharterRecommendationResponseDto> getCharterDistrictRecommendations(
            @Valid @RequestBody CharterRecommendationRequestDto request) {

        log.info("=== 전세 지역구 추천 요청 시작 (POST) ===");
        log.info("요청 파라미터: {}", request);

        try {
            // 전세 전용 RecommendationService 호출
            CharterRecommendationResponseDto response = charterRecommendationService.getCharterDistrictRecommendations(request);

            log.info("=== 전세 지역구 추천 요청 완료 - 상태: {} ===", response.getSearchStatus());
            return ResponseEntity.ok(response);

        } catch (Exception e) {

            log.error("전세 지역구 추천 처리 중 오류 발생", e);

            // 오류 발생 시 기본 응답 반환
            CharterRecommendationResponseDto errorResponse = CharterRecommendationResponseDto.builder()
                    .searchStatus("NO_RESULTS")
                    .message("시스템 오류로 인해 전세 추천 결과를 가져올 수 없습니다. 잠시 후 다시 시도해 주세요.")
                    .recommendedDistricts(java.util.Collections.emptyList())
                    .build();

            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * 월세 지역구 추천 API - POST 방식
     * @RequestBody + @Valid → MethodArgumentNotValidException 발생
     */
    @PostMapping("/monthly-districts")
    public ResponseEntity<MonthlyRecommendationResponseDto> getMonthlyDistrictRecommendations(
            @Valid @RequestBody MonthlyRecommendationRequestDto request) {

        log.info("=== 월세 지역구 추천 요청 시작 (POST) ===");
        log.info("요청 파라미터: {}", request);

        try {
            // 월세 전용 RecommendationService 호출
            MonthlyRecommendationResponseDto response = monthlyRecommendationService.getMonthlyDistrictRecommendations(request);

            log.info("=== 월세 지역구 추천 요청 완료 - 상태: {} ===", response.getSearchStatus());
            return ResponseEntity.ok(response);

        } catch (Exception e) {

            log.error("월세 지역구 추천 처리 중 오류 발생", e);

            // 오류 발생 시 기본 응답 반환
            MonthlyRecommendationResponseDto errorResponse = MonthlyRecommendationResponseDto.builder()
                    .searchStatus("NO_RESULTS")
                    .message("시스템 오류로 인해 월세 추천 결과를 가져올 수 없습니다. 잠시 후 다시 시도해 주세요.")
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