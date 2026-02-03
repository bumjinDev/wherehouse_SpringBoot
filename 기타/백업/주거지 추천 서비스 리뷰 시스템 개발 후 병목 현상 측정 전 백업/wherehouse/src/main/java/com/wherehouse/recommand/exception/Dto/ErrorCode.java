package com.wherehouse.recommand.exception.Dto;

public enum ErrorCode {

    // 파라미터 검증 관련 (4000번대)
    INVALID_PARAMETER("E4001", "요청 파라미터가 유효하지 않습니다"),
    MISSING_PARAMETER("E4002", "필수 파라미터가 누락되었습니다"),
    INVALID_PARAMETER_TYPE("E4003", "파라미터 타입이 올바르지 않습니다"),
    INVALID_PARAMETER_FORMAT("E4004", "파라미터 형식이 올바르지 않습니다"),
    INVALID_PARAMETER_RANGE("E4005", "파라미터 범위가 올바르지 않습니다"),

    // 비즈니스 로직 관련 (4100번대)
    BUSINESS_RULE_VIOLATION("E4101", "비즈니스 규칙 위반입니다"),
    INVALID_LEASE_TYPE("E4102", "임대 유형이 올바르지 않습니다"),
    INVALID_PRIORITY("E4103", "우선순위 값이 올바르지 않습니다"),
    DUPLICATE_PRIORITY("E4104", "우선순위가 중복되었습니다"),
    INVALID_BUDGET_RANGE("E4105", "예산 범위가 올바르지 않습니다"),
    INVALID_AREA_RANGE("E4106", "평수 범위가 올바르지 않습니다"),

    // 데이터 관련 (4200번대)
    DATA_NOT_FOUND("E4201", "요청한 데이터를 찾을 수 없습니다"),
    NO_SEARCH_RESULTS("E4202", "검색 결과가 없습니다"),

    // 외부 서비스 관련 (4300번대)
    REDIS_CONNECTION_ERROR("E4301", "Redis 연결에 실패했습니다"),
    EXTERNAL_API_ERROR("E4302", "외부 API 호출에 실패했습니다"),

    // 서버 오류 (5000번대)
    SERVER_ERROR("E5001", "서버 내부 오류가 발생했습니다"),
    UNKNOWN_ERROR("E5002", "알 수 없는 오류가 발생했습니다");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}