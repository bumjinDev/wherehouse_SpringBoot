package com.aws.database.detailMapService.runner;

import com.aws.database.detailMapService.domain.ArrestRate;
import com.aws.database.detailMapService.domain.Cctv;
import com.aws.database.detailMapService.domain.PoliceOffice;
import com.aws.database.detailMapService.destination.DestinationArrestRateRepository;
import com.aws.database.detailMapService.destination.DestinationCctvRepository;
import com.aws.database.detailMapService.destination.DestinationPoliceOfficeRepository;
import com.aws.database.detailMapService.source.SourceArrestRateRepository;
import com.aws.database.detailMapService.source.SourceCctvRepository;
import com.aws.database.detailMapService.source.SourcePoliceOfficeRepository;
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

    @Override
    public void run(String... args) {
        log.info("======================================================");
        log.info("    보안 관련 데이터 이전 작업을 시작합니다.           ");
        log.info("======================================================");

        try {
            migratePoliceOffice();
            migrateCctv();
            migrateArrestRate();

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

        long destCount = destCctvRepo.count();
        if (destCount > 0) {
            log.warn("Destination DB의 CCTV에 이미 {}개의 데이터가 존재합니다. 작업을 건너뜁니다.", destCount);
            return;
        }

        destCctvRepo.saveAll(sourceData);
        log.info("✓ CCTV: {}개의 데이터를 성공적으로 이전했습니다.", sourceData.size());
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
}