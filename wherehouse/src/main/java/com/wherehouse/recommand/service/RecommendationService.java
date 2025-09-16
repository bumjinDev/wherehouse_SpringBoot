
package com.wherehouse.recommand.service;

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

            // B-04: 지역구별 정규화 범위 계산 및 저장
            calculateAndStoreNormalizationBounds(allProperties);

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

    /**
     * B-04: 지역구별 정규화 범위 계산 및 저장
     * 실시간 추천 점수 계산 성능 최적화를 위해 지역구별·임대유형별 가격 및 평수의 정규화 범위를 사전 계산하여 Redis에 저장
     */
    private void calculateAndStoreNormalizationBounds(List<Property> properties) {
        log.info("=== B-04: 지역구별 정규화 범위 계산 및 저장 시작 ===");

        // 1. 데이터 그룹핑: 지역구명 + 임대유형
        Map<String, List<Property>> groupedProperties = groupPropertiesByDistrictAndLeaseType(properties);

        int processedGroups = 0;
        int validGroups = 0;
        String currentTime = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        for (Map.Entry<String, List<Property>> entry : groupedProperties.entrySet()) {
            String groupKey = entry.getKey();
            List<Property> groupProperties = entry.getValue();

            try {
                log.debug("그룹 [{}] 처리 시작 - 매물 수: {}건", groupKey, groupProperties.size());

                // 품질 보장: 최소 데이터 요구사항 검증 (그룹당 2개 이상 매물)
                if (groupProperties.size() < 2) {
                    log.debug("그룹 [{}] 스킵 - 매물 수 부족 ({}건 < 2건)", groupKey, groupProperties.size());
                    continue;
                }

                // 2. 총 가격 산정 및 3. 정규화 범위 계산
                NormalizationBounds bounds = calculateBoundsForGroup(groupProperties, groupKey);

                if (bounds == null) {
                    log.debug("그룹 [{}] 스킵 - 유효한 데이터 부족", groupKey);
                    continue;
                }

                // 4. Redis 저장
                storeBoundsToRedis(groupKey, bounds, groupProperties.size(), currentTime);

                validGroups++;
                log.debug("그룹 [{}] 처리 완료 - 가격범위: [{} ~ {}], 평수범위: [{} ~ {}]",
                        groupKey, bounds.minPrice, bounds.maxPrice, bounds.minArea, bounds.maxArea);

            } catch (Exception e) {
                log.error("그룹 [{}] 처리 중 오류 발생", groupKey, e);
            }

            processedGroups++;
        }

        log.info("=== B-04: 지역구별 정규화 범위 계산 완료 - 처리된 그룹: {}/{}, 유효한 그룹: {} ===",
                processedGroups, groupedProperties.size(), validGroups);
    }

    /**
     * 1. 데이터 그룹핑: 지역구명 + 임대유형으로 매물 그룹핑
     */
    private Map<String, List<Property>> groupPropertiesByDistrictAndLeaseType(List<Property> properties) {
        Map<String, List<Property>> groupedProperties = new HashMap<>();

        for (Property property : properties) {
            // 유효성 검증: 필수 필드(가격, 평수) 누락 매물 제외
            if (!isValidPropertyForNormalization(property)) {
                continue;
            }

            // 그룹 키 형태: {지역구명}:{임대유형}
            String groupKey = property.getDistrictName() + ":" + property.getLeaseType();

            groupedProperties.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(property);
        }

        log.info("매물 그룹핑 완료 - 총 {}개 그룹 생성", groupedProperties.size());
        return groupedProperties;
    }

    /**
     * 정규화 계산을 위한 매물 유효성 검증
     */
    private boolean isValidPropertyForNormalization(Property property) {
        return property.getDistrictName() != null
                && property.getLeaseType() != null
                && property.getDeposit() != null
                && property.getMonthlyRent() != null
                && property.getAreaInPyeong() != null
                && property.getAreaInPyeong() > 0;
    }

    /**
     * 2. 총 가격 산정 및 3. 정규화 범위 계산
     */
    private NormalizationBounds calculateBoundsForGroup(List<Property> groupProperties, String groupKey) {
        List<Double> totalPrices = new ArrayList<>();
        List<Double> areas = new ArrayList<>();

        for (Property property : groupProperties) {
            // 2. 총 가격 산정
            double totalPrice = calculateTotalPrice(property);
            if (totalPrice > 0) {
                totalPrices.add(totalPrice);
            }

            // 평수 수집
            if (property.getAreaInPyeong() != null && property.getAreaInPyeong() > 0) {
                areas.add(property.getAreaInPyeong());
            }
        }

        if (totalPrices.isEmpty() || areas.isEmpty()) {
            log.debug("그룹 [{}] - 유효한 가격 또는 평수 데이터 없음", groupKey);
            return null;
        }

        // 3. 정규화 범위 계산
        double minPrice = Collections.min(totalPrices);
        double maxPrice = Collections.max(totalPrices);
        double minArea = Collections.min(areas);
        double maxArea = Collections.max(areas);

        // 4. 품질 보장: 제로 분산 방지 (min = max인 경우 max값 조정)
        if (minPrice == maxPrice) {
            maxPrice = minPrice + 1000.0; // 100만원 차이 설정
            log.debug("그룹 [{}] - 가격 제로 분산 방지: {} -> {}", groupKey, minPrice, maxPrice);
        }

        if (minArea == maxArea) {
            maxArea = minArea + 1.0; // 1평 차이 설정
            log.debug("그룹 [{}] - 평수 제로 분산 방지: {} -> {}", groupKey, minArea, maxArea);
        }

        return new NormalizationBounds(minPrice, maxPrice, minArea, maxArea);
    }

    /**
     * 2. 총 가격 산정
     * - 전세: 보증금
     * - 월세: 보증금 + (월세 × 24개월)
     */
    private double calculateTotalPrice(Property property) {
        double deposit = property.getDeposit() != null ? property.getDeposit().doubleValue() : 0.0;
        double monthlyRent = property.getMonthlyRent() != null ? property.getMonthlyRent().doubleValue() : 0.0;

        if ("전세".equals(property.getLeaseType())) {
            return deposit;
        } else if ("월세".equals(property.getLeaseType())) {
            return deposit + (monthlyRent * 24); // 월세 × 24개월
        }

        return deposit; // 기본적으로 보증금 반환
    }

    /**
     * Redis에 정규화 범위 저장
     * 키 패턴: bounds:{지역구명}:{임대유형}
     */
    private void storeBoundsToRedis(String groupKey, NormalizationBounds bounds,
                                    int propertyCount, String currentTime) {
        String redisKey = "bounds:" + groupKey;

        Map<String, Object> boundsHash = new HashMap<>();
        boundsHash.put("minPrice", String.valueOf(bounds.minPrice));
        boundsHash.put("maxPrice", String.valueOf(bounds.maxPrice));
        boundsHash.put("minArea", String.valueOf(bounds.minArea));
        boundsHash.put("maxArea", String.valueOf(bounds.maxArea));
        boundsHash.put("propertyCount", String.valueOf(propertyCount));
        boundsHash.put("lastUpdated", currentTime);

        redisHandler.redisTemplate.opsForHash().putAll(redisKey, boundsHash);

        log.debug("Redis 저장 완료 - Key: {}, Count: {}", redisKey, propertyCount);
    }

    /**
     * 정규화 범위 데이터를 담는 내부 클래스
     */
    private static class NormalizationBounds {
        final double minPrice;
        final double maxPrice;
        final double minArea;
        final double maxArea;

        NormalizationBounds(double minPrice, double maxPrice, double minArea, double maxArea) {
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
            this.minArea = minArea;
            this.maxArea = maxArea;
        }
    }
}