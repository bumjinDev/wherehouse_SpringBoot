package com.example.jdbcapp.runner;

import com.example.jdbcapp.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 애플리케이션 시작 시 JDBC 메커니즘 검증을 자동으로 수행하는 Runner
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcVerificationRunner implements CommandLineRunner {

    private final DriverManagerService driverManagerService;
    private final HikariPoolService hikariPoolService;
    private final ConnectionPhysicalService connectionPhysicalService;
    private final TransactionBindingService transactionBindingService;

    @Override
    public void run(String... args) throws Exception {
        log.info("========================================");
        log.info("JDBC 아키텍처 검증 시작");
        log.info("========================================");

        // 1. 드라이버 등록 확인
        printSection("1. 드라이버 등록 확인 (ServiceLoader)");
        Map<String, Object> driverReg = driverManagerService.verifyDriverRegistration();
        log.info("Oracle 드라이버 발견: {}", driverReg.get("oracleDriverFound"));

        // 2. connect() 메서드 검증
        printSection("2. connect() 직접 호출 검증");
        Map<String, Object> connectResult = driverManagerService.verifyConnectMethod();
        log.info("선택된 드라이버: {}", connectResult.get("selectedDriver"));
        log.info("연결 소요 시간: {}", connectResult.get("connectionTime"));

        // 3. HikariCP 구성 확인
        printSection("3. HikariCP 풀 구성");
        Map<String, Object> poolConfig = hikariPoolService.getPoolConfiguration();
        log.info("Pool Name: {}", poolConfig.get("poolName"));
        log.info("Maximum Pool Size: {}", poolConfig.get("maximumPoolSize"));
        log.info("Minimum Idle: {}", poolConfig.get("minimumIdle"));

        // 4. 풀 상태
        printSection("4. 현재 풀 상태");
        Map<String, Object> poolStatus = hikariPoolService.getPoolStatus();
        log.info("Total: {}, Active: {}, Idle: {}",
                poolStatus.get("totalConnections"),
                poolStatus.get("activeConnections"),
                poolStatus.get("idleConnections"));

        // 5. Connection 메타데이터
        printSection("5. Connection 메타데이터");
        Map<String, Object> metadata = connectionPhysicalService.getConnectionMetadata();
        log.info("Database: {} {}", metadata.get("databaseProduct"), metadata.get("databaseVersion"));
        log.info("Driver: {} {}", metadata.get("driverName"), metadata.get("driverVersion"));

        // 6. Oracle 세션 정보
        printSection("6. Oracle 서버 측 세션 (V$SESSION)");
        Map<String, Object> sessionInfo = connectionPhysicalService.getOracleSessionInfo();
        log.info("SID: {}, SERIAL#: {}", sessionInfo.get("sid"), sessionInfo.get("serial"));
        log.info("Status: {}, Username: {}", sessionInfo.get("status"), sessionInfo.get("username"));

        // 7. 트랜잭션 바인딩
        printSection("7. @Transactional 커넥션 재사용 증명");
        Map<String, Object> txResult = transactionBindingService.executeWithTransaction();
        log.info("Session IDs: {}", txResult.get("sessionIds"));
        log.info("모든 쿼리 동일 세션: {}", txResult.get("allSameSession"));

        // 8. 풀 재사용 vs 신규 생성 비용
        printSection("8. 풀 재사용 vs 신규 생성 비용 비교");
        Map<String, Object> costComparison = hikariPoolService.compareCost();
        log.info("풀 재사용: {}", ((Map<?,?>)costComparison.get("poolReuse")).get("average"));
        log.info("신규 생성: {}", ((Map<?,?>)costComparison.get("newConnection")).get("average"));
        log.info("{}", costComparison.get("speedup"));

        log.info("========================================");
        log.info("JDBC 아키텍처 검증 완료");
        log.info("REST API 엔드포인트: http://localhost:8080/api/jdbc/verify-all");
        log.info("========================================");
    }

    private void printSection(String title) {
        log.info("");
        log.info("--- {} ---", title);
    }
}
