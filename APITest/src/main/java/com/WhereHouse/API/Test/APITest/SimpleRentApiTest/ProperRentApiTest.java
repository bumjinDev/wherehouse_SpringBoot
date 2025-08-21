package com.WhereHouse.API.Test.APITest.SimpleRentApiTest;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLEncoder;

@Component
public class ApartmentRentalParser {

    public static void main(String[] args) {
        try {
            // API 호출
            String xmlResponse = callAPI();

            // XML 파싱
            parseXMLResponse(xmlResponse);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String callAPI() throws Exception {
        String serviceKey = "YOUR_SERVICE_KEY"; // 실제 서비스키로 교체
        String lawd_cd = "11110"; // 서울 종로구
        String deal_ymd = "202407"; // 2024년 7월

        String urlString = "http://openapi.molit.go.kr/OpenAPI_ToolInstallPackage/service/rest/RTMSOBJSvc/getRTMSDataSvcAptRentNew"
                + "?serviceKey=" + URLEncoder.encode(serviceKey, "UTF-8")
                + "&pageNo=1"
                + "&numOfRows=10"
                + "&LAWD_CD=" + lawd_cd
                + "&DEAL_YMD=" + deal_ymd;

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        return response.toString();
    }

    private static void parseXMLResponse(String xmlResponse) throws Exception {
        // DocumentBuilderFactory 생성 및 설정
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        // DocumentBuilder 생성
        DocumentBuilder builder = factory.newDocumentBuilder();

        // XML 문자열을 Document로 변환
        StringReader stringReader = new StringReader(xmlResponse);
        InputSource inputSource = new InputSource(stringReader);
        Document document = builder.parse(inputSource);

        // 헤더 정보 파싱
        parseHeader(document);

        // 바디 정보 파싱
        parseBody(document);
    }

    private static void parseHeader(Document document) {
        System.out.println("=== API 응답 헤더 ===");

        NodeList headerNodes = document.getElementsByTagName("header");
        if (headerNodes.getLength() > 0) {
            Element header = (Element) headerNodes.item(0);

            String resultCode = getTextContent(header, "resultCode");
            String resultMsg = getTextContent(header, "resultMsg");

            System.out.println("응답코드: " + resultCode);
            System.out.println("응답메시지: " + resultMsg);
        }
        System.out.println();
    }

    private static void parseBody(Document document) {
        System.out.println("=== 아파트 전월세 실거래 데이터 ===");

        NodeList itemNodes = document.getElementsByTagName("item");

        if (itemNodes.getLength() == 0) {
            System.out.println("조회된 데이터가 없습니다.");
            return;
        }

        System.out.println("총 " + itemNodes.getLength() + "건의 데이터를 조회했습니다.\n");

        for (int i = 0; i < itemNodes.getLength(); i++) {
            Element item = (Element) itemNodes.item(i);

            // 기본 정보
            String sgsCd = getTextContent(item, "sggCd");
            String umdNm = getTextContent(item, "umdNm");
            String aptNm = getTextContent(item, "aptNm");
            String jibun = getTextContent(item, "jibun");
            String excluUseAr = getTextContent(item, "excluUseAr");

            // 계약 정보
            String dealYear = getTextContent(item, "dealYear");
            String dealMonth = getTextContent(item, "dealMonth");
            String dealDay = getTextContent(item, "dealDay");
            String floor = getTextContent(item, "floor");
            String buildYear = getTextContent(item, "buildYear");

            // 금액 정보
            String deposit = getTextContent(item, "deposit");
            String monthlyRent = getTextContent(item, "monthlyRent");

            // 계약 상세 정보
            String contractTerm = getTextContent(item, "contractTerm");
            String contractType = getTextContent(item, "contractType");
            String useRRRight = getTextContent(item, "useRRRight");
            String preDeposit = getTextContent(item, "preDeposit");
            String preMonthlyRent = getTextContent(item, "preMonthlyRent");

            System.out.println("=== 거래 정보 " + (i+1) + " ===");
            System.out.println("시군구코드: " + sgsCd);
            System.out.println("법정동: " + umdNm);
            System.out.println("아파트명: " + aptNm);
            System.out.println("지번: " + jibun);
            System.out.println("전용면적: " + excluUseAr + "㎡");
            System.out.println("계약년도: " + dealYear);
            System.out.println("계약월: " + dealMonth);
            System.out.println("계약일: " + dealDay);
            System.out.println("층: " + floor);
            System.out.println("건축년도: " + buildYear);
            System.out.println("보증금액: " + deposit + "만원");
            System.out.println("월세금액: " + monthlyRent + "만원");

            // 전세/월세 구분
            if (monthlyRent.equals("0") || monthlyRent.trim().isEmpty()) {
                System.out.println("거래유형: 전세");
            } else {
                System.out.println("거래유형: 월세");
            }

            // 계약 상세 정보 출력
            if (!contractTerm.isEmpty()) {
                System.out.println("계약기간: " + contractTerm + "개월");
            }
            if (!contractType.isEmpty()) {
                System.out.println("계약구분: " + contractType);
            }
            if (!useRRRight.isEmpty()) {
                System.out.println("갱신요구권사용: " + useRRRight);
            }
            if (!preDeposit.isEmpty() && !preDeposit.equals("0")) {
                System.out.println("종전계약보증금: " + preDeposit + "만원");
            }
            if (!preMonthlyRent.isEmpty() && !preMonthlyRent.equals("0")) {
                System.out.println("종전계약월세: " + preMonthlyRent + "만원");
            }

            System.out.println("--------------------");
        }
    }

    // 헬퍼 메서드: Element에서 특정 태그의 텍스트 내용을 가져옴
    private static String getTextContent(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent().trim();
        }
        return "";
    }
}