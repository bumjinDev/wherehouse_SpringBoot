# 편의점 데이터 Oracle DB 설계 및 JPA 구현

## 1. Oracle 테이블 DDL

```sql
CREATE TABLE CONVENIENCE_STORE_DATA (
    ID                      NUMBER(19) PRIMARY KEY,
    OBJT_ID                 NUMBER(38) NOT NULL,
    FCLTY_TY                VARCHAR2(500) DEFAULT '편의점',
    FCLTY_CD                VARCHAR2(6) DEFAULT '509010',
    FCLTY_NM                VARCHAR2(500) NOT NULL,
    ADRES                   VARCHAR2(500),
    RN_ADRES                VARCHAR2(500),
    TELNO                   VARCHAR2(20),
    CTPRVN_CD               VARCHAR2(2),
    SGG_CD                  VARCHAR2(5),
    EMD_CD                  VARCHAR2(8),
    X_COORDINATE            NUMBER,
    Y_COORDINATE            NUMBER,
    DATA_YR                 VARCHAR2(20),
    CREATED_AT              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT UK_CONVENIENCE_OBJT_ID UNIQUE (OBJT_ID)
);

CREATE SEQUENCE SEQ_CONVENIENCE_STORE START WITH 1 INCREMENT BY 1;
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

# 생활안전지도 API 설정
app:
  safemap:
    api-key: 발급받은_인증키
    base-url: https://safemap.go.kr/openApiService/data/getConvenienceStoreData.do
    facility-code: 509010
```

## 4. JPA Entity

```java
package com.wherehouse.safety.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "CONVENIENCE_STORE_DATA")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConvenienceStoreData {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "convenience_seq")
    @SequenceGenerator(name = "convenience_seq", sequenceName = "SEQ_CONVENIENCE_STORE", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "OBJT_ID", nullable = false, unique = true)
    private Long objtId;

    @Column(name = "FCLTY_TY", length = 500)
    private String fcltyTy = "편의점";

    @Column(name = "FCLTY_CD", length = 6)
    private String fcltyCd = "509010";

    @Column(name = "FCLTY_NM", nullable = false, length = 500)
    private String fcltyNm;

    @Column(name = "ADRES", length = 500)
    private String adres;

    @Column(name = "RN_ADRES", length = 500)
    private String rnAdres;

    @Column(name = "TELNO", length = 20)
    private String telno;

    @Column(name = "CTPRVN_CD", length = 2)
    private String ctprvnCd;

    @Column(name = "SGG_CD", length = 5)
    private String sggCd;

    @Column(name = "EMD_CD", length = 8)
    private String emdCd;

    @Column(name = "X_COORDINATE")
    private BigDecimal xCoordinate;

    @Column(name = "Y_COORDINATE")
    private BigDecimal yCoordinate;

    @Column(name = "DATA_YR", length = 20)
    private String dataYr;

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

import com.wherehouse.safety.entity.ConvenienceStoreData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ConvenienceStoreRepository extends JpaRepository<ConvenienceStoreData, Long> {
    
    @Query("SELECT c FROM ConvenienceStoreData c WHERE c.ctprvnCd = '11'")
    List<ConvenienceStoreData> findSeoulStores();
    
    @Query("SELECT c FROM ConvenienceStoreData c WHERE c.sggCd = :sggCd")
    List<ConvenienceStoreData> findBySggCd(@Param("sggCd") String sggCd);
    
    boolean existsByObjtId(Long objtId);
    
    @Query("SELECT COUNT(c) FROM ConvenienceStoreData c WHERE c.ctprvnCd = '11'")
    Long countSeoulStores();
}
```

## 6. API 호출 및 데이터 로딩 Component

```java
package com.wherehouse.safety.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.wherehouse.safety.entity.ConvenienceStoreData;
import com.wherehouse.safety.repository.ConvenienceStoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConvenienceStoreDataLoader implements CommandLineRunner {

    private final ConvenienceStoreRepository repository;
    private final WebClient.Builder webClientBuilder;
    
    @Value("${apps.trade-api.api-key}")
    private String apiKey;
    
    @Value("${apps.trade-api.base-url}")
    private String baseUrl;
    
    @Value("${apps.trade-api.facility-code}")
    private String facilityCode;

    @Override
    public void run(String... args) {
        log.info("편의점 데이터 로딩 시작");
        loadConvenienceStoreData();
        log.info("편의점 데이터 로딩 완료");
    }

    @Transactional
    public void loadConvenienceStoreData() {
        if (repository.count() > 0) {
            log.info("편의점 데이터 이미 존재. 로딩 스킵");
            return;
        }

        try {
            int pageNo = 1;
            int numOfRows = 1000;
            int totalSaved = 0;
            
            log.info("=== API 호출 시작 ===");
            log.info("베이스 URL: {}", baseUrl);
            log.info("API 키: {}", apiKey != null ? apiKey.substring(0, Math.min(apiKey.length(), 10)) + "..." : "null");
            log.info("시설 코드: {}", facilityCode);
            
            while (true) {
                log.info("페이지 {} 호출 시작 (페이지당 {} 건)", pageNo, numOfRows);
                
                String xmlResponse = callSafemapApi(pageNo, numOfRows);
                log.info("API 응답 길이: {} 문자", xmlResponse != null ? xmlResponse.length() : 0);
                log.info("API 응답 첫 500자: {}", xmlResponse != null && xmlResponse.length() > 500 ? 
                    xmlResponse.substring(0, 500) + "..." : xmlResponse);
                
                List<ConvenienceStoreData> stores = parseXmlResponse(xmlResponse);
                log.info("파싱된 편의점 수: {}", stores.size());
                
                if (stores.isEmpty()) {
                    log.info("더 이상 데이터가 없어 종료");
                    break;
                }
                
                int pageSaved = 0;
                for (ConvenienceStoreData store : stores) {
                    if (!repository.existsByObjtId(store.getObjtId())) {
                        repository.save(store);
                        totalSaved++;
                        pageSaved++;
                        log.debug("저장 완료: {} (ID: {})", store.getFcltyNm(), store.getObjtId());
                    } else {
                        log.debug("중복 데이터 스킵: {} (ID: {})", store.getFcltyNm(), store.getObjtId());
                    }
                }
                
                log.info("페이지 {} 처리 완료 - 파싱: {} 개, 저장: {} 개", pageNo, stores.size(), pageSaved);
                pageNo++;
                
                // 페이지 수 제한 (무한루프 방지)
                if (pageNo > 100) {
                    log.warn("페이지 수 제한 도달 (100페이지). 종료");
                    break;
                }
            }
            
            log.info("=== API 호출 완료 ===");
            log.info("전체 {} 개 편의점 데이터 저장 완료", totalSaved);
            
        } catch (Exception e) {
            log.error("편의점 API 호출 실패: {}", e.getMessage(), e);
            throw new RuntimeException("편의점 데이터 로딩 실패", e);
        }
    }

    private String callSafemapApi(int pageNo, int numOfRows) {
        String fullUrl = baseUrl + "?serviceKey=" + apiKey + "&numOfRows=" + numOfRows + 
                        "&pageNo=" + pageNo + "&dataType=XML&Fclty_Cd=" + facilityCode;
        
        log.info("API 호출 URL: {}", fullUrl);
        
        try {
            String response = webClientBuilder
                    .baseUrl(baseUrl)
                    .build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("serviceKey", apiKey)
                            .queryParam("numOfRows", numOfRows)
                            .queryParam("pageNo", pageNo)
                            .queryParam("dataType", "XML")
                            .queryParam("Fclty_Cd", facilityCode)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            log.info("API 호출 성공 - 응답 수신");
            return response;
            
        } catch (Exception e) {
            log.error("API 호출 중 예외 발생: {}", e.getMessage());
            throw e;
        }
    }

    private List<ConvenienceStoreData> parseXmlResponse(String xmlResponse) {
        List<ConvenienceStoreData> stores = new ArrayList<>();
        
        try {
            log.info("XML 파싱 시작");
            
            if (xmlResponse == null || xmlResponse.trim().isEmpty()) {
                log.warn("빈 XML 응답");
                return stores;
            }
            
            XmlMapper xmlMapper = new XmlMapper();
            Map<String, Object> responseMap = xmlMapper.readValue(xmlResponse, Map.class);
            
            log.info("XML 파싱 완료. 최상위 키: {}", responseMap.keySet());
            
            // header 정보 확인
            Map<String, Object> header = (Map<String, Object>) responseMap.get("header");
            if (header != null) {
                log.info("API 응답 헤더: resultCode={}, resultMsg={}", 
                    header.get("resultCode"), header.get("resultMsg"));
            }
            
            Map<String, Object> body = (Map<String, Object>) responseMap.get("body");
            if (body == null) {
                log.warn("body가 null입니다");
                return stores;
            }
            
            log.info("body 키: {}", body.keySet());
            
            Object itemsObj = body.get("items");
            if (itemsObj == null) {
                log.warn("items가 null입니다");
                return stores;
            }
            
            log.info("items 타입: {}", itemsObj.getClass().getSimpleName());
            
            Map<String, Object> items = (Map<String, Object>) itemsObj;
            Object itemObj = items.get("item");
            
            if (itemObj == null) {
                log.warn("item이 null입니다");
                return stores;
            }
            
            log.info("item 타입: {}", itemObj.getClass().getSimpleName());
            
            if (itemObj instanceof List) {
                List<Map<String, Object>> itemList = (List<Map<String, Object>>) itemObj;
                log.info("리스트 형태의 아이템 {} 개 발견", itemList.size());
                for (int i = 0; i < itemList.size(); i++) {
                    Map<String, Object> item = itemList.get(i);
                    log.debug("아이템 {}: {}", i+1, item.keySet());
                    stores.add(mapToConvenienceStore(item));
                }
            } else if (itemObj instanceof Map) {
                log.info("단일 아이템 발견");
                Map<String, Object> item = (Map<String, Object>) itemObj;
                log.debug("아이템 키: {}", item.keySet());
                stores.add(mapToConvenienceStore(item));
            }
            
            log.info("총 {} 개 편의점 파싱 완료", stores.size());
            
        } catch (Exception e) {
            log.error("XML 파싱 실패: {}", e.getMessage(), e);
        }
        
        return stores;
    }

    private ConvenienceStoreData mapToConvenienceStore(Map<String, Object> item) {
        return ConvenienceStoreData.builder()
                .objtId(parseLong(item.get("OBJT_ID")))
                .fcltyTy(parseString(item.get("FCLTY_TY")))
                .fcltyCd(parseString(item.get("FCLTY_CD")))
                .fcltyNm(parseString(item.get("FCLTY_NM")))
                .adres(parseString(item.get("ADRES")))
                .rnAdres(parseString(item.get("RN_ADRES")))
                .telno(parseString(item.get("TELNO")))
                .ctprvnCd(parseString(item.get("CTPRVN_CD")))
                .sggCd(parseString(item.get("SGG_CD")))
                .emdCd(parseString(item.get("EMD_CD")))
                .xCoordinate(parseBigDecimal(item.get("X")))
                .yCoordinate(parseBigDecimal(item.get("Y")))
                .dataYr(parseString(item.get("DATA_YR")))
                .build();
    }

    private Long parseLong(Object value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String parseString(Object value) {
        return value != null ? value.toString().trim() : null;
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}