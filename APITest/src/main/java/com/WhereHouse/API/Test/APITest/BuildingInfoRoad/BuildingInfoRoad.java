package com.WhereHouse.API.Test.APITest.SimpleRentApiTest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 국토교통부 실거래가 정보 조회 API 테스트 클래스
 * 아파트 전월세 실거래 자료를 조회하고 XML 응답을 파싱하여 출력합니다.
 *
 * @author WhereHouse Development Team
 * @version 1.0
 * @since 2025-08-21
 */
@Component
public class ProperRentApiTest implements CommandLineRunner {

    @Value("${molit.rent-api.service-key}")
    private String serviceKey;

    @Value("${molit.rent-api.base-url}")
    private String baseUrl;

    /** API 엔드포인트 */
    private static final String API_ENDPOINT = "/getRTMSDataSvcAptRent";

    /** 조회할 최대 건수 */
    private static final String NUM_OF_ROWS = "100";

    /** 조회할 페이지 번호 */
    private static final String PAGE_NO = "1";

    @Override
    public void run(String... args) throws Exception {
        testRentAPIWithProperParsing();
    }

    /**
     * 전월세 실거래가 API 테스트 메인 메서드
     * DOM Parser를 사용하여 XML 응답을 파싱하고 결과를 출력합니다.
     */
    public void testRentAPIWithProperParsing() {
        try {
            // 강남구 지역코드 (11680)
            String lawdCd = "11680";
            // 현재 년월 (YYYYMM 형식)
            String dealYmd = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));

            System.out.println("🚀 전월세 실거래가 API 테스트 시작");
            System.out.println("📍 조회 지역: " + lawdCd + " (강남구)");
            System.out.println("📅 조회 년월: " + dealYmd);
            System.out.println("═══════════════════════════════════════");

            // API 호출 및 XML 응답 받기
            String xmlResponse = callRentAPI(lawdCd, dealYmd);

            if (xmlResponse != null && !xmlResponse.isEmpty()) {
                System.out.println("✅ API 응답 수신 완료");
                // XML 응답을 DOM Parser로 파싱
                parseXmlWithDOM(xmlResponse);
            } else {
                System.out.println("❌ API 응답이 비어있습니다.");
            }

        } catch (Exception e) {
            System.err.println("❌ API 테스트 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * DOM Parser를 사용하여 XML 응답을 파싱합니다.
     *
     * @param xmlResponse API로부터 받은 XML 응답 문자열
     */
    private void parseXmlWithDOM(String xmlResponse) {
        try {
            System.out.println("\n🔧 DOM Parser로 XML 파싱 시작...");

            // DocumentBuilderFactory 설정
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            // 문자열을 XML Document로 변환
            Document document = builder.parse(new InputSource(new StringReader(xmlResponse)));
            document.getDocumentElement().normalize();

            System.out.println("📄 루트 엘리먼트: " + document.getDocumentElement().getNodeName());

            // 헤더 정보 파싱 (API 호출 결과 상태 확인)
            parseHeader(document);

            // 바디 정보 파싱 (페이징 정보 등)
            parseBody(document);

            // 실제 전월세 데이터 아이템들 파싱
            parseRentItems(document);

        } catch (Exception e) {
            System.err.println("❌ XML 파싱 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * API 응답 헤더 정보를 파싱하여 호출 성공/실패 여부를 확인합니다.
     *
     * @param document 파싱된 XML Document
     */
    private void parseHeader(Document document) {
        try {
            System.out.println("\n📋 API 응답 헤더 정보:");

            NodeList headerNodes = document.getElementsByTagName("header");
            if (headerNodes.getLength() > 0) {
                Element header = (Element) headerNodes.item(0);

                String resultCode = getElementValue(header, "resultCode");
                String resultMsg = getElementValue(header, "resultMsg");

                System.out.println("   ✓ 응답코드: " + (resultCode != null ? resultCode : "정보없음"));
                System.out.println("   📝 응답메시지: " + (resultMsg != null ? resultMsg : "정보없음"));

                // 성공 여부 판단 (정상: 00)
                if ("00".equals(resultCode)) {
                    System.out.println("   🎉 API 호출 성공!");
                } else {
                    System.out.println("   ⚠️ API 호출 실패 - 코드: " + resultCode + ", 메시지: " + resultMsg);
                }
            } else {
                System.out.println("   ⚠️ 헤더 정보를 찾을 수 없습니다.");
            }
        } catch (Exception e) {
            System.err.println("   ❌ 헤더 파싱 실패: " + e.getMessage());
        }
    }

    /**
     * API 응답 바디 정보를 파싱하여 페이징 정보를 출력합니다.
     *
     * @param document 파싱된 XML Document
     */
    private void parseBody(Document document) {
        try {
            System.out.println("\n📊 페이징 정보:");

            NodeList bodyNodes = document.getElementsByTagName("body");
            if (bodyNodes.getLength() > 0) {
                Element body = (Element) bodyNodes.item(0);

                String totalCount = getElementValue(body, "totalCount");
                String numOfRows = getElementValue(body, "numOfRows");
                String pageNo = getElementValue(body, "pageNo");

                System.out.println("   📈 전체 건수: " + (totalCount != null ? totalCount + "건" : "정보없음"));
                System.out.println("   📄 페이지당 건수: " + (numOfRows != null ? numOfRows + "건" : "정보없음"));
                System.out.println("   📖 현재 페이지: " + (pageNo != null ? pageNo : "정보없음"));
            } else {
                System.out.println("   ⚠️ 바디 정보를 찾을 수 없습니다.");
            }
        } catch (Exception e) {
            System.err.println("   ❌ 바디 파싱 실패: " + e.getMessage());
        }
    }

    /**
     * 실제 전월세 실거래 데이터 아이템들을 파싱하여 출력합니다.
     *
     * @param document 파싱된 XML Document
     */
    private void parseRentItems(Document document) {
        try {
            System.out.println("\n🏠 전월세 실거래 데이터:");

            // 모든 <item> 태그 조회
            NodeList itemNodes = document.getElementsByTagName("item");
            int itemCount = itemNodes.getLength();

            System.out.println("   🔍 발견된 아이템 수: " + itemCount + "개");

            if (itemCount == 0) {
                System.out.println("   ⚠️ 조회된 데이터가 없습니다.");
                System.out.println("   💡 다른 지역코드나 날짜로 시도해보세요.");
                return;
            }

            // 실거래 데이터 리스트
            List<Map<String, String>> rentDataList = new ArrayList<>();

            // 최대 10개까지만 출력 (너무 많은 데이터 방지)
            int displayCount = Math.min(itemCount, 10);
            // int displayCount = itemCount;

            for (int i = 0; i < displayCount; i++) {
                Element item = (Element) itemNodes.item(i);
                Map<String, String> rentData = parseIndividualRentItem(item);
                rentDataList.add(rentData);

                System.out.println("\n   🏢 " + (i + 1) + "번째 매물 정보:");
                displayRentData(rentData);
            }

            if (itemCount > displayCount) {
                System.out.println("\n   💡 총 " + itemCount + "건 중 " + displayCount + "건만 표시했습니다.");
                System.out.println("   📝 더 많은 데이터를 보려면 페이징을 구현하세요.");
            }

        } catch (Exception e) {
            System.err.println("   ❌ 아이템 파싱 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 개별 전월세 아이템의 상세 정보를 파싱합니다.
     * 실제 API 응답 필드명(영어)을 기준으로 데이터를 추출합니다.
     *
     * @param item 개별 아이템 Element
     * @return 파싱된 데이터가 담긴 Map
     */
    private Map<String, String> parseIndividualRentItem(Element item) {
        Map<String, String> data = new HashMap<>();

        // API 응답의 실제 필드명들 (영어)
        // 국토교통부 API 문서에 명시된 정확한 필드명 사용
        String [] fieldNames = {
                "aptNm",        // 아파트명
                "excluUseAr",   // 전용면적
                "floor",        // 층
                "buildYear",    // 건축연도
                "dealYear",     // 계약년도
                "dealMonth",    // 계약월
                "dealDay",      // 계약일
                "deposit",      // 보증금
                "monthlyRent",  // 월세금
                "umdNm",        // 법정동
                "jibun",        // 지번
                "sggCd",        // 시군구코드
                "rgstDate"      // 등록일자
        };

        // 각 필드별 데이터 추출
        for (String fieldName : fieldNames) {
            String value = getElementValue(item, fieldName);
            data.put(fieldName, value != null ? value.trim() : "정보없음");
        }

        return data;
    }

    /**
     * 파싱된 전월세 데이터를 사용자 친화적인 형태로 출력합니다.
     *
     * @param data 파싱된 전월세 데이터 Map
     */
    private void displayRentData(Map<String, String> data) {
        try {
            System.out.println("      🏢 아파트명: " + data.get("aptNm"));
            System.out.println("      📐 전용면적: " + data.get("excluUseAr") + "㎡");
            System.out.println("      🏗️ 건축연도: " + data.get("buildYear") + "년");

            // 계약일자 조합
            String dealDate = String.format("%s년 %s월 %s일",
                    data.get("dealYear"),
                    data.get("dealMonth"),
                    data.get("dealDay"));
            System.out.println("      📅 계약일자: " + dealDate);

            // 보증금과 월세 정보 - 수정된 부분
            String deposit = data.get("deposit");
            String monthlyRent = data.get("monthlyRent");

            // 월세 여부 판단 및 표시
            if (monthlyRent != null && !monthlyRent.equals("0") && !monthlyRent.equals("정보없음") && !monthlyRent.trim().isEmpty()) {
                // 월세인 경우
                System.out.println("      💰 보증금: " + deposit + "만원");
                System.out.println("      💰 월세금: " + monthlyRent + "만원");
                System.out.println("      🏷️ 거래유형: 월세");
            } else {
                // 전세인 경우
                System.out.println("      💰 전세금: " + deposit + "만원");
                System.out.println("      🏷️ 거래유형: 전세");
            }

            // 위치 정보
            System.out.println("      📍 위치: " + data.get("umdNm") + " " + data.get("jibun"));
            System.out.println("      🏠 층수: " + data.get("floor") + "층");

            if (!data.get("rgstDate").equals("정보없음")) {
                System.out.println("      📝 등록일: " + data.get("rgstDate"));
            }

        } catch (Exception e) {
            System.err.println("      ❌ 데이터 출력 중 오류: " + e.getMessage());
        }
    }

    /**
     * XML Element에서 지정된 태그명의 텍스트 값을 안전하게 추출합니다.
     *
     * @param parent 부모 Element
     * @param tagName 추출할 태그명
     * @return 태그의 텍스트 값, 없으면 null
     */
    private String getElementValue(Element parent, String tagName) {
        try {
            NodeList nodeList = parent.getElementsByTagName(tagName);
            if (nodeList.getLength() > 0) {
                Node node = nodeList.item(0);
                if (node != null) {
                    Node firstChild = node.getFirstChild();
                    if (firstChild != null) {
                        return firstChild.getNodeValue();
                    }
                }
            }
        } catch (Exception e) {
            // 조용히 무시하고 null 반환 (로그는 남기지 않음)
        }
        return null;
    }

    /**
     * 국토교통부 전월세 실거래가 API를 호출합니다.
     *
     * @param lawdCd 법정동 코드 (5자리)
     * @param dealYmd 조회하려는 년월 (YYYYMM 형식)
     * @return API 응답 XML 문자열, 실패시 null
     */
    private String callRentAPI(String lawdCd, String dealYmd) {
        HttpURLConnection conn = null;
        BufferedReader reader = null;

        try {
            // URL 파라미터 구성
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
                    .append("=").append(URLEncoder.encode(PAGE_NO, "UTF-8"));

            String apiUrl = urlBuilder.toString();
            System.out.println("🌐 API URL: " + apiUrl.replace(serviceKey, "***SERVICE_KEY***"));

            // HTTP 연결 설정
            URL url = new URL(apiUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-type", "application/xml");
            conn.setRequestProperty("Accept", "application/xml");
            conn.setConnectTimeout(30000);  // 연결 타임아웃: 30초
            conn.setReadTimeout(30000);     // 읽기 타임아웃: 30초

            int responseCode = conn.getResponseCode();
            System.out.println("📡 HTTP 응답 코드: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // 성공적인 응답 읽기
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }

                String xmlResponse = response.toString();
                System.out.println("📦 응답 데이터 크기: " + xmlResponse.length() + " bytes");

                return xmlResponse;

            } else {
                // 에러 응답 처리
                System.err.println("❌ API 호출 실패 - HTTP 코드: " + responseCode);

                // 에러 스트림이 있으면 읽어서 출력
                if (conn.getErrorStream() != null) {
                    BufferedReader errorReader = new BufferedReader(
                            new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                    StringBuilder errorResponse = new StringBuilder();
                    String errorLine;

                    while ((errorLine = errorReader.readLine()) != null) {
                        errorResponse.append(errorLine).append("\n");
                    }
                    errorReader.close();

                    System.err.println("💬 에러 응답: " + errorResponse.toString());
                }

                return null;
            }

        } catch (Exception e) {
            System.err.println("❌ API 호출 중 예외 발생: " + e.getMessage());
            e.printStackTrace();
            return null;

        } finally {
            // 리소스 정리
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    System.err.println("⚠️ Reader 닫기 실패: " + e.getMessage());
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}