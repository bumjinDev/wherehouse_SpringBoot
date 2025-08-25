# 건강보험심사평가원 병원정보서비스 API 구현

## 1. Oracle 테이블 DDL

```sql
CREATE TABLE HOSPITAL_INFO (
    ID                      NUMBER(19) PRIMARY KEY,
    YADM_NM                 VARCHAR2(200) NOT NULL,
    CL_CD                   VARCHAR2(10),
    CL_CD_NM                VARCHAR2(50),
    SIDO_CD                 VARCHAR2(10),
    SIDO_CD_NM              VARCHAR2(50),
    SGGU_CD                 VARCHAR2(10),
    SGGU_CD_NM              VARCHAR2(50),
    EMDONG_NM               VARCHAR2(100),
    POST_NO                 VARCHAR2(10),
    ADDR                    VARCHAR2(500),
    TELNO                   VARCHAR2(20),
    HOSP_URL                VARCHAR2(500),
    ESTB_DD                 VARCHAR2(8),
    DR_TOT_CNT             NUMBER(6) DEFAULT 0,
    PNURS_CNT              NUMBER(6) DEFAULT 0,
    TRMT_SUBJ_CNT          NUMBER(3) DEFAULT 0,
    X_POS                   NUMBER(15,10),
    Y_POS                   NUMBER(15,10),
    CREATED_AT              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT UK_HOSPITAL_ADDR UNIQUE (YADM_NM, ADDR)
);

CREATE SEQUENCE SEQ_HOSPITAL_INFO START WITH 1 INCREMENT BY 1;
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
import java.time.LocalDateTime;

@Entity
@Table(name = "HOSPITAL_INFO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HospitalInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hospital_seq")
    @SequenceGenerator(name = "hospital_seq", sequenceName = "SEQ_HOSPITAL_INFO", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "YADM_NM", nullable = false, length = 200)
    private String yadmNm;

    @Column(name = "CL_CD", length = 10)
    private String clCd;

    @Column(name = "CL_CD_NM", length = 50)
    private String clCdNm;

    @Column(name = "SIDO_CD", length = 10)
    private String sidoCd;

    @Column(name = "SIDO_CD_NM", length = 50)
    private String sidoCdNm;

    @Column(name = "SGGU_CD", length = 10)
    private String sgguCd;

    @Column(name = "SGGU_CD_NM", length = 50)
    private String sgguCdNm;

    @Column(name = "EMDONG_NM", length = 100)
    private String emdongNm;

    @Column(name = "POST_NO", length = 10)
    private String postNo;

    @Column(name = "ADDR", length = 500)
    private String addr;

    @Column(name = "TELNO", length = 20)
    private String telno;

    @Column(name = "HOSP_URL", length = 500)
    private String hospUrl;

    @Column(name = "ESTB_DD", length = 8)
    private String estbDd;

    @Column(name = "DR_TOT_CNT")
    private Integer drTotCnt = 0;

    @Column(name = "PNURS_CNT")
    private Integer pnursCnt = 0;

    @Column(name = "TRMT_SUBJ_CNT")
    private Integer trmtSubjCnt = 0;

    @Column(name = "X_POS", precision = 15, scale = 10)
    private BigDecimal xPos;

    @Column(name = "Y_POS", precision = 15, scale = 10)
    private BigDecimal yPos;

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

import com.wherehouse.safety.entity.HospitalInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface HospitalInfoRepository extends JpaRepository<HospitalInfo, Long> {
    boolean existsByYadmNmAndAddr(String yadmNm, String addr);
}
```

## 4. API Response DTO

```java
package com.wherehouse.safety.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HospitalApiResponse {

    private Response response;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {
        private Header header;
        private Body body;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Header {
        private String resultCode;
        private String resultMsg;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        private Items items;
        private Integer numOfRows;
        private Integer pageNo;
        private Integer totalCount;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Items {
        private List<Item> item;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        @JsonProperty("yadmNm")
        private String yadmNm;
        
        @JsonProperty("clCd")
        private String clCd;
        
        @JsonProperty("clCdNm")
        private String clCdNm;
        
        @JsonProperty("sidoCd")
        private String sidoCd;
        
        @JsonProperty("sidoCdNm")
        private String sidoCdNm;
        
        @JsonProperty("sgguCd")
        private String sgguCd;
        
        @JsonProperty("sgguCdNm")
        private String sgguCdNm;
        
        @JsonProperty("emdongNm")
        private String emdongNm;
        
        @JsonProperty("postNo")
        private String postNo;
        
        @JsonProperty("addr")
        private String addr;
        
        @JsonProperty("telno")
        private String telno;
        
        @JsonProperty("hospUrl")
        private String hospUrl;
        
        @JsonProperty("estbDd")
        private String estbDd;
        
        @JsonProperty("drTotCnt")
        private String drTotCnt;
        
        @JsonProperty("pnursCnt")
        private String pnursCnt;
        
        @JsonProperty("trmtSubjCnt")
        private String trmtSubjCnt;
        
        @JsonProperty("XPos")
        private String xPos;
        
        @JsonProperty("YPos")
        private String yPos;
    }
}
```

## 5. API 클라이언트 및 데이터 로더

```java
package com.wherehouse.safety.component;

import com.wherehouse.safety.dto.HospitalApiResponse;
import com.wherehouse.safety.entity.HospitalInfo;
import com.wherehouse.safety.repository.HospitalInfoRepository;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class HospitalDataLoader implements CommandLineRunner {

    private final HospitalInfoRepository hospitalRepository;
    private final WebClient webClient;

    @Value("${app.api.hospital.service-key}")
    private String serviceKey;

    @Override
    @Transactional
    public void run(String... args) {
        if (hospitalRepository.count() > 0) {
            log.info("병원 데이터 이미 존재. 로딩 스킵");
            return;
        }
        
        try {
            List<HospitalInfo> hospitals = getSeoulHospitals();
            
            int savedCount = 0;
            for (HospitalInfo hospital : hospitals) {
                if (!hospitalRepository.existsByYadmNmAndAddr(hospital.getYadmNm(), hospital.getAddr())) {
                    hospitalRepository.save(hospital);
                    savedCount++;
                }
            }
            log.info("{} 개 병원 데이터 저장 완료", savedCount);
            
        } catch (Exception e) {
            log.error("병원 데이터 로딩 실패: {}", e.getMessage());
        }
    }

    private List<HospitalInfo> getSeoulHospitals() {
        List<HospitalInfo> allHospitals = new ArrayList<>();
        int pageNo = 1;
        int totalPages = 1;

        try {
            do {
                HospitalApiResponse response = callApi(pageNo, 100, "110000");
                
                if (response != null && response.getResponse() != null && 
                    "00".equals(response.getResponse().getHeader().getResultCode())) {
                    
                    List<HospitalInfo> hospitals = convertToEntities(response);
                    allHospitals.addAll(hospitals);
                    
                    int totalCount = response.getResponse().getBody().getTotalCount();
                    totalPages = (totalCount + 99) / 100;
                    
                    Thread.sleep(100);
                }
                
                pageNo++;
            } while (pageNo <= totalPages);
            
        } catch (Exception e) {
            log.error("병원 정보 API 호출 실패: {}", e.getMessage());
        }

        return allHospitals;
    }

    private HospitalApiResponse callApi(int pageNo, int numOfRows, String sidoCd) {
        try {
            return webClient.get()
                    .uri("http://apis.data.go.kr/B551182/hospInfoServicev2/getHospBasisList")
                    .queryParam("serviceKey", serviceKey)
                    .queryParam("pageNo", pageNo)
                    .queryParam("numOfRows", numOfRows)
                    .queryParam("sidoCd", sidoCd)
                    .retrieve()
                    .bodyToMono(HospitalApiResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("API 호출 실패: {}", e.getMessage());
            return null;
        }
    }

    private List<HospitalInfo> convertToEntities(HospitalApiResponse response) {
        List<HospitalInfo> hospitals = new ArrayList<>();
        
        if (response.getResponse().getBody().getItems() != null && 
            response.getResponse().getBody().getItems().getItem() != null) {
            
            for (HospitalApiResponse.Item item : response.getResponse().getBody().getItems().getItem()) {
                HospitalInfo hospital = HospitalInfo.builder()
                        .yadmNm(item.getYadmNm())
                        .clCd(item.getClCd())
                        .clCdNm(item.getClCdNm())
                        .sidoCd(item.getSidoCd())
                        .sidoCdNm(item.getSidoCdNm())
                        .sgguCd(item.getSgguCd())
                        .sgguCdNm(item.getSgguCdNm())
                        .emdongNm(item.getEmdongNm())
                        .postNo(item.getPostNo())
                        .addr(item.getAddr())
                        .telno(item.getTelno())
                        .hospUrl(item.getHospUrl())
                        .estbDd(item.getEstbDd())
                        .drTotCnt(parseInteger(item.getDrTotCnt()))
                        .pnursCnt(parseInteger(item.getPnursCnt()))
                        .trmtSubjCnt(parseInteger(item.getTrmtSubjCnt()))
                        .xPos(parseBigDecimal(item.getXPos()))
                        .yPos(parseBigDecimal(item.getYPos()))
                        .build();
                
                hospitals.add(hospital);
            }
        }
        
        return hospitals;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```

## 6. application.yml 추가 설정

```yaml
app:
  api:
    hospital:
      service-key: ${HOSPITAL_API_SERVICE_KEY:YOUR_SERVICE_KEY_HERE}
```