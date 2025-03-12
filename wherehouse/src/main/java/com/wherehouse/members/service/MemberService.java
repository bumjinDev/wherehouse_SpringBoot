package com.wherehouse.members.service;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.security.Key;
import java.sql.Date;
import java.time.Duration;

import javax.crypto.spec.SecretKeySpec;

import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import com.wherehouse.JWT.DTO.AuthenticationEntityConverter;
import com.wherehouse.JWT.Filter.Util.JWTUtil;
import com.wherehouse.JWT.Repository.UserEntityRepository;
import com.wherehouse.members.dao.IMembersRepository;
import com.wherehouse.members.dao.MemberEntityRepository;
import com.wherehouse.members.model.MemberConverter;
import com.wherehouse.members.model.MemberDTO;
import com.wherehouse.members.model.MembersEntity;
import com.wherehouse.redis.handler.RedisHandler;

import org.slf4j.Logger;

@Service
public class MemberService implements IMemberService {

	private static final Logger logger = LoggerFactory.getLogger(MemberService.class);
	
    IMembersRepository membersRepository;
    
    MemberEntityRepository memberEntityRepository;
    
    UserEntityRepository userEntityRepository;

    RedisHandler redisHandler;
    
    JWTUtil jwtUtil;

    private BCryptPasswordEncoder passwordEncoder; // 비밀번호 암호화를 위한 객체
    
    MemberConverter memberConverter;
    AuthenticationEntityConverter authenticationEntityConverter;
    
    public final int USER_ID_DUPLICATE = 1;
    public final int NICKNAME_DUPLICATE = 2;

    public MemberService(
    		
    		IMembersRepository membersRepository,
    		MemberEntityRepository memberEntityRepository,
    		UserEntityRepository userEntityRepository,
    		RedisHandler redisHandler,
    		JWTUtil jwtUtil,
    		BCryptPasswordEncoder passwordEncoder,
    		MemberConverter memberConverter,
    		AuthenticationEntityConverter authenticationEntityConverter) {
    	
    	this.membersRepository = membersRepository;
    	this.memberEntityRepository = memberEntityRepository;
    	this.userEntityRepository = userEntityRepository;
    	this.redisHandler = redisHandler;
    	this.jwtUtil = jwtUtil;
    	this.passwordEncoder = passwordEncoder;
    	this.memberConverter = memberConverter;
    	this.authenticationEntityConverter = authenticationEntityConverter;
    }
    
    @Override
    public Map<String, String> validLoginSuccess(String jwt) {
    	
    	logger.info("MemberService.validLogin()!");
    	
        Key key = getKey(jwt);

        Map<String, String> loginSuccessInfo = new HashMap<>();
        
        loginSuccessInfo.put("userId", jwtUtil.extractUserId(jwt, key));
        loginSuccessInfo.put("userName", jwtUtil.extractUsername(jwt, key));

        return loginSuccessInfo;
    }

    @Override
    public int validJoin(MemberDTO memberDTO) {
    	
    	logger.info("MemberService.validJoin()!");
    	
    	/* 회원 가입 요청에 따라 회원 가입이 가능한지 확인. */
        if ( memberEntityRepository.findById(memberDTO.getId()).isPresent() ) {	// id 중복 여부 확인
        	return USER_ID_DUPLICATE;
        }
        
        if (memberEntityRepository.findByNickName(memberDTO.getNickName()).isPresent()) {	// 닉네임 중복 여부 확인.
        	return NICKNAME_DUPLICATE;
        }
        	
        /* Id 와 닉네임 모두 중복 되지 않은 요청일 시 회원 가입 진행.
         * 1. 회원 관리 테이블 내 회원 정보 추가
         * 2. SpringSecurity 테이블 내 인증 정보 추가.
 		*/
    	
        /* 비밀번호 암호화 수행 */
        String encodedPassword = passwordEncoder.encode(memberDTO.getPw());
        memberDTO.setPw(encodedPassword);  // 암호화된 비밀번호로 변경

        // 현재 시간 설정 (회원가입 시간)
        memberDTO.setJoinDate(new Date(System.currentTimeMillis()));
        
        List<String> roles = List.of("ROLE_USER");
        
        return membersRepository.addMember(
        		memberConverter.toEntity(memberDTO),
        		authenticationEntityConverter.toEntity(memberDTO, roles));
    }
    

    @Override
    public MembersEntity searchEditMember(String editId) {
        return membersRepository.getMember(editId);
    }

    /* editMember : 사용자 수정 요청 처리.
     * 사용자 수정 요청 발생 시 회원 관리 테이블 뿐만 아니라 SpringSecurity 에서 인증 절차에 사용하는
     * 별도의 테이블 또한 수정해야 되며 JWT 도 수정하여 브라우저에 응답으로 포함 한다.
     * */
    
    @Override
    public String editMember(String currentToken, MemberDTO memberDTO) {
    	
    	logger.info("MemberService.editMember()");
    	
    	/* SpringSecurity  */
        List<String> roles = userEntityRepository.findById(memberDTO.getId())
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getRoles();
   
        /* 비밀번호 암호화 수행 */
        String encodedPassword = passwordEncoder.encode(memberDTO.getPw());
        memberDTO.setPw(encodedPassword);  // 암호화된 비밀번호로 변경

        // 현재 시간 설정 (회원가입 시간)
        memberDTO.setJoinDate(new Date(System.currentTimeMillis()));
        // 인증 및 회원 관리에 관한 2개 테이블 DB 테이블 갱신.
        int result = membersRepository.editMember(
        		memberConverter.toEntity(memberDTO),
        		authenticationEntityConverter.toEntity(memberDTO, roles));

        // 정상 적인 수정이 되었다면 변경된 컬럼이 있으니 새롭게 jwt 만들어서 db 내부 갱신하고 HttpResponse 객체 내 응답 중 쿠키 갱신
        if (result == 1) {
        	
        	// 현재 JWT의 키를 재 사용.
        	Key newKey = getKey(currentToken);
        	// 현재 토큰의 사용자 닉네임 컬럼을 수정해서 새로운 컬럼으로 반환.
        	String newToken = editToken(currentToken, newKey, memberDTO.getNickName());
        	// Redis 내 JWT 정보 갱신.
        	updateJwtToken(currentToken, newToken, newKey);
        	
        	return newToken;
        
        } else { return String.valueOf(NICKNAME_DUPLICATE); }	// 2 를 반환 시 Repository 에서 회원 정보 수정 시 닉네임 중복으로 실패 했다는 의미므로 2를 컨트롤러에게 반환.
    }

    // 기존 JWT 토큰에 대해서 변경된 클레임을 수정해서 새로운 JWT 토큰으로 반환.
    private String editToken(String cueentToken, Key key, String newUsername) {
        return jwtUtil.modifyClaim(cueentToken, key, "username", newUsername);
    }

    // JWT 토큰 업데이트 처리
    private void updateJwtToken(String currentToken, String newToken, Key tokenKey) {
    	
    	logger.info("MemberService.updateJwtToken()!");
    	
    	// 새로운 변경된 JWT 토큰 키 값 추가.
    	redisHandler.getValueOperations().set(
    			newToken,											// 새로운 JWT 문자열
    			jwtUtil.encodeKeyToBase64(tokenKey),	// 원래 이전 JWT 의 Key 재 사용 유지.
    			Duration.ofHours(1)); // 1시간을 새롭게 갱신
    	
    	logger.info("Token Refresh : " + redisHandler.getValueOperations().get(newToken) + "\n"
    			+ "old Token : " + (String) redisHandler.getValueOperations().get(currentToken));
    	
    	// 기존 JWT 토큰 키 및 값 삭제.(새로운 토큰 내 유효 시간을 설정 하려면 현재 토큰을 삭제하기 전에 수행해야 하므로 새로운 토큰 생성 및 저장 후 이전 JWT 삭제.)
    	redisHandler.getValueOperations().getAndDelete(currentToken);
    }


    /* JWT 를 가지고 redis 에서 key 문자열을 가져와서 실제 Key 객체로 반환 */
    private Key getKey(String jwtToken) {
    	
    	logger.info("MemberService.getKey()! " + jwtToken);
        
    	String encodedKey = (String) redisHandler.getValueOperations().get(jwtToken);
        byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedKey);
        
        return new SecretKeySpec(decodedBytes, "HmacSHA256");
    }
}
