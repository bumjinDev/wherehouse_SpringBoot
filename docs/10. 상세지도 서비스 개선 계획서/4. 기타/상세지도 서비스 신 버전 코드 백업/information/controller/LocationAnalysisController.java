package com.wherehouse.information.controller;

import com.wherehouse.information.model.LocationAnalysisRequestDTO;

import com.wherehouse.information.model.LocationAnalysisResponseDTO;
import com.wherehouse.information.model.PoliceOfficeResponseDTO;
import com.wherehouse.information.service.ILocationAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

	/* 처음 상세지포 페이지 로드 시 모든 파출소 좌표 정보 가져오기. */
	@GetMapping("/police-offices")
	public ResponseEntity<List<PoliceOfficeResponseDTO>> getAllPoliceOffices() {
		log.info("=== 전체 파출소 데이터 조회 요청 (GET /api/police-offices) ===");

		try {
			List<PoliceOfficeResponseDTO> policeOffices = locationAnalysisService.getAllPoliceOffices();
			log.info("파출소 데이터 조회 완료 - 총 {}개", policeOffices.size());

			return ResponseEntity.ok(policeOffices);
		} catch (Exception e) {
			log.error("파출소 데이터 조회 중 오류 발생", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	@GetMapping("/health")
	public ResponseEntity<String> health() {
		return ResponseEntity.ok("OK");
	}
}
