package com.wherehouse.members.service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.security.Key;

import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import com.wherehouse.JWT.Filter.Util.JWTUtil;
import com.wherehouse.JWT.Repository.UserEntityRepository;
import com.wherehouse.JWT.UserDTO.UserEntity;
import com.wherehouse.members.dao.IMembersRepository;
import com.wherehouse.members.dao.MemberEntityRepository;
import com.wherehouse.members.model.MembersEntity;
import com.wherehouse.redis.handler.RedisHandler;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class MemberService implements IMemberService {

    @Autowired
    IMembersRepository membersRepository;

    @Autowired
    MemberEntityRepository memberEntityRepository;

    @Autowired
    UserEntityRepository userEntityRepository;

    @Autowired
    RedisHandler redisHandler;
    
    @Autowired
    JWTUtil jwtUtil;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder; // 비밀번호 암호화를 위한 객체

    @Override
    public Map<String, String> validLogin(HttpServletRequest httpRequest) {
    	
    	System.out.println("MemberService.validLogin()!");
    	
        String Token = getCookieToken(httpRequest);
        Key key = getKey(Token);

        Map<String, String> loginSuccessInfo = new HashMap<>();
        
        loginSuccessInfo.put("userId", jwtUtil.extractUserId(Token, key));
        loginSuccessInfo.put("userName", jwtUtil.extractUsername(Token, key));

        return loginSuccessInfo;
    }

    @Override
    public int validJoin(HttpServletRequest httpRequest) {
    	
    	
        if (memberEntityRepository.findById(httpRequest.getParameter("id")).isPresent()) {
            return 1;
        } else if (memberEntityRepository.findByNickName(httpRequest.getParameter("nickName")).isPresent()) {
            return 2;
        } else {
        	
        	List<String> roles = new ArrayList<String>(); 
        	roles.add("ROLE_USER");
        	
            MembersEntity membersEntity = createMembersEntity(httpRequest);
            UserEntity userEntity = createUserEntity(
            		httpRequest,
            		roles);
            return membersRepository.addMember(membersEntity, userEntity);
        }
    }

    @Override
    public MembersEntity searchEditMember(HttpServletRequest httpRequest) {
        return membersRepository.getMember(httpRequest.getParameter("editid"));
    }

    @Override
    public int editMember(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    	
    	 // 데이터 준비
        List<String> roles = userEntityRepository.findById(httpRequest.getParameter("id"))
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getRoles();
    	
        MembersEntity memberEntity = createMembersEntity(httpRequest);		// 전달 받은 회원 수정 요청 데이터를 가지고 회원 VO(Entity) 객체 만듬.
        UserEntity userEntity = createUserEntity(httpRequest, roles);		// 전달 받은 회원 수정 요청 데이터를 가지고 "memberEntity" 에 대한 인증 용 객체.

        int result = membersRepository.editMember(memberEntity, userEntity);	// 실제 회원 정보 담고 있는 DBMS 테이블 수정.

        // 정상 적인 수정이 되었다면 변경된 컬럼이 있으니 새롭게 jwt 만들어서 db 내부 갱신하고 HttpResponse 객체 내 응답 중 쿠키 갱신
        if (result == 1) {
        	
            String currentToken = getCookieToken(httpRequest);
            Key currentTokenKey = getKey(currentToken);
            
            /* 회원 정보가 바뀌었으니 그에 따라 새로운 토큰을 만들어서 갱신 작업으로 이전 토큰을 redis 에서 삭제 및 새로운 토큰으로 저장. */
            updateJwtToken(
            		currentToken,	// redis 에서 삭제할 이전 토큰
            		createNewJwtToken(currentToken, currentTokenKey, httpRequest.getParameter("nickName")),	 // redis 내 갱신할 새로운 토큰
            		httpResponse
            );
        }
        
        return result;
    }

    /* == 공통 메소드들 == */
    // 공통 엔티티 생성 메서드
    private MembersEntity createMembersEntity(HttpServletRequest httpRequest) {
        return MembersEntity.builder()
                .id(httpRequest.getParameter("id"))
                .pw(httpRequest.getParameter("pw"))
                .nickName(httpRequest.getParameter("nickName"))
                .tel(httpRequest.getParameter("tel"))
                .email(httpRequest.getParameter("email"))
                .joinDate(new java.sql.Date(new Date().getTime()))
                .build();
    }

    private UserEntity createUserEntity(HttpServletRequest httpRequest, List<String> roles) {
        return UserEntity.builder()
                .userid(httpRequest.getParameter("id"))
                .username(httpRequest.getParameter("nickName"))
                .password(passwordEncoder.encode(httpRequest.getParameter("pw")))
                .roles(roles)
                .build();
    }

    // 기존 JWT 토큰에 대해서 변경된 클레임을 수정해서 새로운 JWT 토큰으로 생성
    private String createNewJwtToken(String cueentToken, Key key, String newUsername) {
        return jwtUtil.modifyUsername(cueentToken, key, newUsername);
    }

    // JWT 토큰 업데이트 처리
    private void updateJwtToken(String currentToken, String newToken, HttpServletResponse response) {
    	
    	System.out.println("\nMemberService.updateJwtToken()!");
    	
    	// 새로운 변경된 JWT 토큰 키 값 추가.
    	redisHandler.getValueOperations().set(
    			newToken,											// 새로운 JWT 문자열
    			jwtUtil.encodeKeyToBase64(getKey(currentToken)),	// 원래 이전 JWT 의 Key 유지.
    			jwtUtil.getRemainingDuration(currentToken, getKey(currentToken)));	// 기존 JWT 유효 시간을 "Duration" 객체로써 반환.
    	
    	System.out.println("newToken : " + newToken +
    						"\n " + (String) redisHandler.getValueOperations().get(newToken));	
    	System.out.println("currentToken : " + currentToken +
				"\n " + (String) redisHandler.getValueOperations().get(currentToken));
    	
    	// 기존 JWT 토큰 키 및 값 삭제.(새로운 토큰 내 유효 시간을 설정 하려면 현재 토큰을 삭제하기 전에 수행해야 하므로 새로운 토큰 생성 및 저장 후 이전 JWT 삭제.)
    	redisHandler.getValueOperations().getAndDelete(currentToken);	
    	
        Cookie cookie = new Cookie("Authorization", newToken);
        cookie.setSecure(false); // HTTPS 환경에서만 작동
        cookie.setHttpOnly(true); // JavaScript 접근 불가
        cookie.setPath("/");
        response.addCookie(cookie);
    }

    private String getCookieToken(HttpServletRequest httpRequest) {
        Cookie[] cookies = httpRequest.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("Authorization".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /* JWT 를 가지고 redis 에서 key 문자열을 가져와서 실제 Key 객체로 반환 */
    private Key getKey(String jwtToken) {
    	
    	System.out.println("MemberService.getKey()!");
        
    	String encodedKey = (String) redisHandler.getValueOperations().get(jwtToken);
        byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedKey);
        
        return new SecretKeySpec(decodedBytes, "HmacSHA256");
    }
}
