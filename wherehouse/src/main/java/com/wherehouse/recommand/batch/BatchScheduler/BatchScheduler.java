package com.wherehouse.recommand.batch.BatchScheduler;

import com.wherehouse.recommand.batch.dto.Property;
import com.wherehouse.redis.handler.RedisHandler;
import com.wherehouse.recommand.batch.repository.AnalysisEntertainmentRepository;
import com.wherehouse.recommand.batch.repository.AnalysisPopulationDensityRepository;
import com.wherehouse.recommand.batch.repository.AnalysisCrimeRepository;
import com.wherehouse.recommand.batch.dto.DistrictCrimeCountDto;
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
    private final AnalysisEntertainmentRepository entertainmentRepository;
    private final AnalysisPopulationDensityRepository populationRepository;
    private final AnalysisCrimeRepository crimeRepository;

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
    /**
     * 매일 새벽 4시 0분 40초에 배치 프로세스를 실행.
     * cron = "[초] [분] [시] [일] [월] [요일]"
     */
    @Scheduled(cron = "40 0 4 * * *")
    public void executeBatchProcess() {
        log.info("=== 부동산 매물 데이터 배치 처리 시작 ===");

        try {
            // B-01: 전 지역구 매물 데이터 수집
            List<Property> allProperties = collectAllDistrictData();

            log.info("이 {}건의 매물 데이터를 수집했습니다.", allProperties.size());

            // B-03: Redis 데이터 적재
            storeDataToRedis(allProperties);

            // B-04: 지역구별 정규화 범위 계산 및 저장
            calculateAndStoreNormalizationBounds(allProperties);

            // B-05: 지역구별 안전성 점수 계산 및 Redis 저장
            calculateAndStoreSafetyScores();

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
        // 수정: 현재 날짜 기준 전월로 변경
        String dealYmd = LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyyMM"));

        // 디버깅용: 실제 조회하는 년월 로그
        log.info("조회 기준 년월: {} (현재 날짜 기준 전월)", dealYmd);

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

        log.info("전 지역구 매물 데이터 수집 완료: 이 {}건", allProperties.size());
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
                if (pageNo > 50) {
                    log.warn("페이지 수가 2를 초과했습니다. 수집을 중단합니다.");
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

            // 디버깅용: API 호출 URL 로그 (첫 번째 페이지만)
            if ("1".equals(pageNo)) {
                log.debug("API 호출 URL: {}", urlBuilder.toString().replace(serviceKey, "***"));
            }

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

                // 디버깅용: 응답 상태 로그
                String responseStr = response.toString();
                if (responseStr.contains("<resultCode>")) {
                    String resultCode = extractResultCode(responseStr);
                    String resultMsg = extractResultMsg(responseStr);
                    log.debug("API 응답 상태 - Code: {}, Message: {}", resultCode, resultMsg);
                }

                return responseStr;
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
     * API 응답에서 resultCode 추출
     */
    private String extractResultCode(String xmlResponse) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xmlResponse)));

            NodeList resultCodeNodes = document.getElementsByTagName("resultCode");
            if (resultCodeNodes.getLength() > 0) {
                return resultCodeNodes.item(0).getTextContent();
            }
        } catch (Exception e) {
            log.debug("resultCode 파싱 실패", e);
        }
        return "Unknown";
    }

    /**
     * API 응답에서 resultMsg 추출
     */
    private String extractResultMsg(String xmlResponse) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xmlResponse)));

            NodeList resultMsgNodes = document.getElementsByTagName("resultMsg");
            if (resultMsgNodes.getLength() > 0) {
                return resultMsgNodes.item(0).getTextContent();
            }
        } catch (Exception e) {
            log.debug("resultMsg 파싱 실패", e);
        }
        return "Unknown";
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
     * 문자열을 Double로 안전하게 변환 (쉼표 제거 처리 추가)
     */
    private Double parseDoubleValue(String value) {
        try {
            if (value != null) {
                // 쉼표 제거 후 파싱
                String cleanValue = value.trim().replaceAll(",", "");
                return Double.parseDouble(cleanValue);
            }
            return null;
        } catch (NumberFormatException e) {
            log.debug("숫자 파싱 실패: '{}'", value);
            return null;
        }
    }

    /**
     * 문자열을 Integer로 안전하게 변환 (쉼표 제거 처리 추가)
     */
    private Integer parseIntegerValue(String value) {
        try {
            if (value != null) {
                // 쉼표 제거 후 파싱
                String cleanValue = value.trim().replaceAll(",", "");
                return Integer.parseInt(cleanValue);
            }
            return null;
        } catch (NumberFormatException e) {
            log.debug("숫자 파싱 실패: '{}'", value);
            return null;
        }
    }

    /**
     * B-03: Redis 데이터 적재 - 수정된 버전
     * 명세서에 따른 전세/월세 완전 분리 구조로 저장
     * 핵심 수정사항: 월세 매물의 보증금과 월세금을 각각 독립된 인덱스로 분리
     */
    private void storeDataToRedis(List<Property> properties) {
        log.info("Redis 데이터 적재 시작 - 이 {}건", properties.size());

        // 기존 데이터 초기화
        redisHandler.clearCurrentRedisDB();

        int successCount = 0;
        Map<String, Integer> districtStats = new HashMap<>();

        // 통계용 인덱스 카운터 (각 인덱스별 생성된 Key 개수 추적)
        Map<String, Set<String>> indexKeyTracker = new HashMap<>();
        indexKeyTracker.put("charterPrice", new HashSet<>());
        indexKeyTracker.put("deposit", new HashSet<>());
        indexKeyTracker.put("monthlyRent", new HashSet<>());
        indexKeyTracker.put("charterArea", new HashSet<>());
        indexKeyTracker.put("monthlyArea", new HashSet<>());

        for (Property property : properties) {
            try {
                String leaseType = property.getLeaseType();
                String propertyId = property.getPropertyId();
                String districtName = property.getDistrictName();

                // 공통 Hash 데이터 생성 (임대유형별 차별화 적용)
                Map<String, Object> propertyHash = new HashMap<>();
                propertyHash.put("propertyId", propertyId != null ? propertyId : "");
                propertyHash.put("aptNm", property.getAptNm() != null ? property.getAptNm() : "");
                propertyHash.put("excluUseAr", property.getExcluUseAr() != null ? property.getExcluUseAr().toString() : "0.0");
                propertyHash.put("floor", property.getFloor() != null ? property.getFloor().toString() : "0");
                propertyHash.put("buildYear", property.getBuildYear() != null ? property.getBuildYear().toString() : "0");
                propertyHash.put("dealDate", property.getDealDate() != null ? property.getDealDate() : "");
                propertyHash.put("leaseType", property.getLeaseType() != null ? property.getLeaseType() : "");
                propertyHash.put("umdNm", property.getUmdNm() != null ? property.getUmdNm() : "");
                propertyHash.put("jibun", property.getJibun() != null ? property.getJibun() : "");
                propertyHash.put("sggCd", property.getSggCd() != null ? property.getSggCd() : "");
                propertyHash.put("address", property.getAddress() != null ? property.getAddress() : "");
                propertyHash.put("areaInPyeong", property.getAreaInPyeong() != null ? property.getAreaInPyeong().toString() : "0.0");
                propertyHash.put("rgstDate", property.getRgstDate() != null ? property.getRgstDate() : "");
                propertyHash.put("districtName", districtName != null ? districtName : "");

                if ("전세".equals(leaseType)) {
                    // 전세 전용 필드 추가
                    propertyHash.put("deposit", property.getDeposit() != null ? property.getDeposit().toString() : "0");
                    // monthlyRent는 전세에서 불필요하므로 추가하지 않음

                    // === 전세 매물 처리 ===

                    // [저장소 1] 매물 원본 데이터 (Hash)
                    // 키 패턴: property:charter:{propertyId}
                    String charterPropertyKey = "property:charter:" + propertyId;
                    redisHandler.redisTemplate.opsForHash().putAll(charterPropertyKey, propertyHash);

                    // [저장소 2] 전세금 인덱스 (Sorted Set)
                    // 키 패턴: idx:charterPrice:{지역구명}
                    String charterPriceIndexKey = "idx:charterPrice:" + districtName;
                    Double charterPrice = property.getDeposit() != null ? property.getDeposit().doubleValue() : 0.0;
                    redisHandler.redisTemplate.opsForZSet().add(charterPriceIndexKey, propertyId, charterPrice);
                    indexKeyTracker.get("charterPrice").add(charterPriceIndexKey);

                    // [저장소 3] 평수 인덱스 (Sorted Set)
                    // 키 패턴: idx:area:{지역구명}:전세
                    String charterAreaIndexKey = "idx:area:" + districtName + ":전세";
                    Double areaScore = property.getAreaInPyeong() != null ? property.getAreaInPyeong() : 0.0;
                    redisHandler.redisTemplate.opsForZSet().add(charterAreaIndexKey, propertyId, areaScore);
                    indexKeyTracker.get("charterArea").add(charterAreaIndexKey);

                } else if ("월세".equals(leaseType)) {
                    // 월세 전용 필드 추가 (보증금 + 월세금 모두 필요)
                    propertyHash.put("deposit", property.getDeposit() != null ? property.getDeposit().toString() : "0");
                    propertyHash.put("monthlyRent", property.getMonthlyRent() != null ? property.getMonthlyRent().toString() : "0");

                    // === 월세 매물 처리 ===

                    // [저장소 1] 매물 원본 데이터 (Hash)
                    // 키 패턴: property:monthly:{propertyId}
                    String monthlyPropertyKey = "property:monthly:" + propertyId;
                    redisHandler.redisTemplate.opsForHash().putAll(monthlyPropertyKey, propertyHash);

                    // [저장소 2] 보증금 인덱스 (Sorted Set) - 핵심 수정: 보증금만 저장
                    // 키 패턴: idx:deposit:{지역구명}
                    String depositIndexKey = "idx:deposit:" + districtName;
                    Double depositPrice = property.getDeposit() != null ? property.getDeposit().doubleValue() : 0.0;
                    redisHandler.redisTemplate.opsForZSet().add(depositIndexKey, propertyId, depositPrice);
                    indexKeyTracker.get("deposit").add(depositIndexKey);

                    // [저장소 3] 월세금 인덱스 (Sorted Set) - 핵심 수정: 월세금만 저장
                    // 키 패턴: idx:monthlyRent:{지역구명}:월세
                    String monthlyRentIndexKey = "idx:monthlyRent:" + districtName + ":월세";
                    Double monthlyRentPrice = property.getMonthlyRent() != null ? property.getMonthlyRent().doubleValue() : 0.0;
                    redisHandler.redisTemplate.opsForZSet().add(monthlyRentIndexKey, propertyId, monthlyRentPrice);
                    indexKeyTracker.get("monthlyRent").add(monthlyRentIndexKey);

                    // [저장소 4] 평수 인덱스 (Sorted Set)
                    // 키 패턴: idx:area:{지역구명}:월세
                    String monthlyAreaIndexKey = "idx:area:" + districtName + ":월세";
                    Double areaScore = property.getAreaInPyeong() != null ? property.getAreaInPyeong() : 0.0;
                    redisHandler.redisTemplate.opsForZSet().add(monthlyAreaIndexKey, propertyId, areaScore);
                    indexKeyTracker.get("monthlyArea").add(monthlyAreaIndexKey);

                } else {
                    log.warn("알 수 없는 임대유형: {} - 매물 ID: {}", leaseType, propertyId);
                    continue;
                }

                successCount++;
                // 지역구별 통계 업데이트
                districtStats.merge(districtName, 1, Integer::sum);

            } catch (Exception e) {
                log.debug("매물 Redis 저장 실패: {}", property.getPropertyId(), e);
            }
        }

        // 통계 로그 출력
        log.info("Redis 데이터 적재 완료 - 성공: {}건 / 전체: {}건", successCount, properties.size());
        log.info("지역구별 매물 수: {}", districtStats);

        // 각 인덱스별 생성된 Key 개수 통계 출력
        log.info("=== 생성된 인덱스별 Key 개수 통계 ===");
        log.info("- 전세금 인덱스 (idx:charterPrice): {}개 Key", indexKeyTracker.get("charterPrice").size());
        log.info("- 보증금 인덱스 (idx:deposit): {}개 Key", indexKeyTracker.get("deposit").size());
        log.info("- 월세금 인덱스 (idx:monthlyRent): {}개 Key", indexKeyTracker.get("monthlyRent").size());
        log.info("- 전세 평수 인덱스 (idx:area:*:전세): {}개 Key", indexKeyTracker.get("charterArea").size());
        log.info("- 월세 평수 인덱스 (idx:area:*:월세): {}개 Key", indexKeyTracker.get("monthlyArea").size());

        // 생성된 Redis 구조 확인 로그
        log.info("=== 생성된 Redis 구조 ===");
        log.info("전세 매물:");
        log.info("- 매물 원본 데이터: property:charter:{{id}} Hash 구조");
        log.info("- 전세금 인덱스: idx:charterPrice:{{지역구명}} Sorted Set");
        log.info("- 평수 인덱스: idx:area:{{지역구명}}:전세 Sorted Set");

        log.info("월세 매물:");
        log.info("- 매물 원본 데이터: property:monthly:{{id}} Hash 구조");
        log.info("- 보증금 인덱스: idx:deposit:{{지역구명}} Sorted Set");
        log.info("- 월세금 인덱스: idx:monthlyRent:{{지역구명}}:월세 Sorted Set");
        log.info("- 평수 인덱스: idx:area:{{지역구명}}:월세 Sorted Set");
    }

    /**
     * B-04: 지역구별 정규화 범위 계산 및 저장 - 수정된 버전
     * 월세의 경우 보증금과 월세금을 각각 독립적으로 정규화 범위 계산
     */
    /**
     * B-04: 지역구별 정규화 범위 계산 및 저장 - 수정된 버전
     * 월세의 경우 보증금과 월세금을 각각 독립적으로 정규화 범위 계산
     */
    private void calculateAndStoreNormalizationBounds(List<Property> properties) {
        log.info("=== B-04: 지역구별 정규화 범위 계산 및 저장 시작 ===");

        // 1. 데이터 그룹핑: 지역구명 + 임대유형
        Map<String, List<Property>> groupedProperties = groupPropertiesByDistrictAndLeaseType(properties);

        int processedGroups = 0;
        int validGroups = 0;
        int skippedGroupsInsufficientData = 0;
        int skippedGroupsInvalidData = 0;
        String currentTime = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        // 그룹별 상세 통계 출력
        log.info("=== 그룹별 매물 수 현황 ===");
        for (Map.Entry<String, List<Property>> entry : groupedProperties.entrySet()) {
            log.info("그룹 [{}]: {}건", entry.getKey(), entry.getValue().size());
        }

        for (Map.Entry<String, List<Property>> entry : groupedProperties.entrySet()) {
            String groupKey = entry.getKey();
            List<Property> groupProperties = entry.getValue();

            try {
                log.debug("그룹 [{}] 처리 시작 - 매물 수: {}건", groupKey, groupProperties.size());

                // 품질 보장: 최소 데이터 요구사항 검증 (그룹당 1개 이상 매물로 완화)
                if (groupProperties.size() < 1) {
                    log.warn("그룹 [{}] 스킵 - 매물 없음 ({}건)", groupKey, groupProperties.size());
                    skippedGroupsInsufficientData++;
                    continue;
                }

                // 2. 수정된 정규화 범위 계산 (월세의 경우 보증금과 월세금 분리)
                NormalizationBounds bounds = calculateBoundsForGroupFixed(groupProperties, groupKey);

                if (bounds == null) {
                    log.warn("그룹 [{}] 스킵 - 유효한 데이터 부족", groupKey);
                    skippedGroupsInvalidData++;
                    continue;
                }

                // 4. Redis 저장 (수정된 호출)
                // groupProperties 리스트를 직접 전달하도록 변경
                storeBoundsToRedisFixed(groupKey, bounds, groupProperties, currentTime);

                validGroups++;
                log.info("그룹 [{}] 처리 완료 - 범위: 가격[{} ~ {}], 평수[{} ~ {}]",
                        groupKey, bounds.minPrice, bounds.maxPrice, bounds.minArea, bounds.maxArea);

            } catch (Exception e) {
                log.error("그룹 [{}] 처리 중 오류 발생", groupKey, e);
            }
            processedGroups++;
        }

        log.info("=== B-04: 지역구별 정규화 범위 계산 완료 ===");
        log.info("- 전체 그룹 수: {}", groupedProperties.size());
        log.info("- 처리된 그룹: {}", processedGroups);
        log.info("- 유효한 그룹: {}", validGroups);
        log.info("- 매물 수 부족으로 스킵된 그룹: {}", skippedGroupsInsufficientData);
        log.info("- 유효 데이터 부족으로 스킵된 그룹: {}", skippedGroupsInvalidData);
    }

    /**
     * 수정된 정규화 범위 계산 - 월세의 경우 보증금과 월세금을 각각 처리
     */
    private NormalizationBounds calculateBoundsForGroupFixed(List<Property> groupProperties, String groupKey) {
        String[] keyParts = groupKey.split(":");
        if (keyParts.length != 2) {
            log.warn("잘못된 그룹 키 형식: {}", groupKey);
            return null;
        }

        String leaseType = keyParts[1];
        List<Double> prices = new ArrayList<>();
        List<Double> areas = new ArrayList<>();

        for (Property property : groupProperties) {
            // 임대유형별 가격 계산 방식 분리
            if ("전세".equals(leaseType)) {
                // 전세: 보증금(전세금)만 사용
                if (property.getDeposit() != null && property.getDeposit() > 0) {
                    prices.add(property.getDeposit().doubleValue());
                }
            } else if ("월세".equals(leaseType)) {
                // 월세: 보증금만 사용 (월세금은 별도 정규화)
                if (property.getDeposit() != null && property.getDeposit() > 0) {
                    prices.add(property.getDeposit().doubleValue());
                }
            }

            // 평수 수집
            if (property.getAreaInPyeong() != null && property.getAreaInPyeong() > 0) {
                areas.add(property.getAreaInPyeong());
            }
        }

        if (prices.isEmpty() || areas.isEmpty()) {
            log.debug("그룹 [{}] - 유효한 가격 또는 평수 데이터 없음", groupKey);
            return null;
        }

        // 정규화 범위 계산
        double minPrice = Collections.min(prices);
        double maxPrice = Collections.max(prices);
        double minArea = Collections.min(areas);
        double maxArea = Collections.max(areas);

        // 제로 분산 방지
        if (minPrice == maxPrice) {
            maxPrice = minPrice + 1000.0;
            log.debug("그룹 [{}] - 가격 제로 분산 방지: {} -> {}", groupKey, minPrice, maxPrice);
        }

        if (minArea == maxArea) {
            maxArea = minArea + 1.0;
            log.debug("그룹 [{}] - 평수 제로 분산 방지: {} -> {}", groupKey, minArea, maxArea);
        }

        return new NormalizationBounds(minPrice, maxPrice, minArea, maxArea);
    }

    /**
     * 월세 전용 정규화 범위 저장 - 보증금과 월세금 각각 저장
     */
    /**
     * 월세 전용 정규화 범위 저장 - 보증금과 월세금 각각 저장 (수정된 버전)
     */
    private void storeBoundsToRedisFixed(String groupKey, NormalizationBounds bounds,
                                         List<Property> groupProperties, String currentTime) {
        String[] keyParts = groupKey.split(":");
        if (keyParts.length != 2) return;

        String districtName = keyParts[0];
        String leaseType = keyParts[1];
        int propertyCount = groupProperties.size();

        if ("전세".equals(leaseType)) {
            // 전세용 정규화 범위 저장
            String redisKey = "bounds:" + districtName + ":전세";
            Map<String, Object> boundsHash = new HashMap<>();
            boundsHash.put("minPrice", String.valueOf(bounds.minPrice));    // 전세금 최소
            boundsHash.put("maxPrice", String.valueOf(bounds.maxPrice));    // 전세금 최대
            boundsHash.put("minArea", String.valueOf(bounds.minArea));
            boundsHash.put("maxArea", String.valueOf(bounds.maxArea));
            boundsHash.put("propertyCount", String.valueOf(propertyCount));
            boundsHash.put("lastUpdated", currentTime);

            redisHandler.redisTemplate.opsForHash().putAll(redisKey, boundsHash);

        } else if ("월세".equals(leaseType)) {
            // 월세용 정규화 범위 저장 - 보증금과 월세금 각각 계산

            // 월세금 범위 별도 계산
            List<Double> monthlyRents = new ArrayList<>();
            // 전달받은 groupProperties를 직접 사용하여 NullPointerException 회피
            for (Property property : groupProperties) {
                if (property.getMonthlyRent() != null && property.getMonthlyRent() > 0) {
                    monthlyRents.add(property.getMonthlyRent().doubleValue());
                }
            }

            double minMonthlyRent = monthlyRents.isEmpty() ? 0.0 : Collections.min(monthlyRents);
            double maxMonthlyRent = monthlyRents.isEmpty() ? 500.0 : Collections.max(monthlyRents);

            // 제로 분산 방지
            if (minMonthlyRent == maxMonthlyRent) {
                maxMonthlyRent = minMonthlyRent + 10.0;
            }

            String redisKey = "bounds:" + districtName + ":월세";
            Map<String, Object> boundsHash = new HashMap<>();
            boundsHash.put("minDeposit", String.valueOf(bounds.minPrice));      // 보증금 최소
            boundsHash.put("maxDeposit", String.valueOf(bounds.maxPrice));      // 보증금 최대
            boundsHash.put("minMonthlyRent", String.valueOf(minMonthlyRent));   // 월세금 최소
            boundsHash.put("maxMonthlyRent", String.valueOf(maxMonthlyRent));   // 월세금 최대
            boundsHash.put("minArea", String.valueOf(bounds.minArea));
            boundsHash.put("maxArea", String.valueOf(bounds.maxArea));
            boundsHash.put("propertyCount", String.valueOf(propertyCount));
            boundsHash.put("lastUpdated", currentTime);

            redisHandler.redisTemplate.opsForHash().putAll(redisKey, boundsHash);
        }

        log.debug("Redis 저장 완료 - Key: bounds:{}:{}, Count: {}", districtName, leaseType, propertyCount);
    }

    /**
     * 1. 데이터 그룹핑: 지역구명 + 임대유형으로 매물 그룹핑
     */
    private Map<String, List<Property>> groupPropertiesByDistrictAndLeaseType(List<Property> properties) {

        Map<String, List<Property>> groupedProperties = new HashMap<>();

        int validPropertyCount = 0;
        int invalidPropertyCount = 0;

        // 제외 이유별 통계
        int nullDistrictName = 0;
        int nullLeaseType = 0;
        int nullDeposit = 0;
        int nullMonthlyRent = 0;
        int nullAreaInPyeong = 0;
        int zeroOrNegativeArea = 0;

        for (Property property : properties) {
            // 상세 유효성 검증
            boolean isValid = true;

            if (property.getDistrictName() == null) {
                nullDistrictName++;
                isValid = false;
            }
            if (property.getLeaseType() == null) {
                nullLeaseType++;
                isValid = false;
            }
            if (property.getDeposit() == null) {
                nullDeposit++;
                isValid = false;
            }
            if (property.getMonthlyRent() == null) {
                nullMonthlyRent++;
                isValid = false;
            }
            if (property.getAreaInPyeong() == null) {
                nullAreaInPyeong++;
                isValid = false;
            }
            if (property.getAreaInPyeong() != null && property.getAreaInPyeong() <= 0) {
                zeroOrNegativeArea++;
                isValid = false;
            }

            if (!isValid) {
                invalidPropertyCount++;
                continue;
            }

            // 그룹 키 형태: {지역구명}:{임대유형}
            String groupKey = property.getDistrictName() + ":" + property.getLeaseType();
            groupedProperties.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(property);
            validPropertyCount++;
        }

        log.info("=== 매물 그룹핑 상세 결과 ===");
        log.info("- 전체 매물: {}건", properties.size());
        log.info("- 유효한 매물: {}건", validPropertyCount);
        log.info("- 제외된 매물: {}건", invalidPropertyCount);
        log.info("- 생성된 그룹 수: {}개", groupedProperties.size());

        log.info("=== 제외 이유별 통계 ===");
        log.info("- 지역구명 null: {}건", nullDistrictName);
        log.info("- 임대유형 null: {}건", nullLeaseType);
        log.info("- 보증금 null: {}건", nullDeposit);
        log.info("- 월세 null: {}건", nullMonthlyRent);
        log.info("- 평수 null: {}건", nullAreaInPyeong);
        log.info("- 평수 0 이하: {}건", zeroOrNegativeArea);

        // 각 지역구별로 전세/월세 그룹 현황 확인
        Map<String, Set<String>> districtLeaseTypes = new HashMap<>();
        for (String groupKey : groupedProperties.keySet()) {
            String[] parts = groupKey.split(":");
            if (parts.length == 2) {
                String district = parts[0];
                String leaseType = parts[1];
                districtLeaseTypes.computeIfAbsent(district, k -> new HashSet<>()).add(leaseType);
            }
        }

        log.info("=== 지역구별 임대유형 현황 ===");
        for (Map.Entry<String, Set<String>> entry : districtLeaseTypes.entrySet()) {
            log.info("{}: {}", entry.getKey(), entry.getValue());
        }

        return groupedProperties;
    }

    /**
     * 정규화 계산을 위한 매물 유효성 검증
     */
    private boolean isValidPropertyForNormalization(Property property) {
        // 각 필드별로 상세 검증 및 로그 출력
        if (property.getDistrictName() == null) {
            log.debug("매물 제외 - 지역구명 null: ID={}", property.getPropertyId());
            return false;
        }

        if (property.getLeaseType() == null) {
            log.debug("매물 제외 - 임대유형 null: ID={}, 지역구={}",
                    property.getPropertyId(), property.getDistrictName());
            return false;
        }

        if (property.getDeposit() == null) {
            log.debug("매물 제외 - 보증금 null: ID={}, 지역구={}, 임대유형={}",
                    property.getPropertyId(), property.getDistrictName(), property.getLeaseType());
            return false;
        }

        if (property.getMonthlyRent() == null) {
            log.debug("매물 제외 - 월세 null: ID={}, 지역구={}, 임대유형={}",
                    property.getPropertyId(), property.getDistrictName(), property.getLeaseType());
            return false;
        }

        if (property.getAreaInPyeong() == null) {
            log.debug("매물 제외 - 평수 null: ID={}, 지역구={}, 임대유형={}",
                    property.getPropertyId(), property.getDistrictName(), property.getLeaseType());
            return false;
        }

        if (property.getAreaInPyeong() <= 0) {
            log.debug("매물 제외 - 평수 0 이하: ID={}, 지역구={}, 임대유형={}, 평수={}",
                    property.getPropertyId(), property.getDistrictName(), property.getLeaseType(), property.getAreaInPyeong());
            return false;
        }

        return true;
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

    /**
     * B-05: 지역구별 안전성 점수 계산 및 Redis 저장
     * 분석 보고서에 따른 범죄율 기반 안전성 점수 계산: (범죄 발생 건수 ÷ 인구수) × 100,000을 종속변수로 하여
     * 유흥주점 수(99.67% 가중치)와 인구수(0.33% 가중치)를 독립변수로 적용
     */
    private void calculateAndStoreSafetyScores() {
        log.info("=== B-05: 지역구별 안전성 점수 계산 및 Redis 저장 시작 ===");

        try {
            // 1. 범죄 데이터 수집 (지역구별 이 범죄 발생 건수)
            List<DistrictCrimeCountDto> crimeData = crimeRepository.findCrimeCount();
            Map<String, Long> crimeCountMap = new HashMap<>();

            log.info("=== 범죄 데이터 수집 결과 ===");
            for (DistrictCrimeCountDto crime : crimeData) {
                String districtName = crime.getDistrictName();
                Long totalCrime = crime.getTotalOccurrence();
                crimeCountMap.put(districtName, totalCrime);
                log.info("범죄 데이터 - {}: {}건", districtName, totalCrime);
            }

            // 2. 인구 데이터 수집 (지역구별 인구수) - 범죄율 계산 및 독립변수로 사용
            List<Object[]> populationCountData = populationRepository.findPopulationCountByDistrict();
            Map<String, Long> populationCountMap = new HashMap<>();

            log.info("=== 인구 데이터 수집 결과 ===");
            for (Object[] row : populationCountData) {
                String districtName = (String) row[0];
                Number populationCount = (Number) row[1];
                Long population = populationCount.longValue();
                populationCountMap.put(districtName, population);
                log.info("인구 데이터 - {}: {}명", districtName, population);
            }

            // 3. 유흥업소 데이터 수집 (지역구별 영업중인 유흥주점 수)
            List<Object[]> entertainmentData = entertainmentRepository.findActiveEntertainmentCountByDistrict();
            Map<String, Double> entertainmentCountMap = new HashMap<>();

            log.info("=== 유흥업소 데이터 수집 결과 ===");
            for (Object[] row : entertainmentData) {
                String districtName = (String) row[0];
                Number count = (Number) row[1];
                double entertainmentCount = count.doubleValue();
                entertainmentCountMap.put(districtName, entertainmentCount);
                log.info("유흥업소 - {}: {}개", districtName, entertainmentCount);
            }

            // 4. 지역구별 범죄율 계산 (인구 10만명당 범죄 발생률)
            Map<String, Double> crimeRateMap = new HashMap<>();

            log.info("=== 지역구별 범죄율 계산 (인구 10만명당) ===");
            for (String districtName : SEOUL_DISTRICT_CODES.values()) {
                Long crimeCount = crimeCountMap.getOrDefault(districtName, 0L);
                Long population = populationCountMap.getOrDefault(districtName, 1L); // 0으로 나누기 방지

                if (population > 0) {
                    double crimeRate = (crimeCount.doubleValue() / population.doubleValue()) * 100000.0;
                    crimeRateMap.put(districtName, crimeRate);
                    log.debug("범죄율 - {}: {:.2f}건/10만명 (범죄:{}건, 인구:{}명)",
                            districtName, crimeRate, crimeCount, population);
                } else {
                    log.warn("지역구 [{}] 인구 데이터 없음, 범죄율 계산 제외", districtName);
                }
            }

            // 5. 정규화를 위한 최대/최소값 계산
            double maxEntertainment = entertainmentCountMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
            double minEntertainment = entertainmentCountMap.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            double maxPopulation = populationCountMap.values().stream().mapToDouble(v -> v.doubleValue()).max().orElse(1.0);
            double minPopulation = populationCountMap.values().stream().mapToDouble(v -> v.doubleValue()).min().orElse(0.0);

            log.info("정규화 범위 - 유흥업소: [{} ~ {}], 인구수: [{} ~ {}]",
                    minEntertainment, maxEntertainment, minPopulation, maxPopulation);

            // 6. 모든 지역구의 원본 안전성 점수 계산 (정규화 전)
            Map<String, Double> rawSafetyScoreMap = new HashMap<>();

            // 7. 서울시 25개 자치구별 원본 안전성 점수 계산 (분석 보고서 공식 적용)
            Map<String, Double> safetyScoreMap = new HashMap<>();
            int processedDistricts = 0;
            int successfulDistricts = 0;

            for (String districtName : SEOUL_DISTRICT_CODES.values()) {
                try {
                    // 해당 지역구의 실제 범죄율이 있는지 확인
                    if (!crimeRateMap.containsKey(districtName)) {
                        log.warn("지역구 [{}] 범죄율 데이터 없음, 건너뜀", districtName);
                        processedDistricts++;
                        continue;
                    }

                    Double entertainmentCount = entertainmentCountMap.getOrDefault(districtName, 0.0);
                    Long populationCount = populationCountMap.getOrDefault(districtName, 0L);
                    Double actualCrimeRate = crimeRateMap.get(districtName);

                    // 8. 독립변수 정규화 (0~1 범위로 변환)
                    double normalizedEntertainment = 0.0;
                    if (maxEntertainment > minEntertainment) {
                        normalizedEntertainment = (entertainmentCount - minEntertainment) / (maxEntertainment - minEntertainment);
                    }

                    double normalizedPopulation = 0.0;
                    if (maxPopulation > minPopulation) {
                        normalizedPopulation = (populationCount.doubleValue() - minPopulation) / (maxPopulation - minPopulation);
                    }

                    // 9. 분석 보고서 공식: 범죄위험도 = 1.0229 × 정규화된_유흥주점밀도 - 0.0034 × 정규화된_인구밀도
                    double crimeRiskScore = (1.0229 * normalizedEntertainment) - (0.0034 * normalizedPopulation);

                    // 10. 분석 보고서 공식: 최종_안전성_점수 = 100 - (범죄위험도 × 10)
                    double rawSafetyScore = 100 - (crimeRiskScore * 10);

                    rawSafetyScoreMap.put(districtName, rawSafetyScore);

                    log.debug("지역구 [{}] 원본 계산 - 유흥업소: {} (정규화: {:.4f}), 인구수: {} (정규화: {:.4f}), 위험도: {:.6f}, 원본점수: {:.2f}",
                            districtName, entertainmentCount, normalizedEntertainment, populationCount, normalizedPopulation, crimeRiskScore, rawSafetyScore);

                    successfulDistricts++;

                } catch (Exception e) {
                    log.error("지역구 [{}] 안전성 점수 계산 실패", districtName, e);
                }

                processedDistricts++;
            }

            // 11. 원본 안전성 점수를 0~100 구간으로 정규화
            double maxRawScore = rawSafetyScoreMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(100.0);
            double minRawScore = rawSafetyScoreMap.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);

            log.info("원본 안전성 점수 범위: [{:.2f} ~ {:.2f}]", minRawScore, maxRawScore);

            // 12. 최종 안전성 점수 정규화 (0~100 구간 분배)
            for (Map.Entry<String, Double> entry : rawSafetyScoreMap.entrySet()) {
                String districtName = entry.getKey();
                Double rawScore = entry.getValue();

                double normalizedScore = 0.0;
                if (maxRawScore > minRawScore) {
                    normalizedScore = ((rawScore - minRawScore) / (maxRawScore - minRawScore)) * 100.0;
                }

                safetyScoreMap.put(districtName, normalizedScore);

                log.debug("지역구 [{}] 최종 - 원본점수: {:.2f}, 정규화점수: {:.2f}", districtName, rawScore, normalizedScore);
            }

            // 10. Redis에 안전성 점수 저장
            storeSafetyScoresToRedis(safetyScoreMap);

            log.info("=== B-05: 지역구별 안전성 점수 계산 완료 ===");
            log.info("- 처리된 지역구: {}개", processedDistricts);
            log.info("- 성공한 지역구: {}개", successfulDistricts);
            log.info("- 범죄 데이터: {}개 지역구", crimeCountMap.size());
            log.info("- 인구 데이터: {}개 지역구", populationCountMap.size());
            log.info("- 유흥업소 데이터: {}개 지역구", entertainmentCountMap.size());

            // 범죄율 통계 출력
            if (!crimeRateMap.isEmpty()) {
                double maxCrimeRate = crimeRateMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
                double minCrimeRate = crimeRateMap.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
                double avgCrimeRate = crimeRateMap.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

                log.info("=== 범죄율 통계 (인구 10만명당) ===");
                log.info("- 최고 범죄율: " + maxCrimeRate + " 건");
                log.info("- 최저 범죄율: " + minCrimeRate + " 건");
                log.info("- 평균 범죄율: " + avgCrimeRate + " 건");
            }

        } catch (Exception e) {
            log.error("B-05 안전성 점수 계산 중 오류 발생", e);
        }
    }

    /**
     * Redis에 지역구별 안전성 점수 저장
     * 키 패턴: safety:{지역구명}
     */
    private void storeSafetyScoresToRedis(Map<String, Double> safetyScoreMap) {
        log.info("Redis 안전성 점수 저장 시작 - 이 {}개 지역구", safetyScoreMap.size());

        int successCount = 0;
        String currentTime = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        for (Map.Entry<String, Double> entry : safetyScoreMap.entrySet()) {
            String districtName = entry.getKey();
            Double safetyScore = entry.getValue();

            try {
                String redisKey = "safety:" + districtName;

                Map<String, Object> safetyHash = new HashMap<>();
                safetyHash.put("districtName", districtName);
                safetyHash.put("safetyScore", String.valueOf(safetyScore));
                safetyHash.put("lastUpdated", currentTime);
                safetyHash.put("version", "1.0");

                redisHandler.redisTemplate.opsForHash().putAll(redisKey, safetyHash);
                successCount++;

                log.debug("Redis 저장 완료 - Key: {}, Score: {:.2f}", redisKey, safetyScore);

            } catch (Exception e) {
                log.error("지역구 [{}] Redis 저장 실패", districtName, e);
            }
        }

        log.info("Redis 안전성 점수 저장 완료 - 성공: {}개 / 전체: {}개", successCount, safetyScoreMap.size());

        // 저장된 안전성 점수 통계
        log.info("=== 안전성 점수 통계 ===");
        double maxScore = safetyScoreMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double minScore = safetyScoreMap.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double avgScore = safetyScoreMap.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        log.info("- 최고 안전성 점수: {}점", String.format("%.2f", maxScore));
        log.info("- 최저 안전성 점수: {}점", String.format("%.2f", minScore));
        log.info("- 평균 안전성 점수: {}점", String.format("%.2f", avgScore));

        // 상위/하위 3개 지역구 출력
        List<Map.Entry<String, Double>> sortedEntries = safetyScoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .toList();

        log.info("- 안전성 상위 3개 지역구:");
        for (int i = 0; i < Math.min(3, sortedEntries.size()); i++) {
            Map.Entry<String, Double> entry = sortedEntries.get(i);
            log.info("  {}위: {} ({}점)", i + 1, entry.getKey(), String.format("%.2f", entry.getValue()));
        }

        log.info("- 안전성 하위 3개 지역구:");
        for (int i = Math.max(0, sortedEntries.size() - 3); i < sortedEntries.size(); i++) {
            Map.Entry<String, Double> entry = sortedEntries.get(i);
            log.info("  {}위: {} ({}점)", sortedEntries.size() - i, entry.getKey(), String.format("%.2f", entry.getValue()));
        }

        log.info("생성된 Redis 구조:");
        log.info("- 안전성 점수: safety:{{지역구명}} Hash 구조");
    }
}