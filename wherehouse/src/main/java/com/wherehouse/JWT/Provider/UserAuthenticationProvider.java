package com.wherehouse.JWT.Provider;

import java.util.Optional;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.wherehouse.JWT.Repository.UserEntityRepository;
import com.wherehouse.JWT.UserDTO.UserEntity;
import com.wherehouse.JWT.UserDetailService.UserEntityDetailService;
import com.wherehouse.JWT.UserDetails.UserEntityDetails;

public class UserAuthenticationProvider implements AuthenticationProvider{
	
	private final UserEntityDetailService userEntityDetailService;
	private final UserEntityRepository userEntityRepository;
	private final BCryptPasswordEncoder passwordEncoder;
	
	public UserAuthenticationProvider(
			
			UserEntityDetailService userEntityDetailService,
			UserEntityRepository userEntityRepository,
			BCryptPasswordEncoder passwordEncoder
			) {
		
		this.userEntityDetailService = userEntityDetailService;
		this.passwordEncoder = passwordEncoder;
		this.userEntityRepository = userEntityRepository;
	}
	
	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		
		System.out.println("UserAuthenticationProvider.authenticate() 실행!");
		
		String userId = (String) authentication.getPrincipal() ;		// interface Principal
		String password = authentication.getCredentials().toString();	// interface Authentication
			
		String username = "";
		Optional<UserEntity> userEntity = userEntityRepository.findByUserid(userId);
		
		if(userEntity.isPresent())
			username = userEntity.get().getUsername();
		
		System.out.println("username : " + username);
		
		UserDetails userDetails = new UserEntityDetails(new UserEntity());
		userDetails = userEntityDetailService.loadUserByUsername(username);	// UserEntity
			
		
		/** 
		 * if 문 내부: 인증 수행 과정에서, DB에 암호화되어 저장된 비밀번호와 비교하여 인증 결과로 객체를 반환한다. 이때 필요한 값들을 인증 객체에 설정한다.
		 * 
		 * 인증 성공 시 설정하는 값들은 아래와 같다. (principal, credentials 은 이미 필터에서 설정 했으니 제외)
		 *
		 * 1. AbstractAuthenticationToken.setAuthenticated(boolean): 
		 *    - `AuthenticationProvider`에서 `UserDetailsService`를 사용하여 DB에서 사용자 정보를 조회한 후
		 *      인증이 성공하면 `setAuthenticated(true)`로 설정.
		 *    - 이 값은 `AuthenticationManager.authenticate()`에서 반환되는 인증 객체의 `isAuthenticated()`를 통해 
		 *      필터에서 확인하고, 인증 성공 여부에 따라 다음 메서드(`successfulAuthentication` 또는 `unsuccessfulAuthentication`)로 분기.
		 *
		 * 2. AbstractAuthenticationToken.Collection<GrantedAuthority> authorities:
		 *    - `UserDetailsService`에서 가져온 `UserDetails`의 권한 목록을 `Authentication` 객체의 `authorities`에 저장.
		 *
		 * 3. AbstractAuthenticationToken.Object details:
		 *    - 인증에 필요한 부가 정보를 저장 (예: 클라이언트 IP).
		 *
		 * 	이후 인증 객체(Token)는 클라이언트 측의 공격을 방지하기 위해 인증 객체 자체는 불변성으로써 설계되므로
		 * 	새로운 Token을 생성해서 반환한다.
		 *  반환함으로써 현재 호출한 지점인 "AbstractAuthenticationToken.authenticated()" 으로 반환하고 인증 매니저가 해당 인증 객체인 Token(AbstractAuthenticationToken.isAuthenticated()) 내
		 * 	boolean authenticated 을 확인 해서 필터 내부적으로 다음 실행할 메소드를 결정한다.
		 */

		/*
		 	public UsernamePasswordAuthenticationToken(Object principal, Object credentials,
				Collection<? extends GrantedAuthority> authorities) { ... }
		 * */
		if(passwordEncoder.matches(password, userDetails.getPassword())) {
			
			System.out.println("올바른 사용자 확인!");
			System.out.println("userDetails.getAuthorities() : " + userDetails.getAuthorities());
			/* 반환 될 토큰 내 super.setAuthenticated(true) 및 권한(AbstractAuthenticationToken.authorities)을
			   설정 후 반환.
			*/
			UsernamePasswordAuthenticationToken returnAuthenticationToken = 
					new UsernamePasswordAuthenticationToken(
					username,
					password,
					userDetails.getAuthorities()
					// super(authorities);
					// super.setAuthenticated(true); // must use super, as we override
			);
			
			/* "@GetMapping("/loginSuccess")" 등에서 jsp 랜더링 시 필요한 정보이므로 jwt 토큰 내 삽입할 정보.
			 * 생성자로써 초기화하는 Token 정보(사용자 이름, 자격증명(비번), 권한((Collection<? extends GrantedAuthority>) 외
			 * 별도의 정상적인 필터 검증 후 JWT 클레임 내 포함될 id 값을 넣는 다. */
			returnAuthenticationToken.setDetails(((UserEntityDetails) userDetails).getuserId());	
			
			return returnAuthenticationToken;
			
		} else {
			
			System.out.println("올바른 사용자가 아님!");
			
			/* 그냥 fail 발생 시킴. */
			UsernamePasswordAuthenticationToken returnAuthenticationToken =
					new UsernamePasswordAuthenticationToken(
						username,
						password
					);
			
			System.out.println("returnAuthenticationToken.isAuthenticated() : " + returnAuthenticationToken.isAuthenticated());
			throw new BadCredentialsException("BadCredentialsExceptoin"); // 인증 실패 시 예외 발생
		}
	}

	/* SpringFilterChain 내 이미 폼로그인을 막아 둠으로써  */
	@Override
	public boolean supports(Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}

}
 