package com.example.jdbcapp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

/**
 * DriverManager의 런타임 제어 메커니즘을 검증하는 서비스
 */
@Slf4j
@Service
public class DriverManagerService {

    private static final String URL = "jdbc:oracle:thin:@43.202.178.156:1521:xe";
    private static final String USER = "SCOTT";
    private static final String PASSWORD = "tiger";

    /**
     * ServiceLoader에 의한 드라이버 자동 등록 확인
     */
    public Map<String, Object> verifyDriverRegistration() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> drivers = new ArrayList<>();

        Enumeration<Driver> registeredDrivers = DriverManager.getDrivers();
        boolean oracleFound = false;

        while (registeredDrivers.hasMoreElements()) {
            Driver driver = registeredDrivers.nextElement();
            Map<String, Object> driverInfo = new LinkedHashMap<>();
            driverInfo.put("className", driver.getClass().getName());
            driverInfo.put("majorVersion", driver.getMajorVersion());
            driverInfo.put("minorVersion", driver.getMinorVersion());
            drivers.add(driverInfo);

            if (driver.getClass().getName().contains("oracle")) {
                oracleFound = true;
            }
        }

        result.put("registeredDrivers", drivers);
        result.put("oracleDriverFound", oracleFound);
        result.put("mechanism", "JDBC 4.0 ServiceLoader (META-INF/services/java.sql.Driver)");

        log.info("드라이버 등록 확인 완료 - Oracle 드라이버 발견: {}", oracleFound);
        return result;
    }

    /**
     * 드라이버의 connect() 직접 호출 방식 검증
     * DriverManager는 acceptsURL()을 명시적으로 호출하지 않고 connect()를 순차 호출함
     */
    public Map<String, Object> verifyConnectMethod() throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();

        Driver oracleDriver = DriverManager.getDriver(URL);
        result.put("selectedDriver", oracleDriver.getClass().getName());

        // acceptsURL - URL 처리 가능 여부 (핵심 경로에서는 미사용)
        boolean canAccept = oracleDriver.acceptsURL(URL);
        result.put("acceptsURL", canAccept);
        result.put("acceptsURLNote", "DriverManager 핵심 경로에서는 미사용, connect()만 호출됨");

        // connect() 직접 호출로 물리적 연결 수립
        Properties props = new Properties();
        props.setProperty("user", USER);
        props.setProperty("password", PASSWORD);

        long startTime = System.currentTimeMillis();
        try (Connection conn = oracleDriver.connect(URL, props)) {
            long elapsed = System.currentTimeMillis() - startTime;

            result.put("connectionClass", conn.getClass().getName());
            result.put("connectionTime", elapsed + " ms");
            result.put("isClosed", conn.isClosed());
        }

        log.info("connect() 직접 호출 검증 완료");
        return result;
    }

    /**
     * DriverManager.getConnection() 내부 동작 시뮬레이션
     */
    public Map<String, Object> simulateGetConnection() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> attempts = new ArrayList<>();

        /*
         * 실제 DriverManager 내부 로직:
         * for (DriverInfo aDriver : registeredDrivers) {
         *     Connection con = aDriver.driver.connect(url, info);
         *     if (con != null) return con;
         * }
         */

        Properties props = new Properties();
        props.setProperty("user", USER);
        props.setProperty("password", PASSWORD);

        Enumeration<Driver> drivers = DriverManager.getDrivers();
        Connection resultConnection = null;

        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            Map<String, Object> attempt = new LinkedHashMap<>();
            attempt.put("driver", driver.getClass().getSimpleName());

            try {
                Connection conn = driver.connect(URL, props);
                if (conn != null) {
                    attempt.put("result", "SUCCESS - Connection 획득");
                    resultConnection = conn;
                    attempts.add(attempt);
                    break;
                } else {
                    attempt.put("result", "null 반환 (URL 처리 불가)");
                }
            } catch (SQLException e) {
                attempt.put("result", "예외: " + e.getMessage());
            }
            attempts.add(attempt);
        }

        result.put("attempts", attempts);
        result.put("mechanism", "등록된 드라이버들의 connect()를 순차 호출, null 아닌 첫 번째 반환");

        if (resultConnection != null) {
            try {
                resultConnection.close();
            } catch (SQLException ignored) {}
        }

        log.info("getConnection() 시뮬레이션 완료");
        return result;
    }

    /**
     * 물리적 커넥션 수립 비용 측정
     */
    public Map<String, Object> measureConnectionCost(int iterations) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Long> times = new ArrayList<>();
        long totalTime = 0;

        for (int i = 0; i < iterations; i++) {
            long start = System.currentTimeMillis();
            try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
                long elapsed = System.currentTimeMillis() - start;
                times.add(elapsed);
                totalTime += elapsed;
            }
        }

        double avgTime = (double) totalTime / iterations;

        result.put("iterations", iterations);
        result.put("times", times);
        result.put("totalTime", totalTime + " ms");
        result.put("averageTime", String.format("%.1f ms", avgTime));
        result.put("implication", "이 비용(50~200ms)이 HikariCP 풀링의 근거");

        log.info("커넥션 수립 비용 측정 완료 - 평균: {} ms", avgTime);
        return result;
    }
}
