# 경찰시설 데이터 Oracle DB 설계 및 JPA 구현

## 1. Oracle 테이블 DDL

```sql
CREATE TABLE POLICE_FACILITY (
    ID                      NUMBER(19) PRIMARY KEY,
    SERIAL_NO               NUMBER(10),
    CITY_PROVINCE           VARCHAR2(50) NOT NULL,
    POLICE_STATION          VARCHAR2(100) NOT NULL,
    FACILITY_NAME           VARCHAR2(100) NOT NULL,
    FACILITY_TYPE           VARCHAR2(50) NOT NULL,
    PHONE_NUMBER            VARCHAR2(50),
    ADDRESS                 VARCHAR2(500) NOT NULL,
    COORD_X                 NUMBER(15,7),
    COORD_Y                 NUMBER(15,7),
    CREATED_AT              TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE SEQ_POLICE_FACILITY START WITH 1 INCREMENT BY 1;
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
    police-facility-data-path: classpath:data/경찰시설데이터.csv
```

## 4. JPA Entity

```java
package com.wherehouse.safety.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "POLICE_FACILITY")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PoliceFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "police_facility_seq")
    @SequenceGenerator(name = "police_facility_seq", sequenceName = "SEQ_POLICE_FACILITY", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SERIAL_NO")
    private Integer serialNo;

    @Column(name = "CITY_PROVINCE", nullable = false, length = 50)
    private String cityProvince;

    @Column(name = "POLICE_STATION", nullable = false, length = 100)
    private String policeStation;

    @Column(name = "FACILITY_NAME", nullable = false, length = 100)
    private String facilityName;

    @Column(name = "FACILITY_TYPE", nullable = false, length = 50)
    private String facilityType;

    @Column(name = "PHONE_NUMBER", length = 50)
    private String phoneNumber;

    @Column(name = "ADDRESS", nullable = false, length = 500)
    private String address;

    @Column(name = "COORD_X")
    private Double coordX;

    @Column(name = "COORD_Y")
    private Double coordY;

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

import com.wherehouse.safety.entity.PoliceFacility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PoliceFacilityRepository extends JpaRepository<PoliceFacility, Long> {
    List<PoliceFacility> findByFacilityType(String facilityType);
    List<PoliceFacility> findByPoliceStation(String policeStation);
    List<PoliceFacility> findByCityProvince(String cityProvince);
    
    @Query("SELECT p FROM PoliceFacility p WHERE p.facilityName LIKE %:name%")
    List<PoliceFacility> findByFacilityNameContaining(@Param("name") String name);
    
    @Query("SELECT p FROM PoliceFacility p WHERE p.coordX BETWEEN :minX AND :maxX AND p.coordY BETWEEN :minY AND :maxY")
    List<PoliceFacility> findByLocationBounds(@Param("minX") Double minX, @Param("maxX") Double maxX, 
                                             @Param("minY") Double minY, @Param("maxY") Double maxY);
    
    @Query("SELECT COUNT(p) FROM PoliceFacility p WHERE p.facilityType = :type")
    Long countByFacilityType(@Param("type") String type);
    
    @Query("SELECT COUNT(p) FROM PoliceFacility p WHERE p.policeStation = :station")
    Long countByPoliceStation(@Param("station") String station);
    
    // 서울시 경찰시설만 조회
    @Query("SELECT p FROM PoliceFacility p WHERE p.cityProvince LIKE '%서울%'")
    List<PoliceFacility> findSeoulPoliceFacilities();
    
    // 지구대만 조회
    @Query("SELECT p FROM PoliceFacility p WHERE p.facilityType = '지구대'")
    List<PoliceFacility> findAllDistrictOffices();
    
    // 파출소만 조회
    @Query("SELECT p FROM PoliceFacility p WHERE p.facilityType = '파출소'")
    List<PoliceFacility> findAllPoliceBoxes();
}
```

## 6. CSV 로더 Component

```java
package com.wherehouse.safety.component;

import com.opencsv.CSVReader;
import com.wherehouse.safety.entity.PoliceFacility;
import com.wherehouse.safety.repository.PoliceFacilityRepository;
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
public class PoliceFacilityDataLoader implements CommandLineRunner {

    private final PoliceFacilityRepository policeFacilityRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.police-facility-data-path}")
    private String csvFilePath;

    @Override
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
```

파일 위치: `src/main/resources/data/경찰시설데이터.csv`