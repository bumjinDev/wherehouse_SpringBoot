package com.wherehouse.recommand.batch.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 매물 고유 식별자 생성기
 * 
 * 국토교통부 API 데이터에는 고유 ID가 없어 매 배치 실행마다 동일 매물을 식별할 수 없는 문제를 해결하기 위해
 * 매물의 불변 속성(위치, 면적, 층수)을 조합한 MD5 Hash를 생성하여 영속적 식별자로 사용합니다.
 * 
 * @author 정범진
 * @since 2025-12-05
 */
@Slf4j
@Component
public class IdGenerator {

    private static final String DELIMITER = "|";
    
    /**
     * 매물의 불변 속성을 기반으로 32자 MD5 Hash 식별자를 생성합니다.
     * 
     * 조합 대상 속성:
     * - 시군구코드 (sggCd): 행정구역 식별
     * - 지번 (jibun): 필지 식별
     * - 아파트명 (aptNm): 단지 식별
     * - 층수 (floor): 호실 위치 식별
     * - 전용면적 (excluUseAr): 평형 식별
     * 
     * 예시 입력: "11680", "역삼동 123-45", "래미안그레이튼", "15", "84.50"
     * 예시 출력: "5d41402abc4b2a76b9719d911017c592"
     * 
     * @param sggCd 시군구코드 (5자리)
     * @param jibun 지번
     * @param aptNm 아파트명
     * @param floor 층수
     * @param excluUseAr 전용면적(㎡)
     * @return 32자 MD5 Hash 문자열 (소문자)
     * @throws IllegalArgumentException 필수 파라미터가 null이거나 비어있는 경우
     */
    public String generatePropertyId(String sggCd, String jibun, String aptNm, 
                                     String floor, String excluUseAr) {
        
        validateParameters(sggCd, jibun, aptNm, floor, excluUseAr);
        
        String rawKey = buildRawKey(sggCd, jibun, aptNm, floor, excluUseAr);
        
        return calculateMd5Hash(rawKey);
    }

    /**
     * 필수 파라미터 유효성 검증
     */
    private void validateParameters(String sggCd, String jibun, String aptNm, 
                                    String floor, String excluUseAr) {
        if (isNullOrEmpty(sggCd)) {
            throw new IllegalArgumentException("시군구코드(sggCd)는 필수입니다");
        }
        if (isNullOrEmpty(jibun)) {
            throw new IllegalArgumentException("지번(jibun)은 필수입니다");
        }
        if (isNullOrEmpty(aptNm)) {
            throw new IllegalArgumentException("아파트명(aptNm)은 필수입니다");
        }
        if (isNullOrEmpty(floor)) {
            throw new IllegalArgumentException("층수(floor)는 필수입니다");
        }
        if (isNullOrEmpty(excluUseAr)) {
            throw new IllegalArgumentException("전용면적(excluUseAr)은 필수입니다");
        }
    }

    /**
     * 불변 속성들을 구분자로 연결하여 원본 키 문자열 생성
     * 
     * 공백 제거: 데이터 정합성 확보를 위해 모든 공백을 제거합니다.
     * 대소문자 통일: 대소문자 차이로 인한 중복 ID 생성을 방지하기 위해 소문자로 통일합니다.
     */
    private String buildRawKey(String sggCd, String jibun, String aptNm, 
                              String floor, String excluUseAr) {
        return String.join(DELIMITER,
                normalizeValue(sggCd),
                normalizeValue(jibun),
                normalizeValue(aptNm),
                normalizeValue(floor),
                normalizeValue(excluUseAr)
        );
    }

    /**
     * 값 정규화: 공백 제거 및 소문자 변환
     */
    private String normalizeValue(String value) {
        return value.replaceAll("\\s+", "").toLowerCase();
    }

    /**
     * MD5 해시 알고리즘을 사용하여 32자 16진수 문자열 생성
     * 
     * MD5는 128bit(16byte) 해시를 생성하며, 이를 16진수 문자열로 변환하면 32자가 됩니다.
     * 충돌 가능성은 이론적으로 존재하나, 실무적으로는 무시 가능한 수준입니다.
     */
    private String calculateMd5Hash(String rawKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
            
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 알고리즘을 찾을 수 없습니다", e);
            throw new RuntimeException("ID 생성 실패: MD5 알고리즘 초기화 오류", e);
        }
    }

    /**
     * 바이트 배열을 16진수 문자열로 변환
     * 
     * 각 바이트(8bit)를 2자리 16진수로 변환하여 연결합니다.
     * 예: [0x5D, 0x41, 0x40] -> "5d4140"
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * null 또는 빈 문자열 여부 확인
     */
    private boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
