package com.database.migration.detailMapService.runner;

import com.database.migration.detailMapService.destination.DestinationMapDataRepository;
import com.database.migration.detailMapService.domain.ArrestRate;
import com.database.migration.detailMapService.domain.Cctv;
import com.database.migration.detailMapService.domain.MapData;
import com.database.migration.detailMapService.domain.PoliceOffice;
import com.database.migration.detailMapService.destination.DestinationArrestRateRepository;
import com.database.migration.detailMapService.destination.DestinationCctvRepository;
import com.database.migration.detailMapService.destination.DestinationPoliceOfficeRepository;
import com.database.migration.detailMapService.source.SourceArrestRateRepository;
import com.database.migration.detailMapService.source.SourceCctvRepository;
import com.database.migration.detailMapService.source.SourceMapDataRepository;
import com.database.migration.detailMapService.source.SourcePoliceOfficeRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;



import java.util.List;

@Component
@Order(2)
@RequiredArgsConstructor
public class SecurityDataMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SecurityDataMigrationRunner.class);

    private final SourcePoliceOfficeRepository sourcePoliceOfficeRepo;
    private final DestinationPoliceOfficeRepository destPoliceOfficeRepo;

    private final SourceCctvRepository sourceCctvRepo;
    private final DestinationCctvRepository destCctvRepo;

    private final SourceArrestRateRepository sourceArrestRateRepo;
    private final DestinationArrestRateRepository destArrestRateRepo;

    private final SourceMapDataRepository sourceMapDataRepo;
    private final DestinationMapDataRepository destMapDataRepo;

    @Override
    public void run(String... args) {
        log.info("======================================================");
        log.info("    보안 관련 데이터 이전 작업을 시작합니다.           ");
        log.info("======================================================");

        try {
//            migratePoliceOffice();        // 완료하여 작업에서 제외
//            migrateCctv();        // 완료하여 작업에서 제외
//            migrateArrestRate();        // 완료하여 작업에서 제외
            migrateMapData();

            log.info("======================================================");
            log.info("    보안 관련 데이터 이전 작업이 완료되었습니다.     ");
            log.info("======================================================");

        } catch (Exception e) {
            log.error("보안 데이터 이전 중 심각한 오류가 발생했습니다.", e);
        }
    }

    private void migratePoliceOffice() {
        log.info("------------------------------------------------------");
        log.info("[1/3] POLICEOFFICE 테이블 마이그레이션 시작");
        log.info("------------------------------------------------------");

        List<PoliceOffice> sourceData = sourcePoliceOfficeRepo.findAll();
        if (sourceData.isEmpty()) {
            log.warn("Source DB의 POLICEOFFICE 테이블에 데이터가 없습니다.");
            return;
        }
        log.info("Source DB에서 {}개의 경찰서 데이터를 조회했습니다.", sourceData.size());

        long destCount = destPoliceOfficeRepo.count();
        if (destCount > 0) {
            log.warn("Destination DB의 POLICEOFFICE에 이미 {}개의 데이터가 존재합니다. 작업을 건너뜁니다.", destCount);
            return;
        }

        destPoliceOfficeRepo.saveAll(sourceData);
        log.info("✓ POLICEOFFICE: {}개의 데이터를 성공적으로 이전했습니다.", sourceData.size());
    }

    // SecurityDataMigrationRunner.java

    private void migrateCctv() {
        log.info("------------------------------------------------------");
        log.info("[2/3] CCTV 테이블 마이그레이션 시작");
        log.info("------------------------------------------------------");

        List<Cctv> sourceData = sourceCctvRepo.findAll();
        if (sourceData.isEmpty()) {
            log.warn("Source DB의 CCTV 테이블에 데이터가 없습니다.");
            return;
        }
        log.info("Source DB에서 {}개의 CCTV 데이터를 조회했습니다.", sourceData.size());

        // ▼▼▼▼▼▼▼▼▼▼ 이 로직 추가 ▼▼▼▼▼▼▼▼▼▼
        // 주소가 null이거나 비어있는 데이터를 필터링하여 유효한 데이터만 남김
        List<Cctv> validData = sourceData.stream()
                .filter(cctv -> cctv.getAddress() != null && !cctv.getAddress().trim().isEmpty())
                .toList();

        long invalidCount = sourceData.size() - validData.size();
        if (invalidCount > 0) {
            log.warn("주소(ADDRESS)가 NULL이거나 비어있는 {}개의 CCTV 데이터는 마이그레이션에서 제외됩니다.", invalidCount);
        }
        // ▲▲▲▲▲▲▲▲▲▲ 여기까지 추가 ▲▲▲▲▲▲▲▲▲▲

        long destCount = destCctvRepo.count();
        if (destCount > 0) {
            log.warn("Destination DB의 CCTV에 이미 {}개의 데이터가 존재합니다. 작업을 건너뜁니다.", destCount);
            return;
        }

        destCctvRepo.saveAll(validData); // 기존 sourceData 대신 필터링된 validData를 저장
        log.info("✓ CCTV: {}개의 데이터를 성공적으로 이전했습니다.", validData.size());
    }

    private void migrateArrestRate() {
        log.info("------------------------------------------------------");
        log.info("[3/3] ARRESTRATE 테이블 마이그레이션 시작");
        log.info("------------------------------------------------------");

        List<ArrestRate> sourceData = sourceArrestRateRepo.findAll();
        if (sourceData.isEmpty()) {
            log.warn("Source DB의 ARRESTRATE 테이블에 데이터가 없습니다.");
            return;
        }
        log.info("Source DB에서 {}개의 검거율 데이터를 조회했습니다.", sourceData.size());

        long destCount = destArrestRateRepo.count();
        if (destCount > 0) {
            log.warn("Destination DB의 ARRESTRATE에 이미 {}개의 데이터가 존재합니다. 작업을 건너뜁니다.", destCount);
            return;
        }

        destArrestRateRepo.saveAll(sourceData);
        log.info("✓ ARRESTRATE: {}개의 데이터를 성공적으로 이전했습니다.", sourceData.size());
    }

    private void migrateMapData() {
        log.info("------------------------------------------------------");
        log.info("[4/4] MAPDATA 테이블 마이그레이션 시작");
        log.info("------------------------------------------------------");

        List<MapData> sourceData = sourceMapDataRepo.findAll();
        if (sourceData.isEmpty()) {
            log.warn("Source DB의 MAPDATA 테이블에 데이터가 없습니다.");
            return;
        }
        log.info("Source DB에서 {}개의 지도 데이터를 조회했습니다.", sourceData.size());

        long destCount = destMapDataRepo.count();
        if (destCount > 0) {
            log.warn("Destination DB의 MAPDATA에 이미 {}개의 데이터가 존재합니다. 작업을 건너뜁니다.", destCount);
            return;
        }

        destMapDataRepo.saveAll(sourceData);
        log.info("✓ MAPDATA: {}개의 데이터를 성공적으로 이전했습니다.", sourceData.size());
    }
}