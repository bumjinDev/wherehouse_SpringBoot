package com.wherehouse.recommand.controller;

import com.wherehouse.recommand.model.RecommendationRequestDto;
import com.wherehouse.recommand.model.RecommendationResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class RecommendationController {

    /**
     * 지역구 추천 API - POST 방식으로 변경
     * @RequestBody + @Valid → MethodArgumentNotValidException 발생
     */
    @PostMapping("/districts")
    public ResponseEntity<RecommendationResponseDto> getDistrictRecommendations(
            @Valid @RequestBody RecommendationRequestDto request) {

        log.info("=== 지역구 추천 요청 시작 (POST) ===");
        log.info("요청 파라미터: {}", request);

        // 임시 Mock 응답
        RecommendationResponseDto response = RecommendationResponseDto.builder()
                .searchStatus("SUCCESS_NORMAL")
                .message("POST 요청이 성공적으로 처리되었습니다 (Mock 응답)")
                .recommendedDistricts(Collections.emptyList())
                .build();

        log.info("=== 지역구 추천 요청 종료 ===");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}