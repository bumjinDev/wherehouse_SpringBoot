package com.wherehouse.JWT.Filter;

import java.security.Key;
import java.util.Base64;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wherehouse.JWT.Filter.Util.JWTUtil;
import com.wherehouse.JWT.Repository.JwtTokenRepository;
import com.wherehouse.JWT.UserDTO.JwtTokenEntity;

import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.servlet.http.HttpServletRequest;

/*
 * RequestAuthenticaiton 과 JWTAuthentication 두 클래스에 대한 공통된 메소드를 책임분리원칙(DRY) 에 근거하여 여기서
 * 	공통 구현.
 * */
@Component
public class JwtComponent {
	
	@Autowired
	JwtTokenRepository jwtTokenRepository;
	
	@Autowired
	JWTUtil jwtUtil;
	
	public Key getSigningKey(String token) {
		
		System.out.println("JwtComponent.getSigningKey()!");
		System.out.println("token : " + token);
		System.out.println("jwtTokenRepository.findById(token) : " + jwtTokenRepository.findById(token).get().getHmacSha256Key());
		
		
		JwtTokenEntity tokenEntity = jwtTokenRepository.findById(token)
	                        .orElseThrow(() -> new IllegalArgumentException("토큰에 해당하는 키가 존재하지 않습니다."));
		System.out.println("tokenEntity.getHmacSha256Key() : " + tokenEntity.getHmacSha256Key());
	    return new SecretKeySpec(
	        Base64.getUrlDecoder().decode(tokenEntity.getHmacSha256Key()), // Safe Base64 디코딩
	        SignatureAlgorithm.HS256.getJcaName()
	    );
	}

	
	/* 토큰을 검증 및 결과인 클레임(사용자 닉네임, 사용자 ID, 권한들)을 HttpServletRequest httpRequest 설정(스레드 스코프로써 동일 요청 컨텍스트 내에서는 동일하게 접근 가능), 없으면 그냥 예외 처리.
	 * 	*/
	public boolean validateToken(String token, Key key, HttpServletRequest httpRequest){
		
		if (!jwtUtil.validateToken(token, key)) {
            throw new SecurityException("검증 결과 토큰이 유효하지 않음");
        }

        // JWT에서 사용자 정보 및 권한 추출
		String userId = jwtUtil.extractUserId(token, key);
		String userName = jwtUtil.extractUsername(token, key);
        List<String> roles = jwtUtil.extractRoles(token, key);

        // 요청 객체에 사용자 정보와 JWT 정보 설정
        httpRequest.setAttribute("userId", userId);
        httpRequest.setAttribute("userName", userName);
        httpRequest.setAttribute("roles", roles);
        httpRequest.setAttribute("jwtToken", token);
        
        return true;
	}
}
