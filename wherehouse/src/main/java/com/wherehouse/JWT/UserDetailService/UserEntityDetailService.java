package com.wherehouse.JWT.UserDetailService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.wherehouse.JWT.Repository.UserEntityRepository;
import com.wherehouse.JWT.UserDTO.UserEntity;
import com.wherehouse.JWT.UserDetails.UserEntityDetails;
import com.wherehouse.members.dao.MemberEntityRepository;


@Service
public class UserEntityDetailService implements UserDetailsService{

	@Autowired
	UserEntityRepository userRepository;
	
	@Autowired
	MemberEntityRepository memberEntityRepository;
	
	@Transactional
	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		
		System.out.println("UserEntityDetailService.loadUserByUsername()!");
		System.out.println("username : " + username);
		
		/* JPA 사용해서 가져오기 :
		 * 	1. 실제 회원 가입 때 필요한 테이블 정보와 JWT 토큰 발급 시 필요한 클레임 생성 시 필요한 항목이 다름.
		 *  2. 회원 관리 테이블과 실제 JWT 인증 요청인 로그인 시도 는 서로 다른 로직이므로 이를 분리하여 유지보수 하기 위함. */
		
		/* DBMS 내에서 사용자 인증 관련 기능을 하기 위한 테이블(membertbl 별도)에서 데이터를 가져옴. */
		UserEntity userEntity = userRepository.findByUsername(username)
									.orElseThrow(() -> new UsernameNotFoundException("UsernameNotFoundException"));
		
		//UserEntity membersEntity = membersRepository.setUsername(username);
		System.out.println("userEntity : \n" + 
							"\n " + userEntity.getUsername() +
							"\n " + userEntity.getUserid() +
							"\n " + userEntity.getPassword() +
							"\n " + userEntity.getRoles());
		
		return new UserEntityDetails(userEntity);
	}
}