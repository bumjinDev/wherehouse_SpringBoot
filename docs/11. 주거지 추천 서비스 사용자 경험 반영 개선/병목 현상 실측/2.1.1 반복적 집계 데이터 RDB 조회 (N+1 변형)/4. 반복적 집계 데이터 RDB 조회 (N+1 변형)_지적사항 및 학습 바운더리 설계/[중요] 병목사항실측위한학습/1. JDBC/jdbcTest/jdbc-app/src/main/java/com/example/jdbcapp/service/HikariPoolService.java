package com.example.jdbcapp.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

/**
 * HikariCP의 커넥션 풀링 동작을 검증하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HikariPoolService {

    private final DataSource dataSource;

    /**
     * HikariDataSource 구성 정보 조회
     */
    public Map<String, Object> getPoolConfiguration() {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!(dataSource instanceof HikariDataSource hikariDS)) {
            result.put("error", "DataSource가 HikariDataSource가 아님");
            return result;
        }

        result.put("poolName", hikariDS.getPoolName());
        result.put("jdbcUrl", hikariDS.getJdbcUrl());
        result.put("driverClassName", hikariDS.getDriverClassName());
        result.put("maximumPoolSize", hikariDS.getMaximumPoolSize());
        result.put("minimumIdle", hikariDS.getMinimumIdle());
        result.put("connectionTimeout", hikariDS.getConnectionTimeout() + " ms");
        result.put("idleTimeout", hikariDS.getIdleTimeout() + " ms");
        result.put("maxLifetime", hikariDS.getMaxLifetime() + " ms");
        result.put("autoCommit", hikariDS.isAutoCommit());

        log.info("HikariCP 구성 정보 조회 완료");
        return result;
    }

    /**
     * 풀 상태 모니터링 (MXBean)
     */
    public Map<String, Object> getPoolStatus() {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!(dataSource instanceof HikariDataSource hikariDS)) {
            result.put("error", "DataSource가 HikariDataSource가 아님");
            return result;
        }

        HikariPoolMXBean mxBean = hikariDS.getHikariPoolMXBean();
        result.put("totalConnections", mxBean.getTotalConnections());
        result.put("activeConnections", mxBean.getActiveConnections());
        result.put("idleConnections", mxBean.getIdleConnections());
        result.put("threadsAwaitingConnection", mxBean.getThreadsAwaitingConnection());

        return result;
    }

    /**
     * 커넥션 대여/반납 시나리오 실행
     */
    public Map<String, Object> demonstrateBorrowReturn() throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> steps = new ArrayList<>();

        HikariDataSource hikariDS = (HikariDataSource) dataSource;
        HikariPoolMXBean mxBean = hikariDS.getHikariPoolMXBean();

        // 초기 상태
        steps.add(createPoolSnapshot("초기 상태", mxBean));

        // 커넥션 1개 대여
        Connection conn1 = dataSource.getConnection();
        steps.add(createPoolSnapshot("커넥션 1개 대여", mxBean));

        // 커넥션 2개 추가 대여
        Connection conn2 = dataSource.getConnection();
        Connection conn3 = dataSource.getConnection();
        steps.add(createPoolSnapshot("커넥션 3개 대여", mxBean));

        // 전체 반납
        conn1.close();
        conn2.close();
        conn3.close();
        steps.add(createPoolSnapshot("전체 반납", mxBean));

        result.put("steps", steps);
        result.put("mechanism", "HikariProxyConnection.close()는 물리적 종료가 아닌 풀 반납");

        log.info("커넥션 대여/반납 시연 완료");
        return result;
    }

    private Map<String, Object> createPoolSnapshot(String label, HikariPoolMXBean mxBean) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("step", label);
        snapshot.put("total", mxBean.getTotalConnections());
        snapshot.put("active", mxBean.getActiveConnections());
        snapshot.put("idle", mxBean.getIdleConnections());
        return snapshot;
    }

    /**
     * 풀 재사용 vs 신규 생성 비용 비교
     */
    public Map<String, Object> compareCost() throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        HikariDataSource hikariDS = (HikariDataSource) dataSource;

        // 1. 풀에서 재사용
        int poolIterations = 100;
        long poolTotal = 0;
        for (int i = 0; i < poolIterations; i++) {
            long start = System.nanoTime();
            try (Connection conn = dataSource.getConnection()) {
                poolTotal += (System.nanoTime() - start);
            }
        }
        double poolAvgMicro = (double) poolTotal / poolIterations / 1000;

        // 2. DriverManager 직접 호출 (신규 생성)
        int directIterations = 3;
        long directTotal = 0;
        for (int i = 0; i < directIterations; i++) {
            long start = System.currentTimeMillis();
            try (Connection conn = DriverManager.getConnection(
                    hikariDS.getJdbcUrl(),
                    hikariDS.getUsername(),
                    hikariDS.getPassword())) {
                directTotal += (System.currentTimeMillis() - start);
            }
        }
        double directAvgMs = (double) directTotal / directIterations;

        result.put("poolReuse", Map.of(
                "iterations", poolIterations,
                "average", String.format("%.1f μs", poolAvgMicro)
        ));
        result.put("newConnection", Map.of(
                "iterations", directIterations,
                "average", String.format("%.1f ms", directAvgMs)
        ));
        result.put("speedup", String.format("풀 재사용이 약 %.0f배 빠름", directAvgMs * 1000 / poolAvgMicro));

        log.info("비용 비교 완료 - 풀: {}μs, 신규: {}ms", poolAvgMicro, directAvgMs);
        return result;
    }

    /**
     * 풀 고갈 시나리오 시뮬레이션
     */
    public Map<String, Object> simulateExhaustion() throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> steps = new ArrayList<>();

        HikariDataSource hikariDS = (HikariDataSource) dataSource;
        HikariPoolMXBean mxBean = hikariDS.getHikariPoolMXBean();
        int maxPoolSize = hikariDS.getMaximumPoolSize();

        result.put("maximumPoolSize", maxPoolSize);

        List<Connection> heldConnections = new ArrayList<>();

        // 모든 커넥션 점유
        for (int i = 0; i < maxPoolSize; i++) {
            Connection conn = dataSource.getConnection();
            heldConnections.add(conn);

            Map<String, Object> step = new LinkedHashMap<>();
            step.put("connectionNumber", i + 1);
            step.put("active", mxBean.getActiveConnections());
            step.put("idle", mxBean.getIdleConnections());
            steps.add(step);
        }

        result.put("exhaustionSteps", steps);
        result.put("isExhausted", mxBean.getIdleConnections() == 0);
        result.put("warning", "풀 고갈 상태에서 추가 요청 시 connection-timeout까지 대기 후 예외 발생");

        // 정리
        for (Connection conn : heldConnections) {
            conn.close();
        }

        result.put("afterRelease", Map.of(
                "active", mxBean.getActiveConnections(),
                "idle", mxBean.getIdleConnections()
        ));

        log.info("풀 고갈 시뮬레이션 완료");
        return result;
    }
}
