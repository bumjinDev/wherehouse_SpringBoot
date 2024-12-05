package com.wherehouse.JWT.UserDTO;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Data
@Builder
@AllArgsConstructor   // 모든 필드를 포함한 생성자 생성
@NoArgsConstructor    // 기본 생성자 생성
@Table(name="JwtTokenEntity")
public class JwtTokenEntity {

	@Id
    private String jwttoken;    	// 사용자 닉네임
	//private String jwt;			// 사용자 JWT
    private String hmacSha256Key;	// 각 사용자 별 랜덤 생성 시그니처 키 값.
//    private Date issuedAt;       	// 토큰 발급 시간
//    private Date expiration;    	// 토큰 만료 시간
}
