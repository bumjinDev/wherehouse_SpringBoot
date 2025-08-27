package com.WhereHouse.APITest.Cinema.Roader;

import com.opencsv.CSVReader;
import com.WhereHouse.APITest.Cinema.Entity.CulturalSportsBusiness;
import com.WhereHouse.APITest.Cinema.Repository.CulturalSportsBusinessRepository;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CulturalSportsDataLoader implements CommandLineRunner {

    private final CulturalSportsBusinessRepository culturalSportsRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.cultural-sports-data-path}")
    private String csvFilePath;

    @Override
    @Transactional
    public void run(String... args) {
        if (culturalSportsRepository.count() > 0) {
            log.info("문화체육업 데이터 이미 존재. 로딩 스킵");
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

                    if (row.length < 56) {
                        skipCount++;
                        log.debug("행 {}: 컬럼 수 부족 ({}개), 스킵", i, row.length);
                        continue;
                    }

                    try {
                        CulturalSportsBusiness business = CulturalSportsBusiness.builder()
                                .localGovtCode(processStringWithLength(row[0], 50, "지자체코드"))
                                .managementNo(processStringWithLength(row[1], 100, "관리번호"))
                                .licenseDate(parseDate(row[2]))
                                .licenseCancelDate(parseDate(row[3]))
                                .businessStatusCode(processStringWithLength(row[4], 20, "영업상태코드"))
                                .businessStatusName(processStringWithLength(row[5], 100, "영업상태명"))
                                .detailStatusCode(processStringWithLength(row[6], 20, "상세상태코드"))
                                .detailStatusName(processStringWithLength(row[7], 100, "상세상태명"))
                                .closureDate(parseDate(row[8]))
                                .suspensionStartDate(parseDate(row[9]))
                                .suspensionEndDate(parseDate(row[10]))
                                .reopenDate(parseDate(row[11]))
                                .phoneNumber(processStringWithLength(row[12], 50, "전화번호"))
                                .locationArea(parseDouble(row[13]))
                                .locationPostalCode(processStringWithLength(row[14], 50, "소재지우편번호"))
                                .jibunAddress(processStringWithLength(row[15], 500, "지번주소"))
                                .roadAddress(processStringWithLength(row[16], 500, "도로명주소"))
                                .roadPostalCode(processStringWithLength(row[17], 50, "도로명우편번호"))
                                .businessName(processStringWithLength(row[18], 300, "사업장명"))
                                .lastUpdateDate(parseDate(row[19]))
                                .dataUpdateType(processStringWithLength(row[20], 20, "데이터갱신구분"))
                                .dataUpdateTime(processStringWithLength(row[21], 50, "데이터갱신일자"))
                                .businessTypeName(processStringWithLength(row[22], 100, "업태구분명"))
                                .coordX(parseDouble(row[23]))
                                .coordY(parseDouble(row[24]))
                                .cultureSportsTypeName(processStringWithLength(row[25], 100, "문화체육업종명"))
                                .cultureBusinessTypeName(processStringWithLength(row[26], 100, "문화사업자구분명"))
                                .totalFloors(parseInteger(row[27]))
                                .surroundingEnvironment(processStringWithLength(row[28], 500, "주변환경명"))
                                .productionItemContent(processStringWithLength(row[29], 1000, "제작취급품목내용"))
                                .facilityArea(parseDouble(row[30]))
                                .groundFloors(parseInteger(row[31]))
                                .undergroundFloors(parseInteger(row[32]))
                                .buildingUseName(processStringWithLength(row[33], 200, "건물용도명"))
                                .passageWidth(parseDouble(row[34]))
                                .lightingBrightness(parseDouble(row[35]))
                                .karaokeRooms(parseInteger(row[36]))
                                .youthRooms(parseInteger(row[37]))
                                .emergencyStairs(processStringWithLength(row[38], 10, "비상계단여부"))
                                .emergencyExit(processStringWithLength(row[39], 10, "비상구여부"))
                                .autoVentilation(processStringWithLength(row[40], 10, "자동환기여부"))
                                .youthRoomExists(processStringWithLength(row[41], 10, "청소년실여부"))
                                .specialLighting(processStringWithLength(row[42], 10, "특수조명여부"))
                                .soundproofFacility(processStringWithLength(row[43], 10, "방음시설여부"))
                                .videoPlayerName(processStringWithLength(row[44], 200, "비디오재생기명"))
                                .lightingFacility(processStringWithLength(row[45], 10, "조명시설유무"))
                                .soundFacility(processStringWithLength(row[46], 10, "음향시설여부"))
                                .convenienceFacility(processStringWithLength(row[47], 10, "편의시설여부"))
                                .fireSafetyFacility(processStringWithLength(row[48], 10, "소방시설여부"))
                                .totalGameMachines(parseInteger(row[49]))
                                .existingGameBusinessType(processStringWithLength(row[50], 200, "기존게임업외업종명"))
                                .providedGameName(processStringWithLength(row[51], 500, "제공게임물명"))
                                .performanceHallType(processStringWithLength(row[52], 100, "공연장형태구분명"))
                                .itemName(processStringWithLength(row[53], 300, "품목명"))
                                .firstRegistrationTime(processStringWithLength(row[54], 50, "최초등록시점"))
                                .regionTypeName(processStringWithLength(row[55], 100, "지역구분명"))
                                .build();

                        culturalSportsRepository.save(business);
                        savedCount++;

                        if (savedCount % 100 == 0) {
                            log.info("{}개 문화체육업 데이터 저장 중...", savedCount);
                        }
                    } catch (Exception e) {
                        skipCount++;
                        log.warn("행 {} 처리 중 오류: {}", i, e.getMessage());
                        if (skipCount <= 5) {
                            log.debug("오류 발생한 행 데이터: {}", Arrays.toString(row));
                        }
                    }
                }

                log.info("=== 문화체육업 데이터 로딩 완료 ===");
                log.info("총 처리 행 수: {}", csvData.size() - 1);
                log.info("저장 성공: {}개", savedCount);
                log.info("처리 실패: {}개", skipCount);
            }
        } catch (Exception e) {
            log.error("문화체육업 CSV 로딩 실패: {}", e.getMessage(), e);
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

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty() || "-".equals(dateStr.trim())) {
            return LocalDate.of(1900, 1, 1); // 기본 날짜
        }

        String trimmed = dateStr.trim();

        // 시간이 포함된 경우 날짜 부분만 추출 (예: "2025-03-10 16:43" -> "2025-03-10")
        if (trimmed.contains(" ")) {
            trimmed = trimmed.split(" ")[0];
        }

        try {
            // YYYY-MM-DD 형식으로 파싱
            return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            try {
                // YYYY-M-D 형식도 시도
                return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("yyyy-M-d"));
            } catch (Exception ex) {
                log.debug("날짜 파싱 실패: {}", dateStr);
                return LocalDate.of(1900, 1, 1); // 기본 날짜
            }
        }
    }

    private Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            log.debug("Double 파싱 실패: {}", value);
            return 0.0;
        }
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