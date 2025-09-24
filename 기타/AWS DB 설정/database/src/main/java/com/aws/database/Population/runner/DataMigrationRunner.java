package com.aws.database.Population.runner;

import com.aws.database.Population.domain.AnalysisPopulationDensity;
import com.aws.database.Population.destination.DestinationPopulationRepository;
import com.aws.database.Population.source.SourcePopulationRepository;
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
public class DataMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationRunner.class);

    private final SourcePopulationRepository sourceRepository;
    private final DestinationPopulationRepository destinationRepository;

    @Override
    @Transactional("destinationTransactionManager") // 트랜잭션은 Destination DB 기준으로 관리
    public void run(String... args) {
        log.info("======================================================");
        log.info("           데이터 이전 작업을 시작합니다.             ");
        log.info("======================================================");

        try {
            // 1. Source DB에서 모든 데이터 조회
            List<AnalysisPopulationDensity> sourceData = sourceRepository.findAll();
            if (sourceData.isEmpty()) {
                log.warn("Source DB에 데이터가 없습니다. 작업을 종료합니다.");
                return;
            }
            log.info("Source DB에서 {}개의 데이터를 조회했습니다.", sourceData.size());

            // 2. Destination DB에 데이터가 이미 있는지 확인
            long destinationCount = destinationRepository.count();
            if (destinationCount > 0) {
                log.warn("Destination DB에 이미 {}개의 데이터가 존재합니다. 중복 저장을 방지하기 위해 작업을 종료합니다.", destinationCount);
                return;
            }

            // 3. Destination DB에 저장할 새로운 Entity 리스트 생성
            // ID를 null로 설정하여 Destination DB의 시퀀스를 통해 새로운 ID가 생성되도록 합니다.
            List<AnalysisPopulationDensity> newDataToSave = sourceData.stream()
                    .map(sourceEntity -> AnalysisPopulationDensity.builder()
                            .districtName(sourceEntity.getDistrictName())
                            .year(sourceEntity.getYear())
                            .populationCount(sourceEntity.getPopulationCount())
                            .areaSize(sourceEntity.getAreaSize())
                            .populationDensity(sourceEntity.getPopulationDensity())
                            .build())
                    .collect(Collectors.toList());

            // 4. Destination DB에 데이터 일괄 저장
            destinationRepository.saveAll(newDataToSave);
            log.info("Destination DB에 {}개의 데이터를 성공적으로 저장했습니다.", newDataToSave.size());

            log.info("======================================================");
            log.info("           데이터 이전 작업이 완료되었습니다.           ");
            log.info("======================================================");

        } catch (Exception e) {
            log.error("데이터 이전 중 심각한 오류가 발생했습니다.", e);
        }
    }
}