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
import com.wherehouse.JWT.Repository.JwtTokenRepository;
import com.wherehouse.JWT.Repository.UserEntityRepository;
import com.wherehouse.JWT.UserDTO.JwtTokenEntity;
import com.wherehouse.JWT.UserDTO.UserEntity;
import com.wherehouse.members.dao.IMembersRepository;
import com.wherehouse.members.dao.MemberEntityRepository;
import com.wherehouse.members.model.MembersEntity;

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
    JwtTokenRepository jwtTokenRepository;

    @Autowired
    JWTUtil jwtUtil;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder; // 비밀번호 암호화를 위한 객체

    @Override
    public Map<String, String> validLogin(HttpServletRequest httpRequest) {
    	
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
        
        
        UserEntity userEntity = createUserEntity(httpRequest, roles);		// " "

        int result = membersRepository.editMember(memberEntity, userEntity);

        // 정상 적인 수정이 되었다면 변경된 컬럼이 있으니 새롭게 jwt 만들어서 db 내부 갱신하고 HttpResponse 객체 내 응답 중 쿠키 갱신
        if (result == 1) {
        	
            String currentToken = getCookieToken(httpRequest);
            Key currentTokenKey = getKey(currentToken);
            
            updateJwtToken(
            		currentToken,
            		createNewJwtToken(currentToken, currentTokenKey, httpRequest.getParameter("nickName")),
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

    // 기존 JWT 토큰을 사용해 새로운 JWT 토큰 생성
    private String createNewJwtToken(String cueentToken, Key key, String newUsername) {
        return jwtUtil.modifyUsername(cueentToken, key, newUsername);
    }

    // JWT 토큰 업데이트 처리
    private void updateJwtToken(String currentToken, String newToken, HttpServletResponse response) {
    	
    	String key = jwtUtil.encodeKeyToBase64(getKey(currentToken));
    	
        jwtTokenRepository.deleteById(currentToken);
        jwtTokenRepository.save(
        		JwtTokenEntity.builder()
                .jwttoken(newToken)
                .hmacSha256Key(key)
                .build());

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

    private Key getKey(String jwtToken) {
        String encodedKey = jwtTokenRepository.findById(jwtToken).get().getHmacSha256Key();
        byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedKey);
        return new SecretKeySpec(decodedBytes, "HmacSHA256");
    }
}
