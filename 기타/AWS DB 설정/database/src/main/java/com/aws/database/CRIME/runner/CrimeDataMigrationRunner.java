package com.aws.database.CRIME.runner;

import com.aws.database.CRIME.domain.AnalysisCrimeStatistics;
import com.aws.database.CRIME.destination.DestinationCrimeRepository;
import com.aws.database.CRIME.source.SourceCrimeRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CrimeDataMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CrimeDataMigrationRunner.class);

    private final SourceCrimeRepository sourceCrimeRepo;
    private final DestinationCrimeRepository destinationCrimeRepo;

    @Override
    @Transactional("destinationTransactionManager")
    public void run(String... args) {
        log.info("======================================================");
        log.info("           범죄 데이터 이전 작업을 시작합니다.          ");
        log.info("======================================================");

        try {
            List<AnalysisCrimeStatistics> sourceData = sourceCrimeRepo.findAll();
            if (sourceData.isEmpty()) {
                log.warn("[범죄 데이터] Source DB에 데이터가 없습니다. 작업을 종료합니다.");
                return;
            }
            log.info("[범죄 데이터] Source DB에서 {}개의 데이터를 조회했습니다.", sourceData.size());

            if (destinationCrimeRepo.count() > 0) {
                log.warn("[범죄 데이터] Destination DB에 이미 데이터가 존재합니다. 작업을 종료합니다.");
                return;
            }

            List<AnalysisCrimeStatistics> newDataToSave = sourceData.stream()
                    .map(s -> AnalysisCrimeStatistics.builder()
                            .districtName(s.getDistrictName())
                            .year(s.getYear())
                            .totalOccurrence(s.getTotalOccurrence())
                            // ... (모든 필드 복사) ...
                            .violenceArrest(s.getViolenceArrest())
                            .build())
                    .collect(Collectors.toList());

            destinationCrimeRepo.saveAll(newDataToSave);
            log.info("[범죄 데이터] Destination DB에 {}개의 데이터를 성공적으로 저장했습니다.", newDataToSave.size());

            log.info("======================================================");
            log.info("           범죄 데이터 이전 작업이 완료되었습니다.        ");
            log.info("======================================================");

        } catch (Exception e) {
            log.error("[범죄 데이터] 이전 중 오류가 발생했습니다.", e);
        }
    }
}