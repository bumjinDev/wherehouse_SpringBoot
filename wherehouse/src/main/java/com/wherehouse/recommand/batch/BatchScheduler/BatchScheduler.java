package com.wherehouse.recommand.batch.BatchScheduler;

import com.wherehouse.recommand.batch.dto.Property;
import com.wherehouse.redis.handler.RedisHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 부동산 매물 데이터 배치 처리 스케줄러
 * 매일 새벽 4시에 국토교통부 API에서 최신 매물 데이터를 수집하여 Redis에 저장
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchScheduler {

    private final RedisHandler redisHandler;

    private final String serviceKey = System.getenv("MOLIT_RENT_API_SERVICE_KEY");

    @Value("${molit.rent-api.base-url}")
    private String baseUrl;

    private static final String API_ENDPOINT = "/getRTMSDataSvcAptRent";
    private static final String NUM_OF_ROWS = "1000"; // 페이지당 조회 건수

    // 서울시 25개 자치구 코드 매핑
    private static final Map<String, String> SEOUL_DISTRICT_CODES;

    static {
        Map<String, String> codes = new HashMap<>();
        codes.put("11110", "종로구");
        codes.put("11140", "중구");
        codes.put("11170", "용산구");
        codes.put("11200", "성동구");
        codes.put("11215", "광진구");
        codes.put("11230", "동대문구");
        codes.put("11260", "중랑구");
        codes.put("11290", "성북구");
        codes.put("11305", "강북구");
        codes.put("11320", "도봉구");
        codes.put("11350", "노원구");
        codes.put("11380", "은평구");
        codes.put("11410", "서대문구");
        codes.put("11440", "마포구");
        codes.put("11470", "양천구");
        codes.put("11500", "강서구");
        codes.put("11530", "구로구");
        codes.put("11545", "금천구");
        codes.put("11560", "영등포구");
        codes.put("11590", "동작구");
        codes.put("11620", "관악구");
        codes.put("11650", "서초구");
        codes.put("11680", "강남구");
        codes.put("11710", "송파구");
        codes.put("11740", "강동구");
        SEOUL_DISTRICT_CODES = Collections.unmodifiableMap(codes);
    }

    // 테스트용: 한번만 실행 (fixedDelay 사용)
//    @Scheduled(fixedDelay = Long.MAX_VALUE, initialDelay = 5000)
    public void executeBatchProcess() {
        log.info("=== 부동산 매물 데이터 배치 처리 시작 ===");

        try {
            // B-01: 전 지역구 매물 데이터 수집
            List<Property> allProperties = collectAllDistrictData();

            log.info("총 {}건의 매물 데이터를 수집했습니다.", allProperties.size());

            // B-03: Redis 데이터 적재
            storeDataToRedis(allProperties);

            log.info("=== 부동산 매물 데이터 배치 처리 완료 ===");

        } catch (Exception e) {
            log.error("배치 처리 중 오류 발생", e);
        }
    }

    /**
     * B-01: 전 지역구 매물 데이터 수집
     * 서울시 25개 자치구를 순회하며 모든 매물 데이터를 수집
     */
    private List<Property> collectAllDistrictData() {
        log.info("서울시 25개 자치구 매물 데이터 수집 시작");

        List<Property> allProperties = new ArrayList<>();
        String dealYmd = LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyyMM")); // 전월 데이터

        log.info("조회 기준 년월: {}", dealYmd);

        for (Map.Entry<String, String> district : SEOUL_DISTRICT_CODES.entrySet()) {
            String districtCode = district.getKey();
            String districtName = district.getValue();

            log.info(">>> {} ({}) 매물 데이터 수집 시작", districtName, districtCode);

            try {
                List<Property> districtProperties = collectDistrictDataWithPaging(
                        districtCode, districtName, dealYmd);

                allProperties.addAll(districtProperties);

                log.info(">>> {} 매물 데이터 수집 완료: {}건", districtName, districtProperties.size());

                // API 호출 간격 조절 (Rate Limit 방지)
                Thread.sleep(200);

            } catch (Exception e) {
                log.error(">>> {} 매물 데이터 수집 실패", districtName, e);
            }
        }

        log.info("전 지역구 매물 데이터 수집 완료: 총 {}건", allProperties.size());
        return allProperties;
    }

    /**
     * 특정 지역구의 모든 페이지 데이터를 수집 (페이징 처리)
     */
    private List<Property> collectDistrictDataWithPaging(String lawdCd, String districtName, String dealYmd) {
        List<Property> districtProperties = new ArrayList<>();
        int pageNo = 1;
        int totalCount = 0;

        do {
            try {
                log.debug("{}페이지 데이터 수집 중...", pageNo);

                String xmlResponse = callRentAPI(lawdCd, dealYmd, String.valueOf(pageNo));
                if (xmlResponse == null) {
                    log.warn("API 응답이 null입니다. 다음 페이지로 이동합니다.");
                    break;
                }

                // XML 파싱 및 매물 데이터 추출
                List<Property> pageProperties = parseXmlAndExtractProperties(xmlResponse, districtName);

                if (pageProperties.isEmpty()) {
                    log.debug("{}페이지에서 추가 데이터가 없습니다. 수집을 종료합니다.", pageNo);
                    break;
                }

                districtProperties.addAll(pageProperties);

                // totalCount는 첫 번째 페이지에서만 파싱
                if (pageNo == 1) {
                    totalCount = extractTotalCount(xmlResponse);
                    log.info(">>> {} 전체 데이터 수: {}건", districtName, totalCount);
                }

                pageNo++;

                // 무한루프 방지
                if (pageNo > 500) {
                    log.warn("페이지 수가 500을 초과했습니다. 수집을 중단합니다.");
                    break;
                }

            } catch (Exception e) {
                log.error("{}페이지 수집 중 오류 발생", pageNo, e);
                break;
            }
        } while (true);

        return districtProperties;
    }

    /**
     * 국토교통부 전월세 실거래가 API 호출
     */
    private String callRentAPI(String lawdCd, String dealYmd, String pageNo) {
        HttpURLConnection conn = null;
        BufferedReader reader = null;

        try {
            StringBuilder urlBuilder = new StringBuilder(baseUrl + API_ENDPOINT);
            urlBuilder.append("?").append(URLEncoder.encode("serviceKey", "UTF-8"))
                    .append("=").append(serviceKey);
            urlBuilder.append("&").append(URLEncoder.encode("LAWD_CD", "UTF-8"))
                    .append("=").append(URLEncoder.encode(lawdCd, "UTF-8"));
            urlBuilder.append("&").append(URLEncoder.encode("DEAL_YMD", "UTF-8"))
                    .append("=").append(URLEncoder.encode(dealYmd, "UTF-8"));
            urlBuilder.append("&").append(URLEncoder.encode("numOfRows", "UTF-8"))
                    .append("=").append(URLEncoder.encode(NUM_OF_ROWS, "UTF-8"));
            urlBuilder.append("&").append(URLEncoder.encode("pageNo", "UTF-8"))
                    .append("=").append(URLEncoder.encode(pageNo, "UTF-8"));

            URL url = new URL(urlBuilder.toString());
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-type", "application/xml");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }

                return response.toString();
            } else {
                log.error("API 호출 실패 - HTTP 코드: {}", responseCode);
                return null;
            }

        } catch (Exception e) {
            log.error("API 호출 중 예외 발생", e);
            return null;
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * XML 응답에서 전체 데이터 수 추출
     */
    private int extractTotalCount(String xmlResponse) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xmlResponse)));

            NodeList bodyNodes = document.getElementsByTagName("body");
            if (bodyNodes.getLength() > 0) {
                Element body = (Element) bodyNodes.item(0);
                String totalCountStr = getElementValue(body, "totalCount");
                return totalCountStr != null ? Integer.parseInt(totalCountStr) : 0;
            }
        } catch (Exception e) {
            log.debug("totalCount 파싱 실패", e);
        }
        return 0;
    }

    /**
     * XML 응답을 파싱하여 Property 객체 리스트로 변환
     */
    private List<Property> parseXmlAndExtractProperties(String xmlResponse, String districtName) {
        List<Property> properties = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xmlResponse)));

            NodeList itemNodes = document.getElementsByTagName("item");

            for (int i = 0; i < itemNodes.getLength(); i++) {
                Element item = (Element) itemNodes.item(i);
                Property property = parseIndividualProperty(item, districtName);
                if (property != null) {
                    properties.add(property);
                }
            }

        } catch (Exception e) {
            log.error("XML 파싱 중 오류 발생", e);
        }

        return properties;
    }

    /**
     * 개별 매물 정보를 Property 객체로 변환 (B-02: 데이터 정제 및 객체 변환)
     */
    private Property parseIndividualProperty(Element item, String districtName) {
        try {
            Property property = Property.builder()
                    .propertyId(UUID.randomUUID().toString()) // 고유 ID 자동 생성
                    .districtName(districtName)
                    .aptNm(getElementValue(item, "aptNm"))
                    .excluUseAr(parseDoubleValue(getElementValue(item, "excluUseAr")))
                    .floor(parseIntegerValue(getElementValue(item, "floor")))
                    .buildYear(parseIntegerValue(getElementValue(item, "buildYear")))
                    .deposit(parseIntegerValue(getElementValue(item, "deposit")))
                    .monthlyRent(parseIntegerValue(getElementValue(item, "monthlyRent")))
                    .umdNm(getElementValue(item, "umdNm"))
                    .jibun(getElementValue(item, "jibun"))
                    .sggCd(getElementValue(item, "sggCd"))
                    .rgstDate(getElementValue(item, "rgstDate"))
                    .build();

            // 계약일자 조합
            String dealYear = getElementValue(item, "dealYear");
            String dealMonth = getElementValue(item, "dealMonth");
            String dealDay = getElementValue(item, "dealDay");
            if (dealYear != null && dealMonth != null && dealDay != null) {
                property.setDealDate(String.format("%s-%02d-%02d",
                        dealYear, Integer.parseInt(dealMonth), Integer.parseInt(dealDay)));
            }

            // 계산된 값들 설정
            property.calculateAreaInPyeong();
            property.determineLeaseType();
            property.generateAddress();

            // 안전성 점수는 API에서 제공되지 않으므로 설정하지 않음

            return property;

        } catch (Exception e) {
            log.debug("개별 매물 파싱 실패", e);
            return null;
        }
    }

    /**
     * XML Element에서 텍스트 값 추출
     */
    private String getElementValue(Element parent, String tagName) {
        try {
            NodeList nodeList = parent.getElementsByTagName(tagName);
            if (nodeList.getLength() > 0) {
                Node node = nodeList.item(0);
                if (node != null && node.getFirstChild() != null) {
                    return node.getFirstChild().getNodeValue().trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 문자열을 Double로 안전하게 변환
     */
    private Double parseDoubleValue(String value) {
        try {
            return value != null ? Double.parseDouble(value.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 문자열을 Integer로 안전하게 변환
     */
    private Integer parseIntegerValue(String value) {
        try {
            return value != null ? Integer.parseInt(value.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * B-03: Redis 데이터 적재
     * 명세서에 따른 3개 구조로 저장: Hash + 2개 Sorted Set 인덱스
     */
    private void storeDataToRedis(List<Property> properties) {
        log.info("Redis 데이터 적재 시작 - 총 {}건", properties.size());

        // 기존 데이터 초기화
        redisHandler.clearCurrentRedisDB();

        int successCount = 0;
        Map<String, Integer> districtStats = new HashMap<>();

        for (Property property : properties) {
            try {
                // 1. 매물 원본 데이터 저장 (Hash) - 키 패턴: property:{id}
                String propertyKey = "property:" + property.getPropertyId();

                // Hash 구조로 각 필드별로 저장 (명세서 2.1에 맞춘 필드만)
                Map<String, Object> propertyHash = new HashMap<>();
                propertyHash.put("propertyId", property.getPropertyId() != null ? property.getPropertyId() : "");
                propertyHash.put("aptNm", property.getAptNm() != null ? property.getAptNm() : "");
                propertyHash.put("excluUseAr", property.getExcluUseAr() != null ? property.getExcluUseAr().toString() : "0.0");
                propertyHash.put("floor", property.getFloor() != null ? property.getFloor().toString() : "0");
                propertyHash.put("buildYear", property.getBuildYear() != null ? property.getBuildYear().toString() : "0");
                propertyHash.put("dealDate", property.getDealDate() != null ? property.getDealDate() : "");
                propertyHash.put("deposit", property.getDeposit() != null ? property.getDeposit().toString() : "0");
                propertyHash.put("monthlyRent", property.getMonthlyRent() != null ? property.getMonthlyRent().toString() : "0");
                propertyHash.put("leaseType", property.getLeaseType() != null ? property.getLeaseType() : "");
                propertyHash.put("umdNm", property.getUmdNm() != null ? property.getUmdNm() : "");
                propertyHash.put("jibun", property.getJibun() != null ? property.getJibun() : "");
                propertyHash.put("sggCd", property.getSggCd() != null ? property.getSggCd() : "");
                propertyHash.put("address", property.getAddress() != null ? property.getAddress() : "");
                propertyHash.put("areaInPyeong", property.getAreaInPyeong() != null ? property.getAreaInPyeong().toString() : "0.0");
                propertyHash.put("rgstDate", property.getRgstDate() != null ? property.getRgstDate() : "");
                propertyHash.put("districtName", property.getDistrictName() != null ? property.getDistrictName() : "");

//                System.out.println("propertyId: " + propertyHash.get("propertyId") + ", aptNm: " + propertyHash.get("aptNm") + ", excluUseAr: " + propertyHash.get("excluUseAr") + ", floor: " + propertyHash.get("floor") + ", buildYear: " + propertyHash.get("buildYear") + ", dealDate: " + propertyHash.get("dealDate") + ", deposit: " + propertyHash.get("deposit") + ", monthlyRent: " + propertyHash.get("monthlyRent") + ", leaseType: " + propertyHash.get("leaseType") + ", umdNm: " + propertyHash.get("umdNm") + ", jibun: " + propertyHash.get("jibun") + ", sggCd: " + propertyHash.get("sggCd") + ", address: " + propertyHash.get("address") + ", areaInPyeong: " + propertyHash.get("areaInPyeong") + ", rgstDate: " + propertyHash.get("rgstDate") + ", districtName: " + propertyHash.get("districtName"));

                // Redis Hash에 저장
                redisHandler.redisTemplate.opsForHash().putAll(propertyKey, propertyHash);
                successCount++;

                // 2. 가격 인덱스 저장 (Sorted Set) - 키 패턴: idx:price:{지역구명}:{임대유형}
                String priceIndexKey = "idx:price:" + property.getDistrictName() + ":" + property.getLeaseType();
                Double priceScore = property.getDeposit() != null ? property.getDeposit().doubleValue() : 0.0;
                redisHandler.redisTemplate.opsForZSet().add(priceIndexKey, property.getPropertyId(), priceScore);

                // 3. 평수 인덱스 저장 (Sorted Set) - 키 패턴: idx:area:{지역구명}:{임대유형}
                String areaIndexKey = "idx:area:" + property.getDistrictName() + ":" + property.getLeaseType();
                Double areaScore = property.getAreaInPyeong() != null ? property.getAreaInPyeong() : 0.0;
                redisHandler.redisTemplate.opsForZSet().add(areaIndexKey, property.getPropertyId(), areaScore);

                // 지역구별 통계 업데이트
                districtStats.merge(property.getDistrictName(), 1, Integer::sum);

            } catch (Exception e) {
                log.debug("매물 Redis 저장 실패: {}", property.getPropertyId(), e);
            }
        }

        // 통계 로그 출력
        log.info("Redis 데이터 적재 완료 - 성공: {}건 / 전체: {}건", successCount, properties.size());
        log.info("지역구별 매물 수: {}", districtStats);

        // 생성된 인덱스 확인 로그
        log.info("생성된 Redis 구조:");
        log.info("- 매물 원본 데이터: property:{{id}} Hash 구조");
        log.info("- 가격 인덱스: idx:price:{{지역구}}:{{임대유형}} Sorted Set");
        log.info("- 평수 인덱스: idx:area:{{지역구}}:{{임대유형}} Sorted Set");
    }
}