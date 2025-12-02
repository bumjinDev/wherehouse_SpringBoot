package com.wherehouse.information.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherehouse.information.model.AddressDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;


/**
 * 카카오맵 API 연동 서비스 (병렬 처리 버전)
 *
 * 주요 기능:
 * 1. 좌표 → 주소 변환 (Reverse Geocoding)
 * 2. 편의시설 검색 (15개 카테고리 병렬 처리)
 *
 * 성능 최적화:
 * - CompletableFuture.allOf()를 사용한 병렬 API 호출
 * - 15개 카테고리 동시 조회로 응답 시간 대폭 단축
 * - I/O 바운드 작업에 최적화된 스레드 풀 사용
 *
 * API 키 관리:
 * - application.yml에서 환경변수로 주입
 * - 클라이언트에 노출되지 않도록 서버에서만 사용
 *
 * 타임아웃:
 * - 연결 타임아웃: 3초
 * - 읽기 타임아웃: 5초
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoApiService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${KAKAO_API_KEY}")
    private String kakaoApiKey;

    private static final String KAKAO_API_BASE_URL = "https://dapi.kakao.com";

    /**
     * I/O 바운드 작업에 최적화된 스레드 풀
     *
     * 설계 근거:
     * - 카카오맵 API 호출은 I/O 바운드 작업 (네트워크 대기 시간이 주요 병목)
     * - 스레드가 I/O 대기 중에는 CPU를 사용하지 않으므로 많은 스레드 생성 가능
     * - 15개 카테고리 동시 호출을 위해 최소 15개 스레드 필요
     * - 여유분을 고려하여 20개 스레드로 설정 (동시 요청 처리 능력 확보)
     *
     * 스레드 풀 크기 계산:
     * - I/O 바운드: 스레드 수 = CPU 코어 수 * (1 + 대기시간/연산시간)
     * - 카카오맵 API: 대기시간(100ms) >> 연산시간(1ms) → 비율 약 100:1
     * - CPU 코어 8개 기준: 8 * (1 + 100) = 808개까지 가능하나, 실용적으로 20개 설정
     *
     * 대안:
     * - ForkJoinPool.commonPool() 사용 가능하나, 다른 작업과 격리를 위해 전용 풀 사용
     * - Executors.newCachedThreadPool() 사용 가능하나, 최대 스레드 수 제한 위해 고정 크기 선택
     */
    private static final ExecutorService KAKAO_API_EXECUTOR =
            Executors.newFixedThreadPool(20, runnable -> {
                Thread thread = new Thread(runnable);
                thread.setName("kakao-api-worker-" + thread.getId());
                thread.setDaemon(true);  // JVM 종료 시 함께 종료
                return thread;
            });

    /**
     * 좌표를 주소로 변환 (Reverse Geocoding)
     *
     * @param latitude 위도
     * @param longitude 경도
     * @return 도로명 주소와 지번 주소를 포함한 AddressDto
     *
     * API 엔드포인트:
     * GET https://dapi.kakao.com/v2/local/geo/coord2address.json?x={경도}&y={위도}
     *
     * 응답 예시:
     * {
     *   "documents": [{
     *     "road_address": {"address_name": "서울특별시 중구 세종대로 110"},
     *     "address": {"address_name": "서울특별시 중구 태평로1가 31"}
     *   }]
     * }
     */
    public AddressDto getAddress(double latitude, double longitude) {
        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("dapi.kakao.com")
                            .path("/v2/local/geo/coord2address.json")
                            .queryParam("x", longitude)
                            .queryParam("y", latitude)
                            .build())
                    .header("Authorization", "KakaoAK " + kakaoApiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode documents = root.path("documents");

            if (documents.isEmpty()) {
                log.warn("주소 변환 실패: 좌표({}, {})에 해당하는 주소 없음", latitude, longitude);
                return AddressDto.builder()
                        .roadAddress("주소 없음")
                        .jibunAddress("주소 없음")
                        .build();
            }

            JsonNode firstDoc = documents.get(0);
            String roadAddress = firstDoc.path("road_address").path("address_name").asText("주소 없음");
            String jibunAddress = firstDoc.path("address").path("address_name").asText("주소 없음");

            return AddressDto.builder()
                    .roadAddress(roadAddress)
                    .jibunAddress(jibunAddress)
                    .build();

        } catch (Exception e) {
            log.error("카카오맵 주소 변환 API 호출 실패", e);
            return AddressDto.builder()
                    .roadAddress("조회 실패")
                    .jibunAddress("조회 실패")
                    .build();
        }
    }

    /**
     * 특정 카테고리의 편의시설 검색
     *
     * @param latitude 검색 중심 위도
     * @param longitude 검색 중심 경도
     * @param categoryCode 카테고리 코드 (예: "CS2" - 편의점 등)
     * @param radius 검색 반경 (미터, 최대 20000)
     * @return 해당 카테고리의 장소 목록
     *
     * API 엔드포인트:
     * GET https://dapi.kakao.com/v2/local/search/category.json
     *     ?category_group_code={카테고리}&x={경도}&y={위도}&radius={반경}
     *
     * 응답 예시:
     * {
     *   "documents": [{
     *     "place_name": "GS25 서소문점",
     *     "x": "126.9775",
     *     "y": "37.5658",
     *     "distance": "120",
     *     "category_group_name": "편의점"
     *   }]
     * }
     */
    public List<Map<String, Object>> searchPlacesByCategory(
            double latitude, double longitude, String categoryCode, int radius) {

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("dapi.kakao.com")
                            .path("/v2/local/search/category.json")
                            .queryParam("category_group_code", categoryCode)
                            .queryParam("x", longitude)
                            .queryParam("y", latitude)
                            .queryParam("radius", radius)
                            .build())
                    .header("Authorization", "KakaoAK " + kakaoApiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode documents = root.path("documents");

            List<Map<String, Object>> places = new ArrayList<>();
            for (JsonNode doc : documents) {
                Map<String, Object> place = new HashMap<>();
                place.put("name", doc.path("place_name").asText());
                place.put("latitude", doc.path("y").asDouble());
                place.put("longitude", doc.path("x").asDouble());
                place.put("distance", doc.path("distance").asInt());
                place.put("categoryName", doc.path("category_group_name").asText());
                places.add(place);
            }

            return places;

        } catch (Exception e) {
            log.error("카카오맵 장소 검색 API 호출 실패 (카테고리: {})", categoryCode, e);
            return new ArrayList<>();
        }
    }

    /**
     * 15개 카테고리 편의시설 병렬 검색 (CompletableFuture 사용)
     *
     * 병렬 처리 설계:
     * - 각 카테고리 조회를 독립적인 CompletableFuture로 실행
     * - CompletableFuture.allOf()로 모든 작업 완료 대기
     * - 장애 격리(Fault Isolation): 일부 카테고리 실패 시에도 나머지 결과 반환
     *
     * 성능 개선:
     * - 순차 실행: 15개 * 평균 100ms = 1,500ms
     * - 병렬 실행: max(15개 API 호출 시간) ≈ 100~150ms (네트워크 병목 고려)
     * - 예상 개선율: 약 90% 단축 (1,500ms → 150ms)
     *
     * 스레드 안전성:
     * - 각 CompletableFuture는 독립적인 워커 스레드에서 실행
     * - 결과 수집 시 ConcurrentHashMap 불필요 (allOf 이후 순차 처리)
     *
     * @param latitude 검색 중심 위도
     * @param longitude 검색 중심 경도
     * @param radius 검색 반경 (미터)
     * @return 카테고리별 장소 목록 맵 (key: 카테고리 코드, value: 장소 목록)
     *
     * 카테고리 목록 (amenity.js 기준):
     * - SW8: 지하철역
     * - CS2: 편의점
     * - FD6: 음식점
     * - CE7: 카페
     * - MT1: 대형마트
     * - BK9: 은행
     * - PO3: 공공기관
     * - CT1: 문화시설
     * - HP8: 병원
     * - PM9: 약국
     * - PK6: 주차장
     * - OL7: 주유소
     * - SC4: 학교
     * - AC5: 학원
     * - AT4: 관광명소
     */
    public Map<String, List<Map<String, Object>>> searchAllAmenities(
            double latitude, double longitude, int radius) {

        log.info("[KakaoApiService] 15개 카테고리 병렬 검색 시작 - 좌표: ({}, {}), 반경: {}m",
                latitude, longitude, radius);
        // 15개 카테고리 코드 배열
        String[] categories = {"SW8", "CS2", "FD6", "CE7", "MT1", "BK9", "PO3",
                "CT1", "HP8", "PM9", "PK6", "OL7", "SC4", "AC5", "AT4"};
        // 카테고리별 CompletableFuture 생성 (비동기 작업 시작), Map.Entry<카테고리코드, CompletableFuture<장소목록>>
        Map<String, CompletableFuture<List<Map<String, Object>>>> futureMap = new HashMap<>();

        for (String category : categories) {
            // supplyAsync: 별도 스레드에서 searchPlacesByCategory 실행
            // KAKAO_API_EXECUTOR: I/O 바운드 작업에 최적화된 전용 스레드 풀 사용
            CompletableFuture<List<Map<String, Object>>> future =
                    CompletableFuture.supplyAsync(() -> {
                                log.debug("[KakaoApiService] 카테고리 {} 조회 시작 - 스레드: {}",
                                        category, Thread.currentThread().getName());

                                List<Map<String, Object>> result =
                                        searchPlacesByCategory(latitude, longitude, category, radius);

                                log.debug("[KakaoApiService] 카테고리 {} 조회 완료 - 결과: {}건, 스레드: {}",
                                        category, result.size(), Thread.currentThread().getName());

                                return result;
                            }, KAKAO_API_EXECUTOR)
                            .exceptionally(ex -> {
                                // 장애 격리: 특정 카테고리 실패 시 빈 리스트 반환 (전체 프로세스 중단 방지)
                                log.error("[KakaoApiService] 카테고리 {} 조회 실패 - 빈 리스트 반환", category, ex);
                                return new ArrayList<>();
                            });
            futureMap.put(category, future);
        }
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futureMap.values().toArray(new CompletableFuture[0])
        );
        allFutures.join();

        log.info("[KakaoApiService] 15개 카테고리 병렬 검색 완료");

        Map<String, List<Map<String, Object>>> results = futureMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,           // 카테고리 코드
                        entry -> entry.getValue().join()  // CompletableFuture → List 변환
                ));

        // 결과 통계 로깅
        int totalPlaces = results.values().stream()
                .mapToInt(List::size)
                .sum();

        log.info("[KakaoApiService] 검색 결과 - 총 카테고리: {}개, 총 장소: {}건",
                results.size(), totalPlaces);

        return results;
    }

    /**
     * 서비스 종료 시 스레드 풀 정리
     *
     * @apiNote 이 메서드는 Spring 컨테이너가 Bean 소멸 시 자동 호출
     */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        log.info("[KakaoApiService] 스레드 풀 종료 시작");

        KAKAO_API_EXECUTOR.shutdown();

        try {
            if (!KAKAO_API_EXECUTOR.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                log.warn("[KakaoApiService] 스레드 풀 정상 종료 실패 - 강제 종료 시도");
                KAKAO_API_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("[KakaoApiService] 스레드 풀 종료 대기 중 인터럽트 발생", e);
            KAKAO_API_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("[KakaoApiService] 스레드 풀 종료 완료");
    }
}