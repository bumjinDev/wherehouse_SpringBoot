package com.wherehouse.members.service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.wherehouse.JWT.Filter.Util.JWTUtil;
import com.wherehouse.JWT.Repository.JwtTokenRepository;
import com.wherehouse.JWT.Repository.UserEntityRepository;
import com.wherehouse.JWT.UserDTO.JwtTokenEntity;
import com.wherehouse.JWT.UserDTO.UserEntity;
import com.wherehouse.members.dao.IMembersRepository;
import com.wherehouse.members.dao.MemberEntityRepository;
import com.wherehouse.members.model.MembersVO;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.security.Key;
/**
 * MemberService:
 * 회원 관련 비즈니스 로직을 처리하는 서비스 클래스입니다.
 * 회원 가입, 회원 정보 조회 및 수정과 같은 주요 기능을 제공합니다.
 */
@Service
public class MemberService implements IMemberService {

    @Autowired
    IMembersRepository membersRepository; // 회원 데이터 처리 레포지토리 인터페이스

    @Autowired
    MemberEntityRepository memberEntityRepository; // 회원 관리 테이블 데이터 접근 객체

    @Autowired
    UserEntityRepository userEntityRepository; // 인증 정보 테이블 데이터 접근 객체

    @Autowired
    JwtTokenRepository jwtTokenRepository;
    
    @Autowired
    JWTUtil jWTUtil;
    
    @Autowired
    private BCryptPasswordEncoder passwordEncoder; // 비밀번호 암호화를 위한 객체
    

    @Autowired
    JWTUtil jwtUtil;
    
    @Override
	public Map<String, String> validLogin(HttpServletRequest httpRequest) {
    	
    	// 쿠키에서 Authorization 값 추출 후 쿠키 값에서 DBMS 조회를 통해 JWT 에 대한 키 값 가져와서 이를 가지고 userId 값과 userName 클레임을 반환.
        
        String Authorization = getCookie(httpRequest);
		Key key = getKey(Authorization);
		
		Map<String, String> loginSuccessInfo = new HashMap<String, String>();
		
		loginSuccessInfo.put("userId", jWTUtil.extractUserId(Authorization, key));
		loginSuccessInfo.put("userName", jWTUtil.extractUsername(Authorization, key));
		
		return loginSuccessInfo;
    }
    
    /**
     * 회원 가입 요청 처리:
     * 요청 데이터를 받아 회원 관리 테이블과 인증 정보 테이블에 데이터를 추가합니다.
     * 중복된 ID 혹은 중복된 닉네임으로 회원 가입 시도할 시 회원 가입이 불가능.
     *
     * @param httpRequest 클라이언트로부터 받은 요청 객체
     * @return 상태 코드:
     *         - 1: 이미 존재하는 회원 ID로 인해 가입 실패
     *         - 0: 회원 가입 성공
     */
    @Override
    public int ValidJoin(HttpServletRequest httpRequest) {
        // MembersVO 객체 생성: 회원 관리 테이블에 저장할 데이터를 빌더 패턴으로 생성
        MembersVO membersVO = MembersVO.builder()
                .id(httpRequest.getParameter("id"))
                .pw(httpRequest.getParameter("pw"))
                .nickName(httpRequest.getParameter("nickName"))
                .tel(httpRequest.getParameter("tel"))
                .email(httpRequest.getParameter("email"))
                .joinDate(new java.sql.Date(new Date().getTime())) // 현재 시간 설정
                .build();

        // 기본 권한 설정
        List<String> roles = new ArrayList<>();
        roles.add("user");

        // UserEntity 객체 생성: 인증 정보 테이블에 저장할 데이터를 빌더 패턴으로 생성
        UserEntity userEntity = UserEntity.builder()
                .username(httpRequest.getParameter("nickName"))
                .userid(httpRequest.getParameter("id"))
                .password(passwordEncoder.encode(httpRequest.getParameter("pw"))) // 비밀번호 암호화
                .roles(roles)
                .build();

        // 중복된 회원 ID 확인
        if (memberEntityRepository.findById(httpRequest.getParameter("id")).isPresent()) {
        	
        	System.out.println("이미 동일한 아이디로 가입된 회원이 있으므로 회원 가입 실패!");
            return 1;
        } else if(memberEntityRepository.findByNickName(httpRequest.getParameter("nickName")).isPresent()) { 
        	
        	System.out.println("이미 동일한 닉네임으로 가입된 회원이 있으므로 회원 가입 실패!");
        	return 2;
        	
        } else {
            // 중복되지 않은 회원 ID이므로 회원 가입 처리
            return membersRepository.addMember(membersVO, userEntity);
        }
    }

    /**
     * 회원 정보 조회:
     * 수정 대상 회원의 정보를 조회합니다.
     *
     * @param httpRequest 클라이언트로부터 받은 요청 객체
     * @return 조회된 회원 정보 객체 (MembersVO)
     */
    @Override
    public MembersVO searchEditMember(HttpServletRequest httpRequest) {
        return membersRepository.getMember(httpRequest.getParameter("editid"));
    }

    /**
     * 회원 정보 수정:
     * 회원 관리 테이블과 인증 정보 테이블의 데이터를 수정합니다.
     *
     * @param httpRequest 클라이언트로부터 받은 요청 객체
     * @return 상태 코드:
     *         - 1: 수정 성공
     *         - 2: 닉네임 중복으로 인해 수정 실패
     */
    @Override
    public int editMember(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        // MembersVO 객체 생성: 수정할 회원 관리 테이블 데이터
        MembersVO memberVO = MembersVO.builder()
                .id(httpRequest.getParameter("id"))
                .pw(httpRequest.getParameter("pw"))
                .nickName(httpRequest.getParameter("nickname"))
                .tel(httpRequest.getParameter("tel"))
                .email(httpRequest.getParameter("email"))
                .build();

        // UserEntity 객체 생성: 수정할 인증 정보 테이블 데이터
        UserEntity userEntity = UserEntity.builder()
                .userid(httpRequest.getParameter("id"))
                .username(httpRequest.getParameter("nickname"))
                .password(passwordEncoder.encode(httpRequest.getParameter("pw"))) // 비밀번호 암호화
                .roles(userEntityRepository.findById(httpRequest.getParameter("id")).get().getRoles()) // 기존 권한 유지
                .build();
        
        // 회원 정보를 수정 작업이 정상적으로 되었다면 JWT 토큰 내 사용자 닉네임 클레임을 변경 후 이에 따라 쿠키 값도 갱신.(반환 값 : 1 / 수정 안되면 2 )
        int ri = membersRepository.editMember(memberVO, userEntity);
        
        if(ri == 1) {
        	
        	String jwtToken = getCookie(httpRequest);
        	Key key = getKey(jwtToken);
        	
        	String newJwtToken = jwtUtil.modifyUsername(jwtToken, key, memberVO.getNickName());
        	
        	jwtTokenRepository.deleteById(jwtToken);
        	jwtTokenRepository.save(
        			JwtTokenEntity.builder()
        			.jwttoken(newJwtToken)
        			.hmacSha256Key(jwtUtil.encodeKeyToBase64(key))
        			.build()
        		);
        	
        	Cookie cookie = new Cookie("Authorization", newJwtToken);
    		cookie.setSecure(false);       // HTTPS에서만 작동
    		cookie.setHttpOnly(true);     // JavaScript 접근 불가
    		cookie.setPath("/");
    		httpResponse.addCookie(cookie);
    		
    		httpResponse.setCharacterEncoding("UTF-8");
        }
        
        // 회원 정보 수정 완료 후 정상적으로 수행이 되었다면 
        return ri;
    }
    
    private String getCookie(HttpServletRequest httpRequest) {
    	
    	Cookie[] cookies = httpRequest.getCookies();
    	
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("Authorization".equals(cookie.getName())) { // 쿠키 이름이 "Authorization"인지 확인
                	return cookie.getValue(); // 쿠키 값 가져오기
                }
            }
        }
        return null;
    }
    
    private Key getKey(String jwtToken) {
    	
    	// DB에서 인코딩된 hmacSha256Key 값 가져오기     
		String encodedHmacSha256Key = jwtTokenRepository.findById(jwtToken).get().getHmacSha256Key();
		// Base64 디코딩하여 바이트 배열로 변환
		byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedHmacSha256Key);
		// 바이트 배열을 사용하여 Key 객체 생성
		return new SecretKeySpec(decodedBytes, "HmacSHA256");
    }
}
