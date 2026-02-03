package com.example.jdbcapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * Spring 트랜잭션의 ThreadLocal 바인딩 메커니즘을 검증하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionBindingService {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 트랜잭션 없이 JdbcTemplate 사용 시 동작
     */
    public Map<String, Object> executeWithoutTransaction() {
        Map<String, Object> result = new LinkedHashMap<>();

        boolean isSyncActive = TransactionSynchronizationManager.isSynchronizationActive();
        boolean isActualTxActive = TransactionSynchronizationManager.isActualTransactionActive();

        result.put("synchronizationActive", isSyncActive);
        result.put("actualTransactionActive", isActualTxActive);

        // 여러 쿼리 실행
        List<Integer> sids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Integer sid = jdbcTemplate.queryForObject(
                    "SELECT SYS_CONTEXT('USERENV', 'SID') FROM DUAL", Integer.class);
            sids.add(sid);
        }

        result.put("sessionIds", sids);
        result.put("note", "트랜잭션 없이 JdbcTemplate 사용 시 각 쿼리마다 커넥션 획득/반납 반복 (풀 상태에 따라 동일하거나 다를 수 있음)");

        log.info("트랜잭션 없이 실행 완료");
        return result;
    }

    /**
     * @Transactional 내에서 커넥션 재사용 증명
     */
    @Transactional(readOnly = true)
    public Map<String, Object> executeWithTransaction() {
        Map<String, Object> result = new LinkedHashMap<>();

        boolean isSyncActive = TransactionSynchronizationManager.isSynchronizationActive();
        boolean isActualTxActive = TransactionSynchronizationManager.isActualTransactionActive();

        result.put("synchronizationActive", isSyncActive);
        result.put("actualTransactionActive", isActualTxActive);

        // 동일 트랜잭션 내 여러 쿼리
        List<Integer> sids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Integer sid = jdbcTemplate.queryForObject(
                    "SELECT SYS_CONTEXT('USERENV', 'SID') FROM DUAL", Integer.class);
            sids.add(sid);
        }

        result.put("sessionIds", sids);

        // 모든 SID가 동일한지 확인
        boolean allSame = sids.stream().distinct().count() == 1;
        result.put("allSameSession", allSame);
        result.put("proof", "ThreadLocal에 바인딩된 커넥션 재사용 증명");

        result.put("mechanism", Map.of(
                "step1", "@Transactional 진입 시 TransactionInterceptor 동작",
                "step2", "PlatformTransactionManager.getTransaction() 호출",
                "step3", "DataSourceTransactionManager가 커넥션 획득",
                "step4", "TransactionSynchronizationManager.bindResource()로 ThreadLocal에 바인딩",
                "step5", "이후 모든 DB 접근은 바인딩된 커넥션 재사용",
                "step6", "메서드 종료 시 commit/rollback 후 unbindResource()"
        ));

        log.info("@Transactional 내 실행 완료 - 모든 쿼리 동일 세션: {}", allSame);
        return result;
    }

    /**
     * Connection Holding Time과 풀 회전율 분석
     */
    public Map<String, Object> analyzeHoldingTime() throws SQLException, InterruptedException {
        Map<String, Object> result = new LinkedHashMap<>();

        // 시나리오 1: 빠른 트랜잭션
        long fastStart = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            jdbcTemplate.queryForList("SELECT * FROM DUAL");
        }
        long fastHoldingTime = System.currentTimeMillis() - fastStart;

        // 시나리오 2: 느린 트랜잭션 (N+1 시뮬레이션)
        long slowStart = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            for (int i = 0; i < 10; i++) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT SYSDATE FROM DUAL")) {
                    rs.next();
                }
                Thread.sleep(10); // 비즈니스 로직 시뮬레이션
            }
        }
        long slowHoldingTime = System.currentTimeMillis() - slowStart;

        result.put("fastTransaction", Map.of(
                "holdingTime", fastHoldingTime + " ms",
                "description", "단일 배치 쿼리"
        ));
        result.put("slowTransaction", Map.of(
                "holdingTime", slowHoldingTime + " ms",
                "description", "N+1 패턴 (루프 내 개별 쿼리 + 비즈니스 로직)"
        ));
        result.put("ratio", String.format("%.1f배 차이", (double) slowHoldingTime / fastHoldingTime));

        // 풀 회전율 계산
        int poolSize = 5;
        long timeWindow = 1000; // 1초

        double fastTurnover = (double) timeWindow / fastHoldingTime * poolSize;
        double slowTurnover = (double) timeWindow / slowHoldingTime * poolSize;

        result.put("turnoverRate", Map.of(
                "poolSize", poolSize,
                "fastDesign", String.format("초당 %.1f 요청 처리 가능", fastTurnover),
                "slowDesign", String.format("초당 %.1f 요청 처리 가능", slowTurnover)
        ));
        result.put("conclusion", "Holding Time 단축이 시스템 처리량에 직접적 영향");

        log.info("Holding Time 분석 완료");
        return result;
    }
}
