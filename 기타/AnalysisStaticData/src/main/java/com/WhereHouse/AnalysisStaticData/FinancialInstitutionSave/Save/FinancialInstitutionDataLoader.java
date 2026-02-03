package com.WhereHouse.AnalysisStaticData.FinancialInstitutionSave.Save;

import com.opencsv.CSVReader;
import com.WhereHouse.AnalysisStaticData.FinancialInstitutionSave.entity.FinancialInstitution;
import com.WhereHouse.AnalysisStaticData.FinancialInstitutionSave.Repository.FinancialInstitutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FinancialInstitutionDataLoader  {  //implements CommandLineRunner

    private final FinancialInstitutionRepository financialInstitutionRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.financial-institution-data-path}")
    private String csvFilePath;

//    @Override
    @Transactional
    public void run(String... args) {

        if (financialInstitutionRepository.count() > 0) {
            log.info("금융기관 데이터 이미 존재. 로딩 스킵");
            return;
        }

        try {
            Resource resource = resourceLoader.getResource(csvFilePath);

            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();

                int savedCount = 0;

                // 헤더 4줄 스킵
                for (int i = 4; i < csvData.size(); i++) {
                    String[] row = csvData.get(i);

                    if (row.length >= 15 && "합계".equals(row[0])) {
                        try {
                            String sigungu = cleanString(row[1]);
                            String dong = cleanString(row[2]);

                            if (sigungu == null || dong == null) {
                                continue;
                            }

                            FinancialInstitution institution = FinancialInstitution.builder()
                                    .sigungu(sigungu)
                                    .dong(dong)
                                    .wooriBankCount(parseInteger(row[3]))
                                    .scBankCount(parseInteger(row[4]))
                                    .kbBankCount(parseInteger(row[5]))
                                    .shinhanBankCount(parseInteger(row[6]))
                                    .citiBankCount(parseInteger(row[7]))
                                    .hanaBankCount(parseInteger(row[8]))
                                    .ibkBankCount(parseInteger(row[9]))
                                    .nhBankCount(parseInteger(row[10]))
                                    .suhyupBankCount(parseInteger(row[11]))
                                    .kdbBankCount(parseInteger(row[12]))
                                    .eximBankCount(parseInteger(row[13]))
                                    .foreignBankCount(parseInteger(row[14]))
                                    .build();

                            if (!financialInstitutionRepository.existsBySigunguAndDong(
                                    institution.getSigungu(), institution.getDong())) {
                                financialInstitutionRepository.save(institution);
                                savedCount++;
                            }

                        } catch (Exception e) {
                            log.warn("행 {} 처리 중 오류: {}", i, e.getMessage());
                        }
                    }
                }

                log.info("금융기관 데이터 로딩 완료: {}개", savedCount);

            }
        } catch (Exception e) {
            log.error("금융기관 CSV 로딩 실패: {}", e.getMessage(), e);
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String cleanString(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}