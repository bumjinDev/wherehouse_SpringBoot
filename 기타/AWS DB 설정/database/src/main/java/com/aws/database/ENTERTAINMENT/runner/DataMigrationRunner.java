package com.aws.database.ENTERTAINMENT.runner;

import com.aws.database.ENTERTAINMENT.domain.AnalysisEntertainmentStatistics;
import com.aws.database.ENTERTAINMENT.destination.DestinationEntertainmentRepository;
import com.aws.database.ENTERTAINMENT.source.SourceEntertainmentRepository;
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
public class DataMigrationRunner { // implements CommandLineRunner

    private static final Logger log = LoggerFactory.getLogger(DataMigrationRunner.class);

    private final SourceEntertainmentRepository sourceRepository;
    private final DestinationEntertainmentRepository destinationRepository;

    // @Override
    @Transactional("destinationTransactionManager") // Transaction management is based on the Destination DB
    public void run(String... args) {
        log.info("======================================================");
        log.info("           Entertainment Data Migration Started.      ");
        log.info("======================================================");

        try {
            // 1. Fetch all data from the Source DB
            List<AnalysisEntertainmentStatistics> sourceData = sourceRepository.findAll();
            if (sourceData.isEmpty()) {
                log.warn("No data found in the Source DB. Terminating the process.");
                return;
            }
            log.info("Fetched {} records from the Source DB.", sourceData.size());

            // 2. Check if data already exists in the Destination DB
            long destinationCount = destinationRepository.count();
            if (destinationCount > 0) {
                log.warn("Destination DB already contains {} records. Terminating to prevent duplicates.", destinationCount);
                return;
            }

            // 3. Create a new list of entities for the Destination DB
            // Set ID to null to allow the Destination DB's sequence to generate new IDs.
            List<AnalysisEntertainmentStatistics> newDataToSave = sourceData.stream()
                    .map(sourceEntity -> AnalysisEntertainmentStatistics.builder()
                            .businessStatusName(sourceEntity.getBusinessStatusName())
                            .phoneNumber(sourceEntity.getPhoneNumber())
                            .jibunAddress(sourceEntity.getJibunAddress())
                            .roadAddress(sourceEntity.getRoadAddress())
                            .businessName(sourceEntity.getBusinessName())
                            .businessCategory(sourceEntity.getBusinessCategory())
                            .hygieneBusinessType(sourceEntity.getHygieneBusinessType())
                            .latitude(sourceEntity.getLatitude())
                            .longitude(sourceEntity.getLongitude())
                            .build())
                    .collect(Collectors.toList());

            // 4. Batch save data to the Destination DB
            destinationRepository.saveAll(newDataToSave);
            log.info("Successfully saved {} records to the Destination DB.", newDataToSave.size());

            log.info("======================================================");
            log.info("           Data Migration Completed Successfully.     ");
            log.info("======================================================");

        } catch (Exception e) {
            log.error("A critical error occurred during data migration.", e);
        }
    }
}