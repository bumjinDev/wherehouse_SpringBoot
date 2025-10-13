package com.wherehouse.information.controller;

import com.wherehouse.information.model.controller.LocationAnalysisRequestDTO;

import com.wherehouse.information.model.controller.LocationAnalysisResponseDTO;
import com.wherehouse.information.service.ILocationAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class LocationAnalysisController {

	private final ILocationAnalysisService locationAnalysisService;

	@PostMapping("/location-analysis")
	public ResponseEntity<LocationAnalysisResponseDTO> getLocationAnalysis(
			@Valid @RequestBody LocationAnalysisRequestDTO request) {

		log.info("=== 위치 분석 요청 시작 (POST /api/location-analysis) ===");
		log.info("Request DTO: {}", request);

		LocationAnalysisResponseDTO response = locationAnalysisService.analyzeLocation(request);

		log.info("위치 분석 요청 처리 완료. Status: {}", response.getAnalysisStatus());

		return ResponseEntity.ok(response);
	}

	@GetMapping("/health")
	public ResponseEntity<String> health() {
		return ResponseEntity.ok("OK");
	}
}
