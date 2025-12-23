package com.example.jdbcapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * Connection의 물리적 실체와 Oracle 세션 정보를 검증하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionPhysicalService {

    private final DataSource dataSource;

    /**
     * DatabaseMetaData를 통한 연결 정보 확인
     */
    public Map<String, Object> getConnectionMetadata() throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            result.put("databaseProduct", metaData.getDatabaseProductName());
            result.put("databaseVersion", metaData.getDatabaseProductVersion());
            result.put("driverName", metaData.getDriverName());
            result.put("driverVersion", metaData.getDriverVersion());
            result.put("jdbcUrl", metaData.getURL());
            result.put("userName", metaData.getUserName());
            result.put("jdbcMajorVersion", metaData.getJDBCMajorVersion());
            result.put("jdbcMinorVersion", metaData.getJDBCMinorVersion());
        }

        log.info("Connection 메타데이터 조회 완료");
        return result;
    }

    /**
     * Oracle V$SESSION을 통한 서버 측 세션 증명
     */
    public Map<String, Object> getOracleSessionInfo() throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();

        String sessionQuery = """
            SELECT 
                SID,
                SERIAL#,
                USERNAME,
                STATUS,
                PROGRAM,
                MACHINE,
                LOGON_TIME
            FROM V$SESSION 
            WHERE SID = SYS_CONTEXT('USERENV', 'SID')
            """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sessionQuery)) {

            if (rs.next()) {
                result.put("sid", rs.getInt("SID"));
                result.put("serial", rs.getInt("SERIAL#"));
                result.put("username", rs.getString("USERNAME"));
                result.put("status", rs.getString("STATUS"));
                result.put("program", rs.getString("PROGRAM"));
                result.put("machine", rs.getString("MACHINE"));
                result.put("logonTime", rs.getTimestamp("LOGON_TIME"));
                result.put("proof", "V$SESSION 조회 성공 = 물리적 Oracle 세션 수립 증명");
            }
        }

        log.info("Oracle 세션 정보 조회 완료");
        return result;
    }

    /**
     * PGA 메모리 할당 증명 (DBA 권한 필요)
     */
    public Map<String, Object> getPgaMemoryInfo() throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();

        String pgaQuery = """
            SELECT 
                s.SID,
                p.SPID AS OS_PROCESS_ID,
                ROUND(p.PGA_USED_MEM / 1024 / 1024, 2) AS PGA_USED_MB,
                ROUND(p.PGA_ALLOC_MEM / 1024 / 1024, 2) AS PGA_ALLOC_MB,
                ROUND(p.PGA_MAX_MEM / 1024 / 1024, 2) AS PGA_MAX_MB
            FROM V$SESSION s
            JOIN V$PROCESS p ON s.PADDR = p.ADDR
            WHERE s.SID = SYS_CONTEXT('USERENV', 'SID')
            """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(pgaQuery)) {

            if (rs.next()) {
                result.put("sid", rs.getInt("SID"));
                result.put("osProcessId", rs.getString("OS_PROCESS_ID"));
                result.put("pgaUsedMB", rs.getDouble("PGA_USED_MB"));
                result.put("pgaAllocMB", rs.getDouble("PGA_ALLOC_MB"));
                result.put("pgaMaxMB", rs.getDouble("PGA_MAX_MB"));
                result.put("proof", "PGA 할당 확인 = 서버 RAM 내 메모리 점유 증명");
            }
        } catch (SQLException e) {
            result.put("error", "V$PROCESS 조회 권한 없음 - DBA 권한 필요");
            result.put("alternative", "V$MYSTAT으로 세션 통계 확인 가능");
            log.warn("PGA 정보 조회 실패: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 다중 커넥션의 독립적 세션 증명
     */
    public Map<String, Object> proveIndependentSessions() throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> sessions = new ArrayList<>();

        try (Connection conn1 = dataSource.getConnection();
             Connection conn2 = dataSource.getConnection();
             Connection conn3 = dataSource.getConnection()) {

            int sid1 = getSessionId(conn1);
            int sid2 = getSessionId(conn2);
            int sid3 = getSessionId(conn3);

            sessions.add(Map.of("connection", 1, "sid", sid1));
            sessions.add(Map.of("connection", 2, "sid", sid2));
            sessions.add(Map.of("connection", 3, "sid", sid3));

            result.put("sessions", sessions);
            result.put("allDifferent", sid1 != sid2 && sid2 != sid3 && sid1 != sid3);
            result.put("proof", "각 Connection은 독립적인 Oracle 세션, 각각 별도 PGA 점유");
        }

        log.info("독립적 세션 증명 완료");
        return result;
    }

    /**
     * Connection Holding Time 시연
     */
    public Map<String, Object> demonstrateHoldingTime() throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> queries = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            int sid = getSessionId(conn);
            result.put("sessionId", sid);

            long totalQueryTime = 0;
            int queryCount = 5;

            for (int i = 1; i <= queryCount; i++) {
                long start = System.currentTimeMillis();

                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT SYSDATE FROM DUAL")) {
                    rs.next();
                }

                long queryTime = System.currentTimeMillis() - start;
                totalQueryTime += queryTime;

                queries.add(Map.of("queryNumber", i, "time", queryTime + " ms"));
            }

            result.put("queries", queries);
            result.put("totalQueryTime", totalQueryTime + " ms");
            result.put("holdingTimeNote", "세션 ID " + sid + "는 이 기간 동안 계속 점유됨");
            result.put("n1Problem", "N+1 문제: 각 쿼리의 네트워크 왕복 시간 누적 → Holding Time 증가 → 풀 회전율 저하");
        }

        log.info("Holding Time 시연 완료");
        return result;
    }

    private int getSessionId(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT SYS_CONTEXT('USERENV', 'SID') AS SID FROM DUAL")) {
            rs.next();
            return rs.getInt("SID");
        }
    }
}
