# 노래연습장 데이터 Oracle DB 설계 및 JPA 구현

## 1. Oracle 테이블 DDL

```sql
CREATE TABLE KARAOKE_ROOMS (
    ID                      NUMBER(19) PRIMARY KEY,
    DISTRICT_CODE           VARCHAR2(20) NOT NULL,
    MANAGEMENT_NUMBER       VARCHAR2(100),
    LICENSE_DATE            DATE,
    CANCEL_DATE             DATE,
    BUSINESS_STATUS_CODE    VARCHAR2(20),
    BUSINESS_STATUS_NAME    VARCHAR2(100),
    DETAIL_STATUS_CODE      VARCHAR2(20),
    DETAIL_STATUS_NAME      VARCHAR2(100),
    CLOSURE_DATE            DATE,
    SUSPEND_START_DATE      DATE,
    SUSPEND_END_DATE        DATE,
    REOPEN_DATE             DATE,
    PHONE_NUMBER            VARCHAR2(50),
    AREA_SIZE               NUMBER(10,2),
    POSTAL_CODE             VARCHAR2(20),
    JIBUN_ADDRESS           VARCHAR2(1000),
    ROAD_ADDRESS            VARCHAR2(1000),
    ROAD_POSTAL_CODE        VARCHAR2(20),
    BUSINESS_NAME           VARCHAR2(500),
    LAST_MODIFIED_DATE      TIMESTAMP,
    DATA_UPDATE_TYPE        VARCHAR2(20),
    DATA_UPDATE_DATE        TIMESTAMP,
    BUSINESS_CATEGORY       VARCHAR2(100),
    COORDINATE_X            NUMBER(15,7),
    COORDINATE_Y            NUMBER(15,7),
    CULTURE_SPORTS_TYPE     VARCHAR2(100),
    CULTURE_BUSINESS_TYPE   VARCHAR2(100),
    TOTAL_FLOORS            NUMBER(6),
    SURROUNDING_ENVIRONMENT VARCHAR2(200),
    PRODUCTION_ITEMS        VARCHAR2(1000),
    FACILITY_AREA           NUMBER(10,2),
    GROUND_FLOORS           NUMBER(6),
    BASEMENT_FLOORS         NUMBER(6),
    BUILDING_PURPOSE        VARCHAR2(200),
    CORRIDOR_WIDTH          NUMBER(10,2),
    LIGHTING_FACILITY_LUX   NUMBER(10),
    KARAOKE_ROOMS_COUNT     NUMBER(6),
    YOUTH_ROOMS_COUNT       NUMBER(6),
    EMERGENCY_STAIRS        VARCHAR2(20),
    EMERGENCY_EXITS         VARCHAR2(20),
    AUTO_VENTILATION        VARCHAR2(20),
    YOUTH_ROOM_AVAILABLE    VARCHAR2(20),
    SPECIAL_LIGHTING        VARCHAR2(20),
    SOUNDPROOF_FACILITY     VARCHAR2(20),
    VIDEO_PLAYER_NAME       VARCHAR2(200),
    LIGHTING_FACILITY_YN    VARCHAR2(20),
    SOUND_FACILITY_YN       VARCHAR2(20),
    CONVENIENCE_FACILITY_YN VARCHAR2(20),
    FIRE_FACILITY_YN        VARCHAR2(20),
    TOTAL_GAME_MACHINES     NUMBER(6),
    EXISTING_BUSINESS_TYPE  VARCHAR2(200),
    PROVIDED_GAMES          VARCHAR2(500),
    VENUE_TYPE              VARCHAR2(100),
    ITEM_NAME               VARCHAR2(500),
    FIRST_REGISTRATION_TIME VARCHAR2(100),
    REGION_TYPE             VARCHAR2(100),
    CREATED_AT              TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE SEQ_KARAOKE_ROOMS START WITH 1 INCREMENT BY 1;
```

## 2. JPA Entity

```java
package com.wherehouse.safety.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "KARAOKE_ROOMS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KaraokeRooms {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "karaoke_room_seq")
    @SequenceGenerator(name = "karaoke_room_seq", sequenceName = "SEQ_KARAOKE_ROOMS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "DISTRICT_CODE", nullable = false, length = 20)
    private String districtCode;

    @Column(name = "MANAGEMENT_NUMBER", length = 100)
    private String managementNumber;

    @Column(name = "LICENSE_DATE")
    private LocalDate licenseDate;

    @Column(name = "CANCEL_DATE")
    private LocalDate cancelDate;

    @Column(name = "BUSINESS_STATUS_CODE", length = 20)
    private String businessStatusCode;

    @Column(name = "BUSINESS_STATUS_NAME", length = 100)
    private String businessStatusName;

    @Column(name = "DETAIL_STATUS_CODE", length = 20)
    private String detailStatusCode;

    @Column(name = "DETAIL_STATUS_NAME", length = 100)
    private String detailStatusName;

    @Column(name = "CLOSURE_DATE")
    private LocalDate closureDate;

    @Column(name = "SUSPEND_START_DATE")
    private LocalDate suspendStartDate;

    @Column(name = "SUSPEND_END_DATE")
    private LocalDate suspendEndDate;

    @Column(name = "REOPEN_DATE")
    private LocalDate reopenDate;

    @Column(name = "PHONE_NUMBER", length = 50)
    private String phoneNumber;

    @Column(name = "AREA_SIZE", precision = 10, scale = 2)
    private BigDecimal areaSize;

    @Column(name = "POSTAL_CODE", length = 20)
    private String postalCode;

    @Column(name = "JIBUN_ADDRESS", length = 1000)
    private String jibunAddress;

    @Column(name = "ROAD_ADDRESS", length = 1000)
    private String roadAddress;

    @Column(name = "ROAD_POSTAL_CODE", length = 20)
    private String roadPostalCode;

    @Column(name = "BUSINESS_NAME", length = 500)
    private String businessName;

    @Column(name = "LAST_MODIFIED_DATE")
    private LocalDateTime lastModifiedDate;

    @Column(name = "DATA_UPDATE_TYPE", length = 20)
    private String dataUpdateType;

    @Column(name = "DATA_UPDATE_DATE")
    private LocalDateTime dataUpdateDate;

    @Column(name = "BUSINESS_CATEGORY", length = 100)
    private String businessCategory;

    @Column(name = "COORDINATE_X", precision = 15, scale = 7)
    private BigDecimal coordinateX;

    @Column(name = "COORDINATE_Y", precision = 15, scale = 7)
    private BigDecimal coordinateY;

    @Column(name = "CULTURE_SPORTS_TYPE", length = 100)
    private String cultureSportsType;

    @Column(name = "CULTURE_BUSINESS_TYPE", length = 100)
    private String cultureBusinessType;

    @Column(name = "TOTAL_FLOORS")
    private Integer totalFloors;

    @Column(name = "SURROUNDING_ENVIRONMENT", length = 200)
    private String surroundingEnvironment;

    @Column(name = "PRODUCTION_ITEMS", length = 1000)
    private String productionItems;

    @Column(name = "FACILITY_AREA", precision = 10, scale = 2)
    private BigDecimal facilityArea;

    @Column(name = "GROUND_FLOORS")
    private Integer groundFloors;

    @Column(name = "BASEMENT_FLOORS")
    private Integer basementFloors;

    @Column(name = "BUILDING_PURPOSE", length = 200)
    private String buildingPurpose;

    @Column(name = "CORRIDOR_WIDTH", precision = 10, scale = 2)
    private BigDecimal corridorWidth;

    @Column(name = "LIGHTING_FACILITY_LUX")
    private Integer lightingFacilityLux;

    @Column(name = "KARAOKE_ROOMS_COUNT")
    private Integer karaokeRoomsCount;

    @Column(name = "YOUTH_ROOMS_COUNT")
    private Integer youthRoomsCount;

    @Column(name = "EMERGENCY_STAIRS", length = 20)
    private String emergencyStairs;

    @Column(name = "EMERGENCY_EXITS", length = 20)
    private String emergencyExits;

    @Column(name = "AUTO_VENTILATION", length = 20)
    private String autoVentilation;

    @Column(name = "YOUTH_ROOM_AVAILABLE", length = 20)
    private String youthRoomAvailable;

    @Column(name = "SPECIAL_LIGHTING", length = 20)
    private String specialLighting;

    @Column(name = "SOUNDPROOF_FACILITY", length = 20)
    private String soundproofFacility;

    @Column(name = "VIDEO_PLAYER_NAME", length = 200)
    private String videoPlayerName;

    @Column(name = "LIGHTING_FACILITY_YN", length = 20)
    private String lightingFacilityYn;

    @Column(name = "SOUND_FACILITY_YN", length = 20)
    private String soundFacilityYn;

    @Column(name = "CONVENIENCE_FACILITY_YN", length = 20)
    private String convenienceFacilityYn;

    @Column(name = "FIRE_FACILITY_YN", length = 20)
    private String fireFacilityYn;

    @Column(name = "TOTAL_GAME_MACHINES")
    private Integer totalGameMachines;

    @Column(name = "EXISTING_BUSINESS_TYPE", length = 200)
    private String existingBusinessType;

    @Column(name = "PROVIDED_GAMES", length = 500)
    private String providedGames;

    @Column(name = "VENUE_TYPE", length = 100)
    private String venueType;

    @Column(name = "ITEM_NAME", length = 500)
    private String itemName;

    @Column(name = "FIRST_REGISTRATION_TIME", length = 100)
    private String firstRegistrationTime;

    @Column(name = "REGION_TYPE", length = 100)
    private String regionType;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

## 3. Repository

```java
package com.wherehouse.safety.repository;

import com.wherehouse.safety.entity.KaraokeRooms;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface KaraokeRoomsRepository extends JpaRepository<KaraokeRooms, Long> {
    
    List<KaraokeRooms> findByDistrictCodeAndBusinessStatusCode(String districtCode, String businessStatusCode);
    
    @Query("SELECT COUNT(k) FROM KaraokeRooms k WHERE k.districtCode = :districtCode AND k.businessStatusCode = '1'")
    Long countActiveByDistrictCode(@Param("districtCode") String districtCode);
    
    @Query("SELECT k.businessCategory, COUNT(k) FROM KaraokeRooms k WHERE k.businessStatusCode = '1' GROUP BY k.businessCategory")
    List<Object[]> countByBusinessCategory();
    
    @Query("SELECT k FROM KaraokeRooms k WHERE k.businessStatusCode = '3'")
    List<KaraokeRooms> findClosedKaraokeRooms();
    
    @Query("SELECT k FROM KaraokeRooms k WHERE k.youthRoomAvailable = 'Y'")
    List<KaraokeRooms> findKaraokeRoomsWithYouthRooms();
    
    boolean existsByManagementNumber(String managementNumber);
}
```

## 4. CSV 로더 Component

```java
package com.wherehouse.safety.component;

import com.opencsv.CSVReader;
import com.wherehouse.safety.entity.KaraokeRooms;
import com.wherehouse.safety.repository.KaraokeRoomsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class KaraokeRoomsDataLoader implements CommandLineRunner {

    private final KaraokeRoomsRepository karaokeRoomsRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.karaoke-rooms-data-path}")
    private String csvFilePath;

    @Override
    @Transactional
    public void run(String... args) {
        if (karaokeRoomsRepository.count() > 0) {
            log.info("노래연습장 데이터 이미 존재. 로딩 스킵");
            return;
        }

        try {
            Resource resource = resourceLoader.getResource(csvFilePath);
            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();

                int totalRows = csvData.size() - 1; // 헤더 제외
                int savedCount = 0;
                int errorCount = 0;

                log.info("노래연습장 데이터 로딩 시작 - 총 {} 건", totalRows);

                for (int i = 1; i < csvData.size(); i++) {
                    String[] row = csvData.get(i);

                    // 진행률 출력 (100건마다)
                    if (i % 100 == 0) {
                        double progress = ((double) (i - 1) / totalRows) * 100;
                        log.info("진행률 : {:.1f}% ({}/{})", progress, i - 1, totalRows);
                    }

                    if (row.length >= 56) {
                        try {
                            KaraokeRooms karaokeRoom = KaraokeRooms.builder()
                                    .districtCode(parseString(row[0]))
                                    .managementNumber(parseString(row[1]))
                                    .licenseDate(parseDate(row[2]))
                                    .cancelDate(parseDate(row[3]))
                                    .businessStatusCode(parseString(row[4]))
                                    .businessStatusName(parseString(row[5]))
                                    .detailStatusCode(parseString(row[6]))
                                    .detailStatusName(parseString(row[7]))
                                    .closureDate(parseDate(row[8]))
                                    .suspendStartDate(parseDate(row[9]))
                                    .suspendEndDate(parseDate(row[10]))
                                    .reopenDate(parseDate(row[11]))
                                    .phoneNumber(parseString(row[12]))
                                    .areaSize(parseBigDecimal(row[13]))
                                    .postalCode(parseString(row[14]))
                                    .jibunAddress(parseString(row[15]))
                                    .roadAddress(parseString(row[16]))
                                    .roadPostalCode(parseString(row[17]))
                                    .businessName(parseString(row[18]))
                                    .lastModifiedDate(parseDateTime(row[19]))
                                    .dataUpdateType(parseString(row[20]))
                                    .dataUpdateDate(parseDateTime(row[21]))
                                    .businessCategory(parseString(row[22]))
                                    .coordinateX(parseBigDecimal(row[23]))
                                    .coordinateY(parseBigDecimal(row[24]))
                                    .cultureSportsType(parseString(row[25]))
                                    .cultureBusinessType(parseString(row[26]))
                                    .totalFloors(parseInteger(row[27]))
                                    .surroundingEnvironment(parseString(row[28]))
                                    .productionItems(parseString(row[29]))
                                    .facilityArea(parseBigDecimal(row[30]))
                                    .groundFloors(parseInteger(row[31]))
                                    .basementFloors(parseInteger(row[32]))
                                    .buildingPurpose(parseString(row[33]))
                                    .corridorWidth(parseBigDecimal(row[34]))
                                    .lightingFacilityLux(parseInteger(row[35]))
                                    .karaokeRoomsCount(parseInteger(row[36]))
                                    .youthRoomsCount(parseInteger(row[37]))
                                    .emergencyStairs(parseString(row[38]))
                                    .emergencyExits(parseString(row[39]))
                                    .autoVentilation(parseString(row[40]))
                                    .youthRoomAvailable(parseString(row[41]))
                                    .specialLighting(parseString(row[42]))
                                    .soundproofFacility(parseString(row[43]))
                                    .videoPlayerName(parseString(row[44]))
                                    .lightingFacilityYn(parseString(row[45]))
                                    .soundFacilityYn(parseString(row[46]))
                                    .convenienceFacilityYn(parseString(row[47]))
                                    .fireFacilityYn(parseString(row[48]))
                                    .totalGameMachines(parseInteger(row[49]))
                                    .existingBusinessType(row.length > 50 ? parseString(row[50]) : "데이터없음")
                                    .providedGames(row.length > 51 ? parseString(row[51]) : "데이터없음")
                                    .venueType(row.length > 52 ? parseString(row[52]) : "데이터없음")
                                    .itemName(row.length > 53 ? parseString(row[53]) : "데이터없음")
                                    .firstRegistrationTime(row.length > 54 ? parseString(row[54]) : "데이터없음")
                                    .regionType(row.length > 55 ? parseString(row[55]) : "데이터없음")
                                    .build();

                            karaokeRoomsRepository.save(karaokeRoom);
                            savedCount++;
                        } catch (Exception e) {
                            errorCount++;
                            log.warn("{}번째 행 저장 실패: {}", i, e.getMessage());
                        }
                    } else {
                        errorCount++;
                        log.warn("{}번째 행 컬럼 부족 ({}개 < 56개)", i, row.length);
                    }
                }

                log.info("노래연습장 데이터 로딩 완료 - 성공: {}건, 실패: {}건, 전체: {}건",
                        savedCount, errorCount, totalRows);
            }
        } catch (Exception e) {
            log.error("CSV 로딩 실패: {}", e.getMessage());
        }
    }

    private String parseString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "데이터없음";
        }
        return value.trim();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return LocalDate.of(1900, 1, 1); // 기본값: 1900-01-01
        }
        try {
            return LocalDate.parse(value.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            return LocalDate.of(1900, 1, 1); // 파싱 실패시도 기본값
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return LocalDateTime.of(1900, 1, 1, 0, 0, 0); // 기본값: 1900-01-01 00:00:00
        }
        try {
            // "59:59.0" 같은 시간 형식 처리
            if (value.contains(":") && !value.contains("-")) {
                return LocalDateTime.of(1900, 1, 1, 0, 0, 0);
            }
            return LocalDateTime.parse(value.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (Exception e) {
            return LocalDateTime.of(1900, 1, 1, 0, 0, 0); // 파싱 실패시도 기본값
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
```

## 5. application.yml 추가 설정

```yaml
# CSV 설정 (기존 설정에 추가)
app:
  csv:
    crime-data-path: classpath:data/5대범죄발생현황_20250822144657.csv
    danran-bars-data-path: classpath:data/단란주점_데이터.csv
    pc-bangs-data-path: classpath:data/PC방_데이터.csv
    karaoke-rooms-data-path: classpath:data/노래연습장_데이터.csv
```

파일 위치: `src/main/resources/data/노래연습장_데이터.csv`

## 주요 특징

1. **테이블명**: PC_BANGS → KARAOKE_ROOMS로 변경
2. **엔티티명**: PcBangs → KaraokeRooms로 변경
3. **시퀀스명**: SEQ_PC_BANGS → SEQ_KARAOKE_ROOMS로 변경
4. **컬럼 매핑**: 노래방실수(KARAOKE_ROOMS_COUNT), 청소년실수(YOUTH_ROOMS_COUNT) 등 노래연습장에 특화된 컬럼명 사용
5. **추가 쿼리**: 청소년실 보유 노래연습장 조회, 폐업 노래연습장 조회 등 노래연습장 특성에 맞는 메서드 추가
6. **데이터 파싱**: 시간 형식("59:59.0") 등 특수한 데이터 형식에 대한 예외 처리 추가