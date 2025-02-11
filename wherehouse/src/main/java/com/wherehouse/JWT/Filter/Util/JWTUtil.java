package com.wherehouse.JWT.Filter.Util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.time.Duration;
import javax.crypto.spec.SecretKeySpec;

public class JWTUtil {

    private final long expirationTime = 3600000; // 1시간 유효

    public String encodeKeyToBase64(Key key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key.getEncoded());
    }
    
    /**
     * Base64로 인코딩된 키 문자열을 Key 객체로 디코딩합니다.
     *
     * @param base64Key Base64로 인코딩된 키 문자열
     * @return Key 객체
     */
    public Key decodeBase64ToKey(String base64Key) {
        byte[] decodedKey = Base64.getUrlDecoder().decode(base64Key);
        return new SecretKeySpec(decodedKey, "HmacSHA256");
    }
    
    public Key getSigningKey() {
        byte[] keyBytes = new byte[32]; // HMAC SHA-256용 256비트 키
        new SecureRandom().nextBytes(keyBytes);
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    // JWT 토큰 생성 (username, userId, roles, 발급 시간, 만료 시간 포함)
    public String generateToken(String username, String userId, List<String> roles, Key key) {
        Date now = new Date();
        return Jwts.builder()
                .claim("username", username)                  // 사용자 이름 클레임 추가
                .claim("userId", userId)                      // 사용자 ID 클레임 추가
                .claim("roles", roles)                        // 역할(roles) 클레임 추가
                .setIssuedAt(now)                             // 발급 시간 설정
                .setExpiration(new Date(now.getTime() + expirationTime)) // 만료 시간 설정
                .signWith(key)                                // Key 객체로 서명
                .compact();
    }

    // JWT 토큰에서 모든 클레임(Claims) 추출
    public Claims extractAllClaims(String token, Key key) {
        return Jwts.parserBuilder()
                .setSigningKey(key) // 서명 확인용 키 설정
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // 클레임 수정 메소드
    public String modifyClaim(String token, Key key, String claimKey, Object newValue) {
        Claims claims = extractAllClaims(token, key); // 기존 클레임 추출
        claims.put(claimKey, newValue); // 수정된 값으로 클레임 추가 또는 갱신

        // 새로운 토큰 생성
        return Jwts.builder()
                .setClaims(claims) // 수정된 클레임 사용
                .setIssuedAt(new Date()) // 재발급 시간 갱신
                .setExpiration(new Date(new Date().getTime() + expirationTime)) // 만료 시간 갱신
                .signWith(key) // 동일한 키로 서명
                .compact();
    }

    // username 클레임 수정
    public String modifyUsername(String token, Key key, String newUsername) {
        return modifyClaim(token, key, "username", newUsername);
    }

    // userId 클레임 수정
    public String modifyUserId(String token, Key key, String newUserId) {
        return modifyClaim(token, key, "userId", newUserId);
    }

    // roles 클레임 수정
    public String modifyRoles(String token, Key key, List<String> newRoles) {
        return modifyClaim(token, key, "roles", newRoles);
    }

    // 기존 메소드들...
    public String extractUsername(String token, Key key) {
        return extractAllClaims(token, key).get("username", String.class);
    }

    public String extractUserId(String token, Key key) {
        return extractAllClaims(token, key).get("userId", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token, Key key) {
        return extractAllClaims(token, key).get("roles", List.class);
    }

    public Date extractIssuedAt(String token, Key key) {
        return extractAllClaims(token, key).getIssuedAt();
    }

    /* 토큰 만료 시간 추출. */
    public Date extractExpiration(String token, Key key) {
        return extractAllClaims(token, key).getExpiration();
    }
    
    /* "extractExpiration" 호출해서 만료 시간을 "Date" 로 뽑아내고 이를 duration 으로 반환. */
    public Duration getRemainingDuration(String token, Key key) {
        Date expiration = extractExpiration(token, key);
        long remainingMillis = expiration.getTime() - System.currentTimeMillis();
        
        return remainingMillis > 0 ? Duration.ofMillis(remainingMillis) : Duration.ZERO;
    }

    public Boolean isTokenExpired(String token, Key key) {
        return extractExpiration(token, key).before(new Date());
    }

    public boolean validateToken(String token, Key key) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(key) // 서명 검증용 키 설정
                .build()
                .parseClaimsJws(token); // 유효한 서명인지 검증
            return true;
        } catch (Exception e) {
        	System.out.println("JWTUitil : 서명이 유효하지 않음!");
            return false; // 유효하지 않은 서명일 경우
        }
    }
}
