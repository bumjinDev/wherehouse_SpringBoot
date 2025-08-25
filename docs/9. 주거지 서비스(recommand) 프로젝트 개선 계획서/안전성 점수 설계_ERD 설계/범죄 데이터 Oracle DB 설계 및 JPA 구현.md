# 범죄 데이터 Oracle DB 설계 및 JPA 구현

## 1. Oracle 테이블 DDL

```sql
CREATE TABLE CRIME_STATISTICS (
    ID                      NUMBER(19) PRIMARY KEY,
    DISTRICT_NAME           VARCHAR2(50) NOT NULL,
    YEAR                    NUMBER(4) DEFAULT 2023,
    TOTAL_OCCURRENCE        NUMBER(10) DEFAULT 0,
    TOTAL_ARREST           NUMBER(10) DEFAULT 0,
    MURDER_OCCURRENCE      NUMBER(6) DEFAULT 0,
    MURDER_ARREST          NUMBER(6) DEFAULT 0,
    ROBBERY_OCCURRENCE     NUMBER(6) DEFAULT 0,
    ROBBERY_ARREST         NUMBER(6) DEFAULT 0,
    SEXUAL_CRIME_OCCURRENCE NUMBER(6) DEFAULT 0,
    SEXUAL_CRIME_ARREST    NUMBER(6) DEFAULT 0,
    THEFT_OCCURRENCE       NUMBER(8) DEFAULT 0,
    THEFT_ARREST          NUMBER(8) DEFAULT 0,
    VIOLENCE_OCCURRENCE    NUMBER(8) DEFAULT 0,
    VIOLENCE_ARREST       NUMBER(8) DEFAULT 0,
    CREATED_AT            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT UK_CRIME_DISTRICT_YEAR UNIQUE (DISTRICT_NAME, YEAR)
);

CREATE SEQUENCE SEQ_CRIME_STATISTICS START WITH 1 INCREMENT BY 1;
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
@Table(name = "CRIME_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrimeStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "crime_seq")
    @SequenceGenerator(name = "crime_seq", sequenceName = "SEQ_CRIME_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "DISTRICT_NAME", nullable = false, length = 50)
    private String districtName;

    @Column(name = "YEAR")
    private Integer year = 2023;

    @Column(name = "TOTAL_OCCURRENCE")
    private Integer totalOccurrence = 0;

    @Column(name = "TOTAL_ARREST")
    private Integer totalArrest = 0;

    @Column(name = "MURDER_OCCURRENCE")
    private Integer murderOccurrence = 0;

    @Column(name = "MURDER_ARREST")
    private Integer murderArrest = 0;

    @Column(name = "ROBBERY_OCCURRENCE")
    private Integer robberyOccurrence = 0;

    @Column(name = "ROBBERY_ARREST")
    private Integer robberyArrest = 0;

    @Column(name = "SEXUAL_CRIME_OCCURRENCE")
    private Integer sexualCrimeOccurrence = 0;

    @Column(name = "SEXUAL_CRIME_ARREST")
    private Integer sexualCrimeArrest = 0;

    @Column(name = "THEFT_OCCURRENCE")
    private Integer theftOccurrence = 0;

    @Column(name = "THEFT_ARREST")
    private Integer theftArrest = 0;

    @Column(name = "VIOLENCE_OCCURRENCE")
    private Integer violenceOccurrence = 0;

    @Column(name = "VIOLENCE_ARREST")
    private Integer violenceArrest = 0;

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

import com.wherehouse.safety.entity.CrimeStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CrimeStatisticsRepository extends JpaRepository<CrimeStatistics, Long> {
    Optional<CrimeStatistics> findByDistrictName(String districtName);
    List<CrimeStatistics> findAllByOrderByTotalOccurrenceDesc();
    boolean existsByDistrictName(String districtName);
}
```

## 6. CSV 로더 Component

```java
package com.wherehouse.safety.component;

import com.opencsv.CSVReader;
import com.wherehouse.safety.entity.CrimeStatistics;
import com.wherehouse.safety.repository.CrimeStatisticsRepository;
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
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CrimeDataLoader implements CommandLineRunner {

    private final CrimeStatisticsRepository crimeRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.crime-data-path}")
    private String csvFilePath;

    @Override
    @Transactional
    public void run(String... args) {
        if (crimeRepository.count() > 0) {
            log.info("데이터 이미 존재. 로딩 스킵");
            return;
        }
        
        try {
            Resource resource = resourceLoader.getResource(csvFilePath);
            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();
                
                int savedCount = 0;
                for (int i = 5; i < csvData.size(); i++) {
                    String[] row = csvData.get(i);
                    if (row.length >= 14 && "합계".equals(row[0]) && !row[1].equals("소계")) {
                        CrimeStatistics crime = CrimeStatistics.builder()
                                .districtName(row[1].trim())
                                .year(2023)
                                .totalOccurrence(parseInteger(row[2]))
                                .totalArrest(parseInteger(row[3]))
                                .murderOccurrence(parseInteger(row[4]))
                                .murderArrest(parseInteger(row[5]))
                                .robberyOccurrence(parseInteger(row[6]))
                                .robberyArrest(parseInteger(row[7]))
                                .sexualCrimeOccurrence(parseInteger(row[8]))
                                .sexualCrimeArrest(parseInteger(row[9]))
                                .theftOccurrence(parseInteger(row[10]))
                                .theftArrest(parseInteger(row[11]))
                                .violenceOccurrence(parseInteger(row[12]))
                                .violenceArrest(parseInteger(row[13]))
                                .build();
                        
                        crimeRepository.save(crime);
                        savedCount++;
                    }
                }
                log.info("{} 개 자치구 데이터 저장 완료", savedCount);
            }
        } catch (Exception e) {
            log.error("CSV 로딩 실패: {}", e.getMessage());
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
```

파일 위치: `src/main/resources/data/5대범죄발생현황_20250822144657.csv`