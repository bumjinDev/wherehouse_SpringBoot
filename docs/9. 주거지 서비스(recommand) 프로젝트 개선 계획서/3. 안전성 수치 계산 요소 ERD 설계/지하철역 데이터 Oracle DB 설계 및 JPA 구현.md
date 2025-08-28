# 지하철역 데이터 Oracle DB 설계 및 JPA 구현

## 1. Oracle 테이블 DDL

```sql
CREATE TABLE SUBWAY_STATION (
    STATION_CODE                VARCHAR2(10) PRIMARY KEY,
    STATION_NAME_KOR            VARCHAR2(100) NOT NULL,
    STATION_NAME_ENG            VARCHAR2(200),
    LINE_NUMBER                 VARCHAR2(10),
    EXTERNAL_CODE               VARCHAR2(20),
    STATION_NAME_CHN            VARCHAR2(100),
    STATION_NAME_JPN            VARCHAR2(100),
    CREATED_AT                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT UK_STATION_EXTERNAL UNIQUE (EXTERNAL_CODE, LINE_NUMBER)
);
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
    crime-data-path: classpath:data/5대범죄발생현황_20250822144657.csv
    subway-station-path: classpath:data/subway_station_data.csv
```

## 4. JPA Entity

```java
package com.wherehouse.subway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "SUBWAY_STATION")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubwayStation {

    @Id
    @Column(name = "STATION_CODE", length = 10)
    private String stationCode;

    @Column(name = "STATION_NAME_KOR", length = 100, nullable = false)
    private String stationNameKor;

    @Column(name = "STATION_NAME_ENG", length = 200)
    private String stationNameEng;

    @Column(name = "LINE_NUMBER", length = 10)
    private String lineNumber;

    @Column(name = "EXTERNAL_CODE", length = 20)
    private String externalCode;

    @Column(name = "STATION_NAME_CHN", length = 100)
    private String stationNameChn;

    @Column(name = "STATION_NAME_JPN", length = 100)
    private String stationNameJpn;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        
        // NULL 값들을 기본값으로 설정
        if (stationNameEng == null) stationNameEng = "데이터없음";
        if (lineNumber == null) lineNumber = "데이터없음";
        if (externalCode == null) externalCode = "데이터없음";
        if (stationNameChn == null) stationNameChn = "数据不存在";
        if (stationNameJpn == null) stationNameJpn = "データなし";
    }
}
```

## 5. Repository

```java
package com.wherehouse.subway.repository;

import com.wherehouse.subway.entity.SubwayStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubwayStationRepository extends JpaRepository<SubwayStation, String> {

    Optional<SubwayStation> findByStationCode(String stationCode);

    List<SubwayStation> findByStationNameKorContaining(String stationName);

    List<SubwayStation> findByLineNumber(String lineNumber);

    @Query("SELECT s FROM SubwayStation s WHERE s.lineNumber = :lineNumber ORDER BY s.externalCode")
    List<SubwayStation> findByLineNumberOrderByExternalCode(@Param("lineNumber") String lineNumber);

    @Query("SELECT DISTINCT s.lineNumber FROM SubwayStation s ORDER BY s.lineNumber")
    List<String> findDistinctLineNumbers();

    boolean existsByStationCode(String stationCode);
}
```

## 6. CSV 로더 Component

```java
package com.wherehouse.subway.component;

import com.opencsv.CSVReader;
import com.wherehouse.subway.entity.SubwayStation;
import com.wherehouse.subway.repository.SubwayStationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubwayStationDataLoader implements CommandLineRunner {

    private final SubwayStationRepository subwayStationRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.subway-station-path}")
    private String csvFilePath;

    private static final int BATCH_SIZE = 500;

    @Override
    public void run(String... args) {
        long existingCount = subwayStationRepository.count();
        if (existingCount > 0) {
            log.info("지하철역 데이터 이미 존재 ({}개). 로딩 스킵", existingCount);
            return;
        }
        
        try {
            Resource resource = resourceLoader.getResource(csvFilePath);
            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();
                log.info("CSV 파일에서 {} 행 읽음 (헤더 포함)", csvData.size());

                List<SubwayStation> batch = new ArrayList<>();
                int totalSaved = 0;
                int totalErrors = 0;
                
                for (int i = 1; i < csvData.size(); i++) {
                    String[] row = csvData.get(i);
                    try {
                        if (row.length >= 6) {
                            SubwayStation station = createSubwayStation(row);
                            batch.add(station);
                            
                            if (batch.size() >= BATCH_SIZE || i == csvData.size() - 1) {
                                int saved = saveBatch(batch);
                                totalSaved += saved;
                                batch.clear();
                                
                                if (totalSaved % (BATCH_SIZE * 2) == 0) {
                                    log.info("진행 상황: {} 개 데이터 저장 완료", totalSaved);
                                }
                            }
                        }
                    } catch (Exception e) {
                        totalErrors++;
                        log.warn("행 {} 처리 중 오류: {}", i, e.getMessage());
                    }
                }
                
                long finalCount = subwayStationRepository.count();
                log.info("로딩 완료 - 처리 시도: {} 개, 최종 DB 저장: {} 개, 오류: {} 개", 
                    totalSaved, finalCount, totalErrors);
            }
        } catch (Exception e) {
            log.error("지하철역 CSV 로딩 실패: {}", e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int saveBatch(List<SubwayStation> batch) {
        try {
            List<SubwayStation> saved = subwayStationRepository.saveAll(batch);
            return saved.size();
        } catch (Exception e) {
            log.error("배치 저장 실패: {}", e.getMessage());
            
            int savedCount = 0;
            for (SubwayStation station : batch) {
                try {
                    subwayStationRepository.save(station);
                    savedCount++;
                } catch (Exception individualError) {
                    log.warn("개별 저장 실패 - 역코드: {}", station.getStationCode());
                }
            }
            
            log.info("개별 저장 완료: {} / {} 개", savedCount, batch.size());
            return savedCount;
        }
    }

    private SubwayStation createSubwayStation(String[] row) {
        return SubwayStation.builder()
                .stationCode(parseString(row[0]))
                .stationNameKor(parseString(row[1]))
                .stationNameEng(parseString(row[2]))
                .lineNumber(parseString(row[3]))
                .externalCode(parseString(row[4]))
                .stationNameChn(row.length > 5 ? parseString(row[5]) : null)
                .stationNameJpn(row.length > 6 ? parseString(row[6]) : null)
                .build();
    }

    private String parseString(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return null;
        }
        String trimmed = value.trim();
        
        if (trimmed.length() > 190) {
            trimmed = trimmed.substring(0, 190);
            log.debug("문자열 길이 제한으로 자름: 원본 길이 {} -> 190", value.length());
        }
        
        return trimmed;
    }
}
```

파일 위치: `src/main/resources/data/subway_station_data.csv`