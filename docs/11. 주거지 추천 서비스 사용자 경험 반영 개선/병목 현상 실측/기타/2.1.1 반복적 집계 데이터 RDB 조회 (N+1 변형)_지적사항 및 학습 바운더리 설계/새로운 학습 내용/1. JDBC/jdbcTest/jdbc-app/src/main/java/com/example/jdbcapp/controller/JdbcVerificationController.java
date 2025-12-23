package com.example.jdbcapp.controller;

import com.example.jdbcapp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JDBC 아키텍처 검증 API
 * 
 * 각 엔드포인트는 문서에 기술된 JDBC 메커니즘을 실제로 검증하고 결과를 반환한다.
 */
@RestController
@RequestMapping("/api/jdbc")
@RequiredArgsConstructor
public class JdbcVerificationController {

    private final DriverManagerService driverManagerService;
    private final HikariPoolService hikariPoolService;
    private final ConnectionPhysicalService connectionPhysicalService;
    private final TransactionBindingService transactionBindingService;

    // ==================== DriverManager 메커니즘 ====================

    /**
     * GET /api/jdbc/driver/registration
     * ServiceLoader에 의한 드라이버 자동 등록 확인
     */
    @GetMapping("/driver/registration")
    public ResponseEntity<Map<String, Object>> verifyDriverRegistration() {
        return ResponseEntity.ok(driverManagerService.verifyDriverRegistration());
    }

    /**
     * GET /api/jdbc/driver/connect
     * 드라이버의 connect() 직접 호출 방식 검증
     */
    @GetMapping("/driver/connect")
    public ResponseEntity<Map<String, Object>> verifyConnectMethod() throws SQLException {
        return ResponseEntity.ok(driverManagerService.verifyConnectMethod());
    }

    /**
     * GET /api/jdbc/driver/simulate
     * DriverManager.getConnection() 내부 동작 시뮬레이션
     */
    @GetMapping("/driver/simulate")
    public ResponseEntity<Map<String, Object>> simulateGetConnection() {
        return ResponseEntity.ok(driverManagerService.simulateGetConnection());
    }

    /**
     * GET /api/jdbc/driver/cost?iterations=5
     * 물리적 커넥션 수립 비용 측정
     */
    @GetMapping("/driver/cost")
    public ResponseEntity<Map<String, Object>> measureConnectionCost(
            @RequestParam(defaultValue = "5") int iterations) throws SQLException {
        return ResponseEntity.ok(driverManagerService.measureConnectionCost(iterations));
    }

    // ==================== HikariCP 풀링 메커니즘 ====================

    /**
     * GET /api/jdbc/pool/config
     * HikariDataSource 구성 정보 조회
     */
    @GetMapping("/pool/config")
    public ResponseEntity<Map<String, Object>> getPoolConfiguration() {
        return ResponseEntity.ok(hikariPoolService.getPoolConfiguration());
    }

    /**
     * GET /api/jdbc/pool/status
     * 현재 풀 상태 조회
     */
    @GetMapping("/pool/status")
    public ResponseEntity<Map<String, Object>> getPoolStatus() {
        return ResponseEntity.ok(hikariPoolService.getPoolStatus());
    }

    /**
     * GET /api/jdbc/pool/borrow-return
     * 커넥션 대여/반납 시나리오 시연
     */
    @GetMapping("/pool/borrow-return")
    public ResponseEntity<Map<String, Object>> demonstrateBorrowReturn() throws SQLException {
        return ResponseEntity.ok(hikariPoolService.demonstrateBorrowReturn());
    }

    /**
     * GET /api/jdbc/pool/compare-cost
     * 풀 재사용 vs 신규 생성 비용 비교
     */
    @GetMapping("/pool/compare-cost")
    public ResponseEntity<Map<String, Object>> compareCost() throws SQLException {
        return ResponseEntity.ok(hikariPoolService.compareCost());
    }

    /**
     * GET /api/jdbc/pool/exhaustion
     * 풀 고갈 시나리오 시뮬레이션
     */
    @GetMapping("/pool/exhaustion")
    public ResponseEntity<Map<String, Object>> simulateExhaustion() throws SQLException {
        return ResponseEntity.ok(hikariPoolService.simulateExhaustion());
    }

    // ==================== Connection 물리적 실체 ====================

    /**
     * GET /api/jdbc/connection/metadata
     * DatabaseMetaData를 통한 연결 정보 확인
     */
    @GetMapping("/connection/metadata")
    public ResponseEntity<Map<String, Object>> getConnectionMetadata() throws SQLException {
        return ResponseEntity.ok(connectionPhysicalService.getConnectionMetadata());
    }

    /**
     * GET /api/jdbc/connection/session
     * Oracle V$SESSION을 통한 서버 측 세션 정보
     */
    @GetMapping("/connection/session")
    public ResponseEntity<Map<String, Object>> getOracleSessionInfo() throws SQLException {
        return ResponseEntity.ok(connectionPhysicalService.getOracleSessionInfo());
    }

    /**
     * GET /api/jdbc/connection/pga
     * PGA 메모리 할당 정보 (DBA 권한 필요)
     */
    @GetMapping("/connection/pga")
    public ResponseEntity<Map<String, Object>> getPgaMemoryInfo() throws SQLException {
        return ResponseEntity.ok(connectionPhysicalService.getPgaMemoryInfo());
    }

    /**
     * GET /api/jdbc/connection/independent
     * 다중 커넥션의 독립적 세션 증명
     */
    @GetMapping("/connection/independent")
    public ResponseEntity<Map<String, Object>> proveIndependentSessions() throws SQLException {
        return ResponseEntity.ok(connectionPhysicalService.proveIndependentSessions());
    }

    /**
     * GET /api/jdbc/connection/holding-time
     * Connection Holding Time 시연
     */
    @GetMapping("/connection/holding-time")
    public ResponseEntity<Map<String, Object>> demonstrateHoldingTime() throws SQLException {
        return ResponseEntity.ok(connectionPhysicalService.demonstrateHoldingTime());
    }

    // ==================== 트랜잭션 바인딩 메커니즘 ====================

    /**
     * GET /api/jdbc/transaction/without
     * 트랜잭션 없이 실행
     */
    @GetMapping("/transaction/without")
    public ResponseEntity<Map<String, Object>> executeWithoutTransaction() {
        return ResponseEntity.ok(transactionBindingService.executeWithoutTransaction());
    }

    /**
     * GET /api/jdbc/transaction/with
     * @Transactional 내에서 실행 (커넥션 재사용 증명)
     */
    @GetMapping("/transaction/with")
    public ResponseEntity<Map<String, Object>> executeWithTransaction() {
        return ResponseEntity.ok(transactionBindingService.executeWithTransaction());
    }

    /**
     * GET /api/jdbc/transaction/holding-analysis
     * Holding Time과 풀 회전율 분석
     */
    @GetMapping("/transaction/holding-analysis")
    public ResponseEntity<Map<String, Object>> analyzeHoldingTime() 
            throws SQLException, InterruptedException {
        return ResponseEntity.ok(transactionBindingService.analyzeHoldingTime());
    }

    // ==================== 전체 검증 ====================

    /**
     * GET /api/jdbc/verify-all
     * 모든 검증 한번에 실행
     */
    @GetMapping("/verify-all")
    public ResponseEntity<Map<String, Object>> verifyAll() throws SQLException, InterruptedException {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("1_driverRegistration", driverManagerService.verifyDriverRegistration());
        result.put("2_connectMethod", driverManagerService.verifyConnectMethod());
        result.put("3_poolConfig", hikariPoolService.getPoolConfiguration());
        result.put("4_poolStatus", hikariPoolService.getPoolStatus());
        result.put("5_connectionMetadata", connectionPhysicalService.getConnectionMetadata());
        result.put("6_oracleSession", connectionPhysicalService.getOracleSessionInfo());
        result.put("7_transactionBinding", transactionBindingService.executeWithTransaction());

        return ResponseEntity.ok(result);
    }
}
