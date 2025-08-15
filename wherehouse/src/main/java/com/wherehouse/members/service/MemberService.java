package com.wherehouse.members.service;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import com.wherehouse.members.exception.JwtKeyNotFoundException;
import com.wherehouse.members.exception.MemberNotFoundException;
import com.wherehouse.members.exception.NicknameAlreadyExistsException;
import com.wherehouse.members.exception.UserIdAlreadyExistsException;
import com.wherehouse.members.model.MemberConverter;
import com.wherehouse.members.model.MemberDTO;
import com.wherehouse.members.model.MembersEntity;
import com.wherehouse.redis.handler.RedisHandler;

import org.slf4j.Logger;

/**
 * MemberService
 *
 * 회원 관련 핵심 비즈니스 로직을 담당하는 서비스 클래스.
 * 회원 가입, 정보 수정, JWT 갱신 등 주요 기능을 제공하며,
 * 인증 정보와 회원 정보를 동시에 관리한다.
 *
 * 기술 구성:
 * - JWT 기반 인증 구조 (Redis 연동)
 * - 회원 테이블 + Spring Security 인증 테이블 동시 갱신
 * - 사용자 정의 예외를 통한 예외 흐름 제어
 */
@Service
public class MemberService implements IMemberService {

    private static final Logger logger = LoggerFactory.getLogger(MemberService.class);

    private final IMembersRepository membersRepository;
    private final MemberEntityRepository memberEntityRepository;
    private final UserEntityRepository userEntityRepository;
    private final JWTUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final MemberConverter memberConverter;
    private final AuthenticationEntityConverter authenticationEntityConverter;

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
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.memberConverter = memberConverter;
        this.authenticationEntityConverter = authenticationEntityConverter;
    }

    /**
     * 로그인 성공 시 JWT에서 사용자 ID와 닉네임을 추출하여 반환.
     * 인증 필터 이후 호출되며, Redis에서 서명 키를 복원한 뒤 클레임을 파싱한다.
     *
     * @param jwt 클라이언트로부터 전달받은 JWT 토큰
     * @return 사용자 ID 및 닉네임을 포함한 Map
     */
    @Override
    public Map<String, String> validLoginSuccess(String jwt) {
        logger.info("MemberService.validLogin()");

        Map<String, String> loginSuccessInfo = new HashMap<>();
        
        loginSuccessInfo.put("userId", jwtUtil.extractUserId(jwt));
        loginSuccessInfo.put("userName", jwtUtil.extractUsername(jwt));

        return loginSuccessInfo;
    }

    /**
     * 회원 가입 요청 처리
     * ID 및 닉네임 중복 검증, 비밀번호 암호화 후
     * 회원 테이블과 인증 테이블에 사용자 정보 등록.
     *
     * @param memberDTO 클라이언트로부터 전달받은 가입 요청 데이터
     * @throws UserIdAlreadyExistsException 아이디 중복
     * @throws NicknameAlreadyExistsException 닉네임 중복
     */
    @Override
    public void validJoin(MemberDTO memberDTO) {
        logger.info("MemberService.validJoin()");

        if (memberEntityRepository.findById(memberDTO.getId()).isPresent()) {
            throw new UserIdAlreadyExistsException("이미 사용 중인 아이디입니다.");
        }
        if (memberEntityRepository.findByNickName(memberDTO.getNickName()).isPresent()) {
            throw new NicknameAlreadyExistsException("이미 사용 중인 닉네임입니다.");
        }

        memberDTO.setPw(passwordEncoder.encode(memberDTO.getPw()));
        memberDTO.setJoinDate(new Date(System.currentTimeMillis()));

        membersRepository.addMember(
            memberConverter.toEntity(memberDTO),
            authenticationEntityConverter.toEntity(memberDTO, List.of("ROLE_USER"))
        );
    }

    /**
     * 회원 정보 수정 페이지 요청 시, 사용자의 정보를 조회하여 DTO로 반환
     *
     * @param editId 조회 대상 사용자 ID
     * @return 사용자 정보 DTO
     * @throws MemberNotFoundException 존재하지 않는 사용자 ID
     * 
     * JPA.update
     */
    @Override
    public MemberDTO searchEditMember(String editId) {
    	
        return memberConverter.toDTO(membersRepository.getMember(editId)
                						.orElseThrow(() -> new MemberNotFoundException("현재 존재하지 않는 사용자 ID 입니다")));
    }

    /**
     * 회원 정보 수정 처리
     * 비밀번호 암호화, 닉네임 중복 검사, 테이블 갱신 및 JWT 토큰 재발급 포함.
     *
     * @param currentToken 기존 JWT 토큰
     * @param memberDTO 수정 요청된 회원 정보
     * @return 새롭게 갱신된 JWT 토큰
     * @throws MemberNotFoundException 사용자 ID 존재하지 않을 경우
     * @throws NicknameAlreadyExistsException 닉네임 중복 시
     */
    @Override
    public String editMember(String currentToken, MemberDTO memberDTO) {
    	
        logger.info("MemberService.editMember()");

        List<String> roles = userEntityRepository.findById(memberDTO.getId())
            .orElseThrow(() -> new MemberNotFoundException("현재 존재하지 않는 사용자 ID 입니다"))
            .getRoles();

        memberDTO.setPw(passwordEncoder.encode(memberDTO.getPw()));
        memberDTO.setJoinDate(new Date(System.currentTimeMillis()));

        Optional<MembersEntity> membersEntity = membersRepository.isNEditickNameAllowed(
            memberConverter.toEntity(memberDTO),
            authenticationEntityConverter.toEntity(memberDTO, roles)  
        );

        if(membersEntity.isPresent())
        	throw new NicknameAlreadyExistsException("닉네임이 중복되어 수정할 수 없습니다.");

        membersRepository.editMember(
            memberConverter.toEntity(memberDTO),
            authenticationEntityConverter.toEntity(memberDTO, roles)
        );

        return editToken(currentToken, "username", memberDTO.getNickName());
    }

    // ======== 내부 헬퍼 메서드 ========

    /**
     * 기존 JWT 토큰의 특정 클레임을 수정하여 새로운 토큰 반환
     *
     * @param currentToken 기존 JWT 문자열
     * @param claimName 수정할 클레임 이름
     * @param newUsername 새 닉네임
     * @return 수정된 JWT 토큰 문자열
     */
    private String editToken(String currentToken, String claimName, String newUsername) {
        return jwtUtil.modifyClaim(currentToken, claimName, newUsername);
    }
    }