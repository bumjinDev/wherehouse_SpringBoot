package com.WhereHouse.AnalysisStaticData.Police.Roader;

import com.opencsv.CSVReader;
import com.WhereHouse.AnalysisStaticData.Police.Entity.PoliceFacility;
import com.WhereHouse.AnalysisStaticData.Police.Repository.PoliceFacilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PoliceFacilityDataLoader { // implements CommandLineRunner

    private final PoliceFacilityRepository policeFacilityRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.police-facility-data-path}")
    private String csvFilePath;

//    @Override
    @Transactional
    public void run(String... args) {
        if (policeFacilityRepository.count() > 0) {
            log.info("경찰시설 데이터 이미 존재. 로딩 스킵");
            return;
        }

        try {
            Resource resource = resourceLoader.getResource(csvFilePath);
            log.info("CSV 파일 경로: {}", csvFilePath);
            log.info("CSV 파일 존재 여부: {}", resource.exists());

            if (!resource.exists()) {
                log.error("CSV 파일이 존재하지 않습니다: {}", csvFilePath);
                return;
            }

            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();
                log.info("총 CSV 행 수: {}", csvData.size());

                if (csvData.size() == 0) {
                    log.warn("CSV 파일이 비어있습니다");
                    return;
                }

                if (csvData.size() > 0) {
                    log.info("첫 번째 행 컬럼 수: {}", csvData.get(0).length);
                    log.info("첫 번째 행 내용: {}", Arrays.toString(csvData.get(0)));
                }

                if (csvData.size() > 1) {
                    log.info("두 번째 행 컬럼 수: {}", csvData.get(1).length);
                    log.info("두 번째 행 내용: {}", Arrays.toString(csvData.get(1)));
                }

                int savedCount = 0;
                int skipCount = 0;

                // 첫 번째 행은 헤더이므로 스킵
                for (int i = 1; i < csvData.size(); i++) {
                    String[] row = csvData.get(i);

                    if (row.length < 7) {
                        skipCount++;
                        log.debug("행 {}: 컬럼 수 부족 ({}개), 스킵", i, row.length);
                        continue;
                    }

                    try {
                        PoliceFacility facility = PoliceFacility.builder()
                                .serialNo(parseInteger(row[0]))
                                .cityProvince(processStringWithLength(row[1], 50, "시도청"))
                                .policeStation(processStringWithLength(row[2], 100, "경찰서"))
                                .facilityName(processStringWithLength(row[3], 100, "관서명"))
                                .facilityType(processStringWithLength(row[4], 50, "구분"))
                                .phoneNumber(processStringWithLength(row[5], 50, "전화번호"))
                                .address(processStringWithLength(row[6], 500, "주소"))
                                .coordX(0.0) // 초기값 설정 (추후 지오코딩으로 설정 예정)
                                .coordY(0.0) // 초기값 설정 (추후 지오코딩으로 설정 예정)
                                .build();

                        policeFacilityRepository.save(facility);
                        savedCount++;

                        if (savedCount % 100 == 0) {
                            log.info("{}개 경찰시설 데이터 저장 중...", savedCount);
                        }
                    } catch (Exception e) {
                        skipCount++;
                        log.warn("행 {} 처리 중 오류: {}", i, e.getMessage());
                        if (skipCount <= 5) {
                            log.debug("오류 발생한 행 데이터: {}", Arrays.toString(row));
                        }
                    }
                }

                log.info("=== 경찰시설 데이터 로딩 완료 ===");
                log.info("총 처리 행 수: {}", csvData.size() - 1);
                log.info("저장 성공: {}개", savedCount);
                log.info("처리 실패: {}개", skipCount);
            }
        } catch (Exception e) {
            log.error("경찰시설 CSV 로딩 실패: {}", e.getMessage(), e);
        }
    }

    private String processStringWithLength(String value, int maxLength, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return "데이터없음";
        }
        String trimmed = value.trim();

        if (trimmed.length() > maxLength) {
            log.warn("{} 필드 길이 초과 (실제: {}, 최대: {}): {}",
                    fieldName, trimmed.length(), maxLength,
                    trimmed.length() > 30 ? trimmed.substring(0, 30) + "..." : trimmed);
            return trimmed.substring(0, maxLength);
        }

        return trimmed;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.debug("Integer 파싱 실패: {}", value);
            return 0;
        }
    }
}