# 문화체육업 데이터 Oracle DB 설계 및 JPA 구현

## 1. Oracle 테이블 DDL

```sql
CREATE TABLE CULTURAL_SPORTS_BUSINESS (
    ID                          NUMBER(19) PRIMARY KEY,
    LOCAL_GOVT_CODE             VARCHAR2(50) NOT NULL,
    MANAGEMENT_NO               VARCHAR2(100) NOT NULL,
    LICENSE_DATE                DATE,
    LICENSE_CANCEL_DATE         DATE,
    BUSINESS_STATUS_CODE        VARCHAR2(20),
    BUSINESS_STATUS_NAME        VARCHAR2(100),
    DETAIL_STATUS_CODE          VARCHAR2(20),
    DETAIL_STATUS_NAME          VARCHAR2(100),
    CLOSURE_DATE                DATE,
    SUSPENSION_START_DATE       DATE,
    SUSPENSION_END_DATE         DATE,
    REOPEN_DATE                 DATE,
    PHONE_NUMBER                VARCHAR2(50),
    LOCATION_AREA               NUMBER(15,2),
    LOCATION_POSTAL_CODE        VARCHAR2(50),
    JIBUN_ADDRESS               VARCHAR2(500),
    ROAD_ADDRESS                VARCHAR2(500),
    ROAD_POSTAL_CODE            VARCHAR2(50),
    BUSINESS_NAME               VARCHAR2(300),
    LAST_UPDATE_DATE            DATE,
    DATA_UPDATE_TYPE            VARCHAR2(20),
    DATA_UPDATE_TIME            VARCHAR2(50),
    BUSINESS_TYPE_NAME          VARCHAR2(100),
    COORD_X                     NUMBER(15,7),
    COORD_Y                     NUMBER(15,7),
    CULTURE_SPORTS_TYPE_NAME    VARCHAR2(100),
    CULTURE_BUSINESS_TYPE_NAME  VARCHAR2(100),
    TOTAL_FLOORS                NUMBER(10),
    SURROUNDING_ENVIRONMENT     VARCHAR2(500),
    PRODUCTION_ITEM_CONTENT     VARCHAR2(1000),
    FACILITY_AREA               NUMBER(15,2),
    GROUND_FLOORS               NUMBER(10),
    UNDERGROUND_FLOORS          NUMBER(10),
    BUILDING_USE_NAME           VARCHAR2(200),
    PASSAGE_WIDTH               NUMBER(10,2),
    LIGHTING_BRIGHTNESS         NUMBER(10,2),
    KARAOKE_ROOMS               NUMBER(10),
    YOUTH_ROOMS                 NUMBER(10),
    EMERGENCY_STAIRS            VARCHAR2(50),
    EMERGENCY_EXIT              VARCHAR2(50),
    AUTO_VENTILATION            VARCHAR2(50),
    YOUTH_ROOM_EXISTS           VARCHAR2(50),
    SPECIAL_LIGHTING            VARCHAR2(50),
    SOUNDPROOF_FACILITY         VARCHAR2(50),
    VIDEO_PLAYER_NAME           VARCHAR2(500),
    LIGHTING_FACILITY           VARCHAR2(50),
    SOUND_FACILITY              VARCHAR2(50),
    CONVENIENCE_FACILITY        VARCHAR2(50),
    FIRE_SAFETY_FACILITY        VARCHAR2(50),
    TOTAL_GAME_MACHINES         NUMBER(10),
    EXISTING_GAME_BUSINESS_TYPE VARCHAR2(200),
    PROVIDED_GAME_NAME          VARCHAR2(500),
    PERFORMANCE_HALL_TYPE       VARCHAR2(100),
    ITEM_NAME                   VARCHAR2(300),
    FIRST_REGISTRATION_TIME     VARCHAR2(50),
    REGION_TYPE_NAME            VARCHAR2(100),
    CREATED_AT                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE SEQ_CULTURAL_SPORTS_BUSINESS START WITH 1 INCREMENT BY 1;
```

## 2. build.gradle (전체 파일)

```gradle
plugins {
	id 'java'
	id 'org.springframework.boot' version '3.3.5'
	id 'io.spring.dependency-management' version '1.1.6'
}

group = 'com.wherehouse'
version = '0.1.0'

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

configurations {
	compileOnly {
		extendsFrom annotationProcessor
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// Spring Boot 기본
	implementation 'org.springframework.boot:spring-boot-starter'
	implementation 'org.springframework.boot:spring-boot-starter-web'
	implementation 'org.springframework.boot:spring-boot-starter-webflux'
	
	// JPA 및 데이터베이스
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
	implementation 'jakarta.persistence:jakarta.persistence-api:3.1.0'
	implementation 'org.hibernate:hibernate-core:6.2.2.Final'
	implementation 'org.springframework.boot:spring-boot-starter-jdbc'
	implementation 'com.oracle.database.jdbc:ojdbc8:19.8.0.0'
	
	// MyBatis
	implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:2.2.2'
	implementation 'org.mybatis:mybatis-spring:3.0.3'
	
	// XML 처리용
	implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-xml'
	implementation 'javax.xml.bind:jaxb-api:2.3.1'
	implementation 'org.glassfish.jaxb:jaxb-runtime:2.3.1'

	// Lombok
	compileOnly 'org.projectlombok:lombok'
	annotationProcessor 'org.projectlombok:lombok'

	// OpenCSV
	implementation 'com.opencsv:opencsv:5.9'

	// Spring Security
	implementation 'org.springframework.boot:spring-boot-starter-security'
	
	// JSP
	implementation 'org.apache.tomcat.embed:tomcat-embed-jasper'
	implementation 'org.glassfish.web:jakarta.servlet.jsp.jstl:2.0.0'
	
	// JWT
	implementation 'io.jsonwebtoken:jjwt-api:0.11.5'
	implementation 'io.jsonwebtoken:jjwt-impl:0.11.5'
	implementation 'io.jsonwebtoken:jjwt-jackson:0.11.5'
	implementation group: 'org.javassist', name: 'javassist', version: '3.15.0-GA'
	
	// Redis
	implementation 'org.springframework.boot:spring-boot-starter-data-redis'

	// Elasticsearch
	implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'
	
	// JDBC
	implementation 'org.springframework.boot:spring-boot-starter-data-jdbc'
	
	// Validation
	implementation 'org.springframework.boot:spring-boot-starter-validation'

	// 테스트
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	testImplementation 'io.projectreactor:reactor-test'
	testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.test {
	useJUnitPlatform()
}

tasks.withType(JavaCompile) {
	options.compilerArgs << '-parameters'
}
```

## 3. application.yml (전체 파일)

```yaml
spring:
  application:
    name: wherehouse

  datasource:
    driver-class-name: oracle.jdbc.OracleDriver
    url: jdbc:oracle:thin:@127.0.0.1:1521:xe
    username: SCOTT
    password: tiger

    hikari:
      auto-commit: false
      idle-timeout: 30000
      max-lifetime: 1800000

  data:
    redis:
      host: 43.202.178.156
      port: 6379
      timeout: 0
      lettuce:
        pool:
          max-active: 100
          max-idle: 50
          min-idle: 10
          max-wait: -1ms
          time-between-eviction-runs: 10s

  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.OracleDialect
        format_sql: true
        default_batch_fetch_size: 50
        jdbc.fetch_size: 100
        cache.use_query_cache: false
        physical_naming_strategy: org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
    open-in-view: false

  mvc:
    view:
      prefix: /WEB-INF/view/
      suffix: .jsp

mybatis:
  mapper-locations: classpath:/mapper/*Mapper.xml
  type-aliases-package: com.wherehouse.information.model
  configuration:
    jdbc-type-for-null: null

server:
  port: 8185
  servlet:
    context-path: /wherehouse

logging:
  file:
    name: log/wherehouse.log
  level:
    root: info
    com.wherehouse: debug
    org.hibernate.SQL: debug

# CSV 설정
app:
  csv:
    cultural-sports-data-path: classpath:data/문화체육업데이터.csv
```

## 4. JPA Entity

```java
package com.wherehouse.safety.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "CULTURAL_SPORTS_BUSINESS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CulturalSportsBusiness {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cultural_sports_seq")
    @SequenceGenerator(name = "cultural_sports_seq", sequenceName = "SEQ_CULTURAL_SPORTS_BUSINESS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "LOCAL_GOVT_CODE", nullable = false, length = 50)
    private String localGovtCode;

    @Column(name = "MANAGEMENT_NO", nullable = false, length = 100)
    private String managementNo;

    @Column(name = "LICENSE_DATE")
    private LocalDate licenseDate;

    @Column(name = "LICENSE_CANCEL_DATE")
    private LocalDate licenseCancelDate;

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

    @Column(name = "SUSPENSION_START_DATE")
    private LocalDate suspensionStartDate;

    @Column(name = "SUSPENSION_END_DATE")
    private LocalDate suspensionEndDate;

    @Column(name = "REOPEN_DATE")
    private LocalDate reopenDate;

    @Column(name = "PHONE_NUMBER", length = 50)
    private String phoneNumber;

    @Column(name = "LOCATION_AREA")
    private Double locationArea;

    @Column(name = "LOCATION_POSTAL_CODE", length = 50)
    private String locationPostalCode;

    @Column(name = "JIBUN_ADDRESS", length = 500)
    private String jibunAddress;

    @Column(name = "ROAD_ADDRESS", length = 500)
    private String roadAddress;

    @Column(name = "ROAD_POSTAL_CODE", length = 50)
    private String roadPostalCode;

    @Column(name = "BUSINESS_NAME", length = 300)
    private String businessName;

    @Column(name = "LAST_UPDATE_DATE")
    private LocalDate lastUpdateDate;

    @Column(name = "DATA_UPDATE_TYPE", length = 20)
    private String dataUpdateType;

    @Column(name = "DATA_UPDATE_TIME", length = 50)
    private String dataUpdateTime;

    @Column(name = "BUSINESS_TYPE_NAME", length = 100)
    private String businessTypeName;

    @Column(name = "COORD_X")
    private Double coordX;

    @Column(name = "COORD_Y")
    private Double coordY;

    @Column(name = "CULTURE_SPORTS_TYPE_NAME", length = 100)
    private String cultureSportsTypeName;

    @Column(name = "CULTURE_BUSINESS_TYPE_NAME", length = 100)
    private String cultureBusinessTypeName;

    @Column(name = "TOTAL_FLOORS")
    private Integer totalFloors;

    @Column(name = "SURROUNDING_ENVIRONMENT", length = 500)
    private String surroundingEnvironment;

    @Column(name = "PRODUCTION_ITEM_CONTENT", length = 1000)
    private String productionItemContent;

    @Column(name = "FACILITY_AREA")
    private Double facilityArea;

    @Column(name = "GROUND_FLOORS")
    private Integer groundFloors;

    @Column(name = "UNDERGROUND_FLOORS")
    private Integer undergroundFloors;

    @Column(name = "BUILDING_USE_NAME", length = 200)
    private String buildingUseName;

    @Column(name = "PASSAGE_WIDTH")
    private Double passageWidth;

    @Column(name = "LIGHTING_BRIGHTNESS")
    private Double lightingBrightness;

    @Column(name = "KARAOKE_ROOMS")
    private Integer karaokeRooms;

    @Column(name = "YOUTH_ROOMS")
    private Integer youthRooms;

    @Column(name = "EMERGENCY_STAIRS", length = 50)
    private String emergencyStairs;

    @Column(name = "EMERGENCY_EXIT", length = 50)
    private String emergencyExit;

    @Column(name = "AUTO_VENTILATION", length = 50)
    private String autoVentilation;

    @Column(name = "YOUTH_ROOM_EXISTS", length = 50)
    private String youthRoomExists;

    @Column(name = "SPECIAL_LIGHTING", length = 50)
    private String specialLighting;

    @Column(name = "SOUNDPROOF_FACILITY", length = 50)
    private String soundproofFacility;

    @Column(name = "VIDEO_PLAYER_NAME", length = 500)
    private String videoPlayerName;

    @Column(name = "LIGHTING_FACILITY", length = 50)
    private String lightingFacility;

    @Column(name = "SOUND_FACILITY", length = 50)
    private String soundFacility;

    @Column(name = "CONVENIENCE_FACILITY", length = 50)
    private String convenienceFacility;

    @Column(name = "FIRE_SAFETY_FACILITY", length = 50)
    private String fireSafetyFacility;

    @Column(name = "TOTAL_GAME_MACHINES")
    private Integer totalGameMachines;

    @Column(name = "EXISTING_GAME_BUSINESS_TYPE", length = 200)
    private String existingGameBusinessType;

    @Column(name = "PROVIDED_GAME_NAME", length = 500)
    private String providedGameName;

    @Column(name = "PERFORMANCE_HALL_TYPE", length = 100)
    private String performanceHallType;

    @Column(name = "ITEM_NAME", length = 300)
    private String itemName;

    @Column(name = "FIRST_REGISTRATION_TIME", length = 50)
    private String firstRegistrationTime;

    @Column(name = "REGION_TYPE_NAME", length = 100)
    private String regionTypeName;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

## 5. Repository

```java
package com.wherehouse.safety.repository;

import com.wherehouse.safety.entity.CulturalSportsBusiness;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CulturalSportsBusinessRepository extends JpaRepository<CulturalSportsBusiness, Long> {
    List<CulturalSportsBusiness> findByBusinessStatusName(String businessStatusName);
    List<CulturalSportsBusiness> findByCultureSportsTypeName(String cultureSportsTypeName);
    Optional<CulturalSportsBusiness> findByManagementNo(String managementNo);
    boolean existsByManagementNo(String managementNo);
    
    @Query("SELECT c FROM CulturalSportsBusiness c WHERE c.businessName LIKE %:name%")
    List<CulturalSportsBusiness> findByBusinessNameContaining(@Param("name") String name);
    
    @Query("SELECT c FROM CulturalSportsBusiness c WHERE c.coordX BETWEEN :minX AND :maxX AND c.coordY BETWEEN :minY AND :maxY")
    List<CulturalSportsBusiness> findByLocationBounds(@Param("minX") Double minX, @Param("maxX") Double maxX, 
                                                     @Param("minY") Double minY, @Param("maxY") Double maxY);
    
    @Query("SELECT COUNT(c) FROM CulturalSportsBusiness c WHERE c.businessStatusName = :statusName")
    Long countByBusinessStatus(@Param("statusName") String statusName);
    
    @Query("SELECT COUNT(c) FROM CulturalSportsBusiness c WHERE c.cultureSportsTypeName = :typeName")
    Long countByCultureSportsType(@Param("typeName") String typeName);
}
```

## 6. CSV 로더 Component

```java
package com.wherehouse.safety.component;

import com.opencsv.CSVReader;
import com.wherehouse.safety.entity.CulturalSportsBusiness;
import com.wherehouse.safety.repository.CulturalSportsBusinessRepository;
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
                                .emergencyStairs(processStringWithLength(row[38], 50, "비상계단여부"))
                                .emergencyExit(processStringWithLength(row[39], 50, "비상구여부"))
                                .autoVentilation(processStringWithLength(row[40], 50, "자동환기여부"))
                                .youthRoomExists(processStringWithLength(row[41], 50, "청소년실여부"))
                                .specialLighting(processStringWithLength(row[42], 50, "특수조명여부"))
                                .soundproofFacility(processStringWithLength(row[43], 50, "방음시설여부"))
                                .videoPlayerName(processStringWithLength(row[44], 500, "비디오재생기명"))
                                .lightingFacility(processStringWithLength(row[45], 50, "조명시설유무"))
                                .soundFacility(processStringWithLength(row[46], 50, "음향시설여부"))
                                .convenienceFacility(processStringWithLength(row[47], 50, "편의시설여부"))
                                .fireSafetyFacility(processStringWithLength(row[48], 50, "소방시설여부"))
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
```

파일 위치: `src/main/resources/data/문화체육업데이터.csv`