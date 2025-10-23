package com.wherehouse.information.batch.processor;

import ch.hsr.geohash.GeoHash;
import com.wherehouse.information.entity.Cctv;
import com.wherehouse.information.entity.CctvGeo;
import com.wherehouse.information.entity.PoliceOffice;
import com.wherehouse.information.entity.PoliceOfficeGeo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Geohash 인덱싱 ETL 배치 프로세서
 *
 * 목적:
 * - 원본 테이블(CCTV, POLICEOFFICE)의 좌표 데이터에 Geohash ID를 추가
 * - 읽기 최적화 테이블(CCTV_GEO, POLICEOFFICE_GEO) 생성 및 B-Tree 인덱스 적용
 * - 실시간 서비스의 빠른 9-Block 그리드 검색을 위한 데이터 사전 처리
 *
 * 처리 흐름:
 * B-01: ETL 대상 테이블 정의
 * B-02: 목적 테이블 초기화 (TRUNCATE)
 * B-03: 데이터 추출 및 변환 (Geohash ID 계산)
 * B-04: 데이터 적재 (Batch Insert)
 * B-05: B-Tree 인덱스 생성
 * B-06: 처리 결과 로깅
 *
 * 실행 주기: 매일 새벽 4시 자동 실행
 * 수동 실행: executeEtlProcess() 메서드 직접 호출 가능
 */
@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class GeohashIndexingEtlProcessor {

    @PersistenceContext
    private final EntityManager entityManager;

    // Geohash 정밀도: 7자리 (약 150m x 150m 격자)
    private static final int GEOHASH_PRECISION = 7;

    // 배치 처리 단위 (메모리 효율성)
    private static final int BATCH_SIZE = 1000;

    /**
     * ETL 프로세스 메인 메서드
     * 스케줄: 매일 새벽 4시 (cron = "0 0 4 * * ?")
     */
    @Scheduled(cron = "0 0 4 * * ?")
    @Scheduled(cron = "5 46 * * * ?")
    @Transactional
    public void executeEtlProcess() {
        log.info("========================================");
        log.info("=== Geohash Indexing ETL 프로세스 시작 ===");
        log.info("========================================");
        long startTime = System.currentTimeMillis();

        try {
            // B-01: ETL 대상 테이블 정의 및 처리
            log.info("[B-01] ETL 대상 테이블: CCTV → CCTV_GEO, POLICEOFFICE → POLICEOFFICE_GEO");

            // CCTV 테이블 ETL 처리
            processCctvTable();

            // POLICEOFFICE 테이블 ETL 처리
            processPoliceOfficeTable();

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            log.info("========================================");
            log.info("=== ETL 프로세스 정상 완료 ===");
            log.info("총 소요 시간: {}ms ({}초)", duration, duration / 1000.0);
            log.info("========================================");

        } catch (Exception e) {
            log.error("========================================");
            log.error("=== ETL 프로세스 실행 중 오류 발생 ===");
            log.error("========================================", e);
            throw new RuntimeException("ETL 프로세스 실패", e);
        }
    }

    /**
     * CCTV → CCTV_GEO 테이블 ETL 처리
     *
     * 처리 단계:
     * 1. CCTV_GEO 테이블 초기화 (TRUNCATE)
     * 2. CCTV 원본 데이터 조회
     * 3. Geohash ID 계산 및 변환
     * 4. CCTV_GEO 테이블에 Batch Insert
     * 5. B-Tree 인덱스 생성
     */
    @Transactional
    public void processCctvTable() {
        log.info("--- [CCTV] ETL 처리 시작 ---");
        long startTime = System.currentTimeMillis();

        try {
            // B-02: 목적 테이블 초기화 (TRUNCATE)
            log.info("[B-02] CCTV_GEO 테이블 초기화 중...");
            truncateTable("CCTV_GEO");

            // B-03: 데이터 추출 (원본 테이블 조회)
            log.info("[B-03] CCTV 원본 데이터 추출 중...");
            List<Cctv> cctvList = entityManager
                    .createQuery("SELECT c FROM Cctv c", Cctv.class)
                    .getResultList();
            log.info("추출된 CCTV 데이터: {}건", cctvList.size());

            if (cctvList.isEmpty()) {
                log.warn("원본 CCTV 테이블에 데이터가 없습니다. ETL 처리를 건너뜁니다.");
                return;
            }

            // B-03 & B-04: 데이터 변환 및 적재
            log.info("[B-03 & B-04] Geohash ID 계산 및 CCTV_GEO 테이블 적재 중...");
            int processedCount = 0;
            List<CctvGeo> batchList = new ArrayList<>();

            for (Cctv cctv : cctvList) {
                // Geohash ID 계산 (7자리 정밀도)
                String geohashId = calculateGeohash(cctv.getLatitude(), cctv.getLongitude());

                // CctvGeo 엔티티 생성
                CctvGeo cctvGeo = CctvGeo.builder()
                        .numbers(cctv.getNumbers())
                        .address(cctv.getAddress())
                        .latitude(cctv.getLatitude())
                        .longitude(cctv.getLongitude())
                        .cameraCount(cctv.getCameraCount())
                        .geohashId(geohashId)
                        .build();

                batchList.add(cctvGeo);
                processedCount++;

                // 배치 단위로 DB에 저장 (메모리 효율성)
                if (batchList.size() >= BATCH_SIZE) {
                    batchInsert(batchList);
                    batchList.clear();
                    log.debug("CCTV_GEO {}건 저장 완료", processedCount);
                }
            }

            // 남은 데이터 저장
            if (!batchList.isEmpty()) {
                batchInsert(batchList);
            }

            log.info("CCTV_GEO 테이블 적재 완료: 총 {}건", processedCount);

            // B-05: B-Tree 인덱스 생성
            createIndexIfNotExists("CCTV_GEO", "IDX_CCTV_GEO_GEOHASH", "GEOHASH_ID");

            long endTime = System.currentTimeMillis();
            log.info("--- [CCTV] ETL 처리 완료 (소요 시간: {}ms) ---", (endTime - startTime));

        } catch (Exception e) {
            log.error("[CCTV] ETL 처리 중 오류 발생", e);
            throw new RuntimeException("CCTV ETL 처리 실패", e);
        }
    }

    /**
     * POLICEOFFICE → POLICEOFFICE_GEO 테이블 ETL 처리
     *
     * 처리 단계:
     * 1. POLICEOFFICE_GEO 테이블 초기화 (TRUNCATE)
     * 2. POLICEOFFICE 원본 데이터 조회
     * 3. Geohash ID 계산 및 변환
     * 4. POLICEOFFICE_GEO 테이블에 Batch Insert
     * 5. B-Tree 인덱스 생성
     */
    @Transactional
    public void processPoliceOfficeTable() {
        log.info("--- [POLICEOFFICE] ETL 처리 시작 ---");
        long startTime = System.currentTimeMillis();

        try {
            // B-02: 목적 테이블 초기화 (TRUNCATE)
            log.info("[B-02] POLICEOFFICE_GEO 테이블 초기화 중...");
            truncateTable("POLICEOFFICE_GEO");

            // B-03: 데이터 추출 (원본 테이블 조회)
            log.info("[B-03] POLICEOFFICE 원본 데이터 추출 중...");
            List<PoliceOffice> policeList = entityManager
                    .createQuery("SELECT p FROM PoliceOffice p", PoliceOffice.class)
                    .getResultList();

            log.info("추출된 POLICEOFFICE 데이터: {}건", policeList.size());

            if (policeList.isEmpty()) {
                log.warn("원본 POLICEOFFICE 테이블에 데이터가 없습니다. ETL 처리를 건너뜁니다.");
                return;
            }

            // B-03 & B-04: 데이터 변환 및 적재
            log.info("[B-03 & B-04] Geohash ID 계산 및 POLICEOFFICE_GEO 테이블 적재 중...");
            int processedCount = 0;

            List<PoliceOfficeGeo> batchList = new ArrayList<>();

            for (PoliceOffice police : policeList) {
                // Geohash ID 계산 (7자리 정밀도)
                String geohashId = calculateGeohash(police.getLatitude(), police.getLongitude());

                // PoliceOfficeGeo 엔티티 생성
                PoliceOfficeGeo policeGeo = PoliceOfficeGeo.builder()
                        .address(police.getAddress())
                        .latitude(police.getLatitude())
                        .longitude(police.getLongitude())
                        .geohashId(geohashId)
                        .build();

                batchList.add(policeGeo);
                processedCount++;

                // 배치 단위로 DB에 저장 (메모리 효율성)
                if (batchList.size() >= BATCH_SIZE) {
                    batchInsert(batchList);
                    batchList.clear();
                    log.debug("POLICEOFFICE_GEO {}건 저장 완료", processedCount);
                }
            }

            // 남은 데이터 저장
            if (!batchList.isEmpty()) {
                batchInsert(batchList);
            }

            log.info("POLICEOFFICE_GEO 테이블 적재 완료: 총 {}건", processedCount);

            // B-05: B-Tree 인덱스 생성
            createIndexIfNotExists("POLICEOFFICE_GEO", "IDX_POLICE_GEO_GEOHASH", "GEOHASH_ID");

            long endTime = System.currentTimeMillis();
            log.info("--- [POLICEOFFICE] ETL 처리 완료 (소요 시간: {}ms) ---", (endTime - startTime));

        } catch (Exception e) {
            log.error("[POLICEOFFICE] ETL 처리 중 오류 발생", e);
            throw new RuntimeException("POLICEOFFICE ETL 처리 실패", e);
        }
    }

    /**
     * Geohash ID 계산
     *
     * @param latitude 위도
     * @param longitude 경도
     * @return 7자리 정밀도 Geohash ID (예: "wydm7p1")
     */
    private String calculateGeohash(double latitude, double longitude) {
        GeoHash geoHash = GeoHash.withCharacterPrecision(latitude, longitude, GEOHASH_PRECISION);
        return geoHash.toBase32();
    }

    /**
     * 테이블 TRUNCATE (전체 데이터 삭제)
     *
     * @param tableName 테이블 이름
     */
    private void truncateTable(String tableName) {
        try {
            String sql = "TRUNCATE TABLE " + tableName;
            entityManager.createNativeQuery(sql).executeUpdate();
            log.info("테이블 {} TRUNCATE 완료", tableName);
        } catch (Exception e) {
            log.warn("테이블 {} TRUNCATE 실패 (테이블이 존재하지 않을 수 있음): {}", tableName, e.getMessage());
        }
    }

    /**
     * 배치 단위로 엔티티 저장
     *
     * @param entities 저장할 엔티티 리스트
     */
    private <T> void batchInsert(List<T> entities) {
        for (T entity : entities) {
            entityManager.persist(entity);
        }
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * B-Tree 인덱스 생성 (이미 존재하면 생성하지 않음)
     *
     * @param tableName 테이블 이름
     * @param indexName 인덱스 이름
     * @param columnName 인덱스를 생성할 컬럼 이름
     */
    private void createIndexIfNotExists(String tableName, String indexName, String columnName) {
        log.info("[B-05] {} 테이블에 B-Tree 인덱스 생성 중...", tableName);

        try {
            // 인덱스 존재 여부 확인 (Oracle)
            String checkSql = "SELECT COUNT(*) FROM USER_INDEXES WHERE INDEX_NAME = ?1";
            Long count = ((Number) entityManager.createNativeQuery(checkSql)
                    .setParameter(1, indexName.toUpperCase())
                    .getSingleResult()).longValue();

            if (count > 0) {
                log.info("인덱스 {}가 이미 존재합니다. 생성을 건너뜁니다.", indexName);
                return;
            }

            // B-Tree 인덱스 생성
            String createIndexSql = String.format(
                    "CREATE INDEX %s ON %s(%s)",
                    indexName, tableName, columnName
            );
            entityManager.createNativeQuery(createIndexSql).executeUpdate();
            log.info("B-Tree 인덱스 {} 생성 완료 (컬럼: {})", indexName, columnName);

        } catch (Exception e) {
            log.error("인덱스 {} 생성 중 오류 발생", indexName, e);
            // 인덱스 생성 실패는 치명적이지 않으므로 예외를 던지지 않음
        }
    }
}