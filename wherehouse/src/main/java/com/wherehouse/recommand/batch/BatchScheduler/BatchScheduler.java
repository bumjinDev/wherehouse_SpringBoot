package com.wherehouse.recommand.batch.BatchScheduler;

import com.wherehouse.recommand.batch.dto.Property;
import com.wherehouse.recommand.batch.event.DataCollectionCompletedEvent;
import com.wherehouse.recommand.batch.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 부동산 매물 데이터 배치 처리 스케줄러 (TO-BE 아키텍처)
 * * 수정사항 (2025-12-05):
 * - API 응답 태그명 수정 (한글 -> 영문)
 * - 날짜 파싱 로직 수정 (dealYear/Month/Day 조합)
 * * @author 정범진
 * @since 2025-12-05
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchScheduler {

    private final IdGenerator idGenerator;
    private final ApplicationEventPublisher eventPublisher;

    private final String serviceKey = System.getenv("MOLIT_RENT_API_SERVICE_KEY");

    @Value("${molit.rent-api.base-url}")
    private String baseUrl;

    private static final String API_ENDPOINT = "/getRTMSDataSvcAptRent";
    private static final String NUM_OF_ROWS = "1000";

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

    // 매달 새벽 4시 1분 30초 수행
    @Scheduled(cron = "30 1 4 28 * *")
    // 테스트를 위해 즉시 실행 (필요 시 cron으로 변경)
    @Scheduled(fixedDelay = Long.MAX_VALUE, initialDelay = 5000)
    public void executeBatchProcess() {
        log.info("=== 부동산 매물 데이터 배치 처리 시작 (Data Collection Phase) ===");

        long startTime = System.currentTimeMillis();

        try {
            if (serviceKey == null || serviceKey.isEmpty()) {
                log.info("FATAL: 환경변수 'MOLIT_RENT_API_SERVICE_KEY'가 설정되지 않았습니다. 배치를 중단합니다.");
                return;
            }

            // log.info("serverkey : {} ", serviceKey);

            List<Property> allProperties = collectAllDistrictData();

            if (allProperties.isEmpty()) {
                log.warn("수집된 매물 데이터가 없습니다. 배치 프로세스를 종료합니다.");
                return;
            }

            log.info("총 {}건의 매물 데이터를 수집했습니다.", allProperties.size());

            Map<String, List<Property>> classifiedProperties = classifyPropertiesByLeaseType(allProperties);
            List<Property> charterProperties = classifiedProperties.get("전세");
            List<Property> monthlyProperties = classifiedProperties.get("월세");

            log.info("전세 매물: {}건, 월세 매물: {}건", charterProperties.size(), monthlyProperties.size());

            publishDataCollectionCompletedEvent(charterProperties, monthlyProperties);

            long endTime = System.currentTimeMillis();
            long elapsedTime = endTime - startTime;

            log.info("=== 데이터 수집 완료 (BatchScheduler 역할 종료) ===");
            log.info("소요 시간: {}ms ({}초)", elapsedTime, elapsedTime / 1000);

        } catch (Exception e) {
            log.error("배치 처리 중 오류 발생", e);
            throw new RuntimeException("배치 처리 실패", e);
        }
    }

    private List<Property> collectAllDistrictData() {
        log.info("서울시 25개 자치구 매물 데이터 수집 시작");

        List<Property> allProperties = new ArrayList<>();
        String dealYmd = LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyyMM"));

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
                Thread.sleep(200);

            } catch (Exception e) {
                log.error(">>> {} 매물 데이터 수집 실패", districtName, e);
            }
        }

        log.info("전 지역구 매물 데이터 수집 완료: 총 {}건", allProperties.size());
        return allProperties;
    }

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

                List<Property> pageProperties = parseXmlAndExtractProperties(xmlResponse, districtName);

                if (pageProperties.isEmpty()) {
                    log.debug("{}페이지에서 유효한 데이터가 없습니다. 수집을 종료합니다.", pageNo);
                    break;
                }

                districtProperties.addAll(pageProperties);

                // 첫 페이지에서 totalCount 파싱 시도 (선택 사항)
                if (pageNo == 1) {
                    // extractTotalCount 메서드 내부 로직은 유지하되, 필요 시 활용
                }

                pageNo++;

            } catch (Exception e) {
                log.error("{}페이지 데이터 수집 중 오류 발생", pageNo, e);
                break;
            }

        } while (true);

        return districtProperties;
    }

    private String callRentAPI(String lawdCd, String dealYmd, String pageNo) {
        try {
            StringBuilder urlBuilder = new StringBuilder(baseUrl);
            urlBuilder.append(API_ENDPOINT);

            // [중요] 이미 인코딩된 키라고 가정
            urlBuilder.append("?serviceKey=").append(serviceKey);

            urlBuilder.append("&LAWD_CD=").append(URLEncoder.encode(lawdCd, "UTF-8"));
            urlBuilder.append("&DEAL_YMD=").append(URLEncoder.encode(dealYmd, "UTF-8"));
            urlBuilder.append("&pageNo=").append(pageNo);
            urlBuilder.append("&numOfRows=").append(NUM_OF_ROWS);

            URL url = new URL(urlBuilder.toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-type", "application/json");

            BufferedReader rd;
            if (conn.getResponseCode() >= 200 && conn.getResponseCode() <= 300) {
                rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            } else {
                rd = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            }

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                sb.append(line);
            }
            rd.close();
            conn.disconnect();

            return sb.toString();

        } catch (Exception e) {
            log.error("API 호출 중 통신 오류 발생", e);
            return null;
        }
    }

    private int extractTotalCount(String xmlResponse) {
        // totalCount 태그는 API마다 다를 수 있으나 보통 영어로 옴
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xmlResponse)));

            NodeList totalCountNodes = document.getElementsByTagName("totalCount"); // 보통 소문자 시작
            if (totalCountNodes.getLength() > 0) {
                return Integer.parseInt(totalCountNodes.item(0).getTextContent());
            }
        } catch (Exception e) {
            // 무시
        }
        return 0;
    }

    /**
     * 핵심 수정: 태그 이름을 API 실제 응답(영문)에 맞춰 변경
     */
    private List<Property> parseXmlAndExtractProperties(String xmlResponse, String districtName) {
        List<Property> properties = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xmlResponse)));

            // 에러 체크
            NodeList resultCodeNode = document.getElementsByTagName("resultCode");
            NodeList resultMsgNode = document.getElementsByTagName("resultMsg");

            if (resultMsgNode.getLength() > 0) {
                String msg = resultMsgNode.item(0).getTextContent();
                String code = resultCodeNode.getLength() > 0 ? resultCodeNode.item(0).getTextContent() : "UNKNOWN";

                // 정상(00, 000)이 아니면 에러 처리
                if (!"00".equals(code) && !"000".equals(code) && !"OK".equalsIgnoreCase(msg)) {
                    log.error(">>> API ERROR DETECTED for {}: Code={}, Msg={}", districtName, code, msg);
                    return properties;
                }
            }

            NodeList items = document.getElementsByTagName("item");

            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);

                try {
                    // [수정] 한글 태그 -> 영문 태그로 변경
                    String aptNm = getTagValue("aptNm", item);          // 아파트 -> aptNm
                    String excluUseArStr = getTagValue("excluUseAr", item); // 전용면적 -> excluUseAr
                    String floorStr = getTagValue("floor", item);       // 층 -> floor
                    String buildYearStr = getTagValue("buildYear", item); // 건축년도 -> buildYear

                    // [수정] 날짜: dealYear, dealMonth, dealDay 합쳐서 처리
                    String dealYear = getTagValue("dealYear", item);
                    String dealMonth = getTagValue("dealMonth", item);
                    String dealDay = getTagValue("dealDay", item);
                    // 포맷: 20251120 형태로 조합 (한 자리 월/일 처리 필요)
                    String dealDate = String.format("%s%02d%02d",
                            dealYear,
                            parseIntOrDefault(dealMonth, 0),
                            parseIntOrDefault(dealDay, 0));

                    String depositStr = getTagValue("deposit", item);       // 보증금액 -> deposit
                    String monthlyRentStr = getTagValue("monthlyRent", item); // 월세금액 -> monthlyRent
                    String umdNm = getTagValue("umdNm", item);             // 법정동 -> umdNm
                    String jibun = getTagValue("jibun", item);             // 지번 -> jibun
                    String sggCd = getTagValue("sggCd", item);             // 지역코드 -> sggCd

                    // 필수 필드 검증
                    if (!isValidPropertyData(aptNm, excluUseArStr, floorStr, depositStr, umdNm, jibun, sggCd)) {
                        // 로그가 너무 많아질 수 있으므로 debug 레벨로 낮춤
                        // log.debug("필수 필드 누락: {}", aptNm);
                        continue;
                    }

                    Double excluUseAr = parseDoubleOrDefault(excluUseArStr, 0.0);
                    Integer floor = parseIntOrDefault(floorStr, 0);
                    Integer buildYear = parseIntOrDefault(buildYearStr, 0);
                    Integer deposit = parseIntOrDefault(depositStr.replace(",", ""), 0);
                    Integer monthlyRent = parseIntOrDefault(monthlyRentStr.replace(",", ""), 0);

                    if (excluUseAr <= 0) {
                        continue;
                    }

                    String propertyId = idGenerator.generatePropertyId(
                            sggCd, jibun, aptNm, String.valueOf(floor), String.valueOf(excluUseAr)
                    );

                    Property property = Property.builder()
                            .propertyId(propertyId)
                            .aptNm(aptNm)
                            .excluUseAr(excluUseAr)
                            .floor(floor)
                            .buildYear(buildYear)
                            .dealDate(dealDate) // 조합된 날짜 사용
                            .deposit(deposit)
                            .monthlyRent(monthlyRent)
                            .umdNm(umdNm)
                            .jibun(jibun)
                            .sggCd(sggCd)
                            .districtName(districtName)
                            .rgstDate(LocalDate.now().toString())
                            .build();

                    property.calculateAreaInPyeong();
                    property.determineLeaseType();
                    property.generateAddress();

                    properties.add(property);

                } catch (Exception e) {
                    log.warn("개별 매물 파싱 중 오류 (무시하고 계속 진행): {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("XML 파싱 중 치명적 오류", e);
        }

        return properties;
    }

    private boolean isValidPropertyData(String aptNm, String excluUseAr, String floor,
                                        String deposit, String umdNm, String jibun, String sggCd) {
        return aptNm != null && !aptNm.trim().isEmpty() &&
                excluUseAr != null && !excluUseAr.trim().isEmpty() &&
                floor != null && !floor.trim().isEmpty() &&
                deposit != null && !deposit.trim().isEmpty() &&
                umdNm != null && !umdNm.trim().isEmpty() &&
                jibun != null && !jibun.trim().isEmpty() &&
                sggCd != null && !sggCd.trim().isEmpty();
    }

    private String getTagValue(String tag, Element element) {
        NodeList nodeList = element.getElementsByTagName(tag);
        if (nodeList.getLength() > 0) {
            Node node = nodeList.item(0);
            if (node != null && node.getFirstChild() != null) {
                return node.getFirstChild().getNodeValue().trim();
            }
        }
        return "";
    }

    private Double parseDoubleOrDefault(String value, Double defaultValue) {
        try {
            return Double.parseDouble(value.replace(",", ""));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Integer parseIntOrDefault(String value, Integer defaultValue) {
        try {
            return Integer.parseInt(value.replace(",", ""));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Map<String, List<Property>> classifyPropertiesByLeaseType(List<Property> allProperties) {
        Map<String, List<Property>> classified = new HashMap<>();

        List<Property> charterProperties = allProperties.stream()
                .filter(p -> "전세".equals(p.getLeaseType()))
                .collect(Collectors.toList());

        List<Property> monthlyProperties = allProperties.stream()
                .filter(p -> "월세".equals(p.getLeaseType()))
                .collect(Collectors.toList());

        classified.put("전세", charterProperties);
        classified.put("월세", monthlyProperties);

        return classified;
    }

    private void publishDataCollectionCompletedEvent(List<Property> charterProperties,
                                                     List<Property> monthlyProperties) {

        DataCollectionCompletedEvent event = DataCollectionCompletedEvent.builder()
                .charterProperties(charterProperties)
                .monthlyProperties(monthlyProperties)
                .collectedAt(LocalDateTime.now())
                .totalCount(charterProperties.size() + monthlyProperties.size())
                .build();

        if (event.isValid()) {
            eventPublisher.publishEvent(event);
            log.info("데이터 수집 완료 이벤트 발행: 전세 {}건, 월세 {}건",
                    event.getCharterCount(), event.getMonthlyCount());
        } else {
            log.warn("유효하지 않은 이벤트: 데이터가 비어있습니다.");
        }
    }
}