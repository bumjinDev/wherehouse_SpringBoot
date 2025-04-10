package com.wherehouse.JWT.SecurityConfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.wherehouse.JWT.Filter.JwtAuthProcessorFilter;
import com.wherehouse.JWT.Filter.LoginFilter;
import com.wherehouse.JWT.Filter.Util.CookieUtil;
import com.wherehouse.JWT.Filter.Util.JWTUtil;
import com.wherehouse.JWT.Provider.UserAuthenticationProvider;
import com.wherehouse.JWT.Repository.UserEntityRepository;
import com.wherehouse.JWT.UserDetailService.UserEntityDetailService;
import com.wherehouse.JWT.exceptionHandler.JwtAccessDeniedHandler;
import com.wherehouse.JWT.exceptionHandler.JwtAuthenticationFailureHandler;
import com.wherehouse.redis.handler.RedisHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AuthenticationConfiguration authenticationConfiguration;
    private final UserEntityDetailService userEntityDetailService;
    private final UserEntityRepository userEntityRepository;
    private final JWTUtil jwtUtil;
    private final CookieUtil cookieUtil;
    private final RedisHandler redisHandler;

    public SecurityConfig(
    		
        AuthenticationConfiguration authenticationConfiguration,
        UserEntityDetailService userEntityDetailService,
        UserEntityRepository userEntityRepository,
        JWTUtil jwtUtil,
        CookieUtil cookieUtil,
        RedisHandler redisHandler
    ) {
        this.authenticationConfiguration = authenticationConfiguration;
        this.userEntityDetailService = userEntityDetailService;
        this.userEntityRepository = userEntityRepository;
        this.jwtUtil = jwtUtil;
        this.cookieUtil = cookieUtil;
        this.redisHandler = redisHandler;
    }

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {    
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserAuthenticationProvider userAuthenticationProvider() {
        return new UserAuthenticationProvider(
            userEntityDetailService,
            userEntityRepository,
            bCryptPasswordEncoder()
        );
    }
    @Bean
    public AuthenticationManager authenticationManager() throws Exception {
        AuthenticationManager authenticationManager = authenticationConfiguration.getAuthenticationManager();
        ((ProviderManager) authenticationManager).getProviders().add(userAuthenticationProvider());
        return authenticationManager;
    }
    
    /* [회원관리 서비스 - 로그인] : 로그인 요청 처리를 'UsernamePasswordAuthenticationFilter' 가 처리 */
    @Bean
    public SecurityFilterChain loginFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/login")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterAt(new LoginFilter(authenticationManager(), redisHandler, jwtUtil), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    /* [회원관리 서비스 - 로그 아웃] : Spring MVC Controller 아닌 Spring Security Handler 적용. */
    @Bean
    public SecurityFilterChain logoutFilterChain(HttpSecurity http, CookieLogoutHandler cookieLogoutHandler) throws Exception {
        http.securityMatcher("/logout")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .logout(logout -> logout
                .logoutUrl("/logout")
                .addLogoutHandler(cookieLogoutHandler)
                .logoutSuccessUrl("/")
            );

        return http.build();
    }
    /* [회원관리 서비스 - 모든 요청] : Spring MVC Controller 아닌 Spring Security Handler 적용. */
    @Bean
    public SecurityFilterChain membersServiceFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/members/**")
        .authorizeHttpRequests(auth -> auth
				.requestMatchers("/members/loginSuccess").authenticated()	// 로그인 성공('loginFilterChain') 또는 메인 페이지(index.jsp) 로부터 요청 받아 'members/loginSucess.jsp' 요청
																			// .hasAuthority("ROLE_USER") : 별도로 구분된 권한 필요 없음.
				/* /members/edit :
				 * 	- GET  : 회원 수정 페이지 요청, 로그인 상태임을 검증이 되어야지만 회원 수정 페이지 요청 가능
				 * 	- POST : 회원 수정 요청, 로그인 상태임을 검증이 되어야지만 회원 수정 요청 가능
				 * 굳이 메소드 별 나눌 필요 없으나 가독성 위한 작성
				*/
				
				.requestMatchers(HttpMethod.POST,  "/members/edit").authenticated()
				.requestMatchers(HttpMethod.DELETE,  "/members/edit").authenticated()

				/* /members/join :
				 * 	- GET  : 회원 가입 페이지 요청, 필터 적용하지 않음.
				 * 	- POST : 회원 수정 페이지로부터의 실제 회원 가입 요청, 인가 검증 필요하므로 필터 적용
				*/
				
//				.requestMatchers(HttpMethod.GET,  "/members/join").authenticated()
				.anyRequest().permitAll()
    		)
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterAt(new JwtAuthProcessorFilter(cookieUtil, jwtUtil, redisHandler), UsernamePasswordAuthenticationFilter.class)
	        .exceptionHandling(exception ->
		        exception.authenticationEntryPoint(new JwtAuthenticationFailureHandler()) // JWT 인증 실패 시 실행될 핸들러 등록
		        		 .accessDeniedHandler(new JwtAccessDeniedHandler()));               // 인가 실패 처리
        return http.build();
    }
//    /* [게시글 서비스 - 게시글 목록 페이지] : 별도의 권한이 필요 없으나 CSP 정책 설정 목적의 필터 체인 설정 */
//    @Bean
//    public SecurityFilterChain boardServicePageListFilterChain(HttpSecurity http) throws Exception {
//        http.securityMatcher("/boards/page/{id}")
//            .formLogin(formLogin -> formLogin.disable()) // 폼 로그인 비활성화
//            .csrf(csrf -> csrf.disable()) // CSRF 비활성화
//            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
//
//        // CSP(Content Security Policy) 설정 업데이트
//        http.headers(headers -> headers
//    		.contentSecurityPolicy(csp -> csp
//    	            .policyDirectives(
//    	                "default-src 'self'; " +
//    	                "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://ajax.googleapis.com https://kit.fontawesome.com; " +
//    	                "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com; " +
//    	                "font-src 'self' https://fonts.gstatic.com https://cdn.jsdelivr.net https://kit.fontawesome.com https://ka-f.fontawesome.com; " +
//    	                "img-src 'self' data:; " +
//    	                "connect-src 'self' https://ka-f.fontawesome.com https://cdn.jsdelivr.net; " +
//    	                "frame-ancestors 'self'; " +
//    	                "worker-src 'self'; " +
//    	                "object-src 'none';"
//    	            )
//    	     )
//        );
//        return http.build();
//    }
    /* [게시글 서비스 - 모든 요청] : 각 요청 경로 및 HTTP Method 별 권한 필요한 위치 설정 */
    @Bean
    public SecurityFilterChain boardServcieFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/boards/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(            
                    "/boards/new",            // 게시글 작성 페이지 (GET /boards/new)
                    "/boards/",               // 게시글 작성 요청 (POST /boards)
                    "/boards/*/auth",         // 인가 요청 (GET /boards/{id}/auth)
                    "/boards/*/edit",         // 게시글 수정 페이지 요청 (GET /boards/edit?boardId=)
                    "/boards/comments"        // 댓글 작성 (POST /boards/comments)
                ).authenticated()
                
                /* /boards/{id} :
                 *  - POST : 글 수정
                 *  - DELETE : 글 삭제
                 *  - GET : 글 단순 선택 조회, GET 요청은 포함되면 안되므로 HTTP Method 별 정책 세분화. 
                 */
                .requestMatchers(HttpMethod.POST, "/boards/{id}").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/boards/{id}").authenticated()

                .anyRequest().permitAll()  // hasAuthority("ROLE_USER") : 별도의 인가 권한 구분 필요하지 않음.
            )
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterAt(new JwtAuthProcessorFilter(cookieUtil, jwtUtil, redisHandler), UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(exception ->
                exception.authenticationEntryPoint(new JwtAuthenticationFailureHandler())  // JWT 인증 실패 시 실행될 핸들러 등록
                         .accessDeniedHandler(new JwtAccessDeniedHandler())                // 인가 실패 처리
        );
        // CSP(Content Security Policy) 설정 업데이트
        http.headers(headers -> headers
		.contentSecurityPolicy(csp -> csp
	            .policyDirectives(
	                "default-src 'self'; " +
	                "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://ajax.googleapis.com https://kit.fontawesome.com; " +
	                "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com; " +
	                "font-src 'self' https://fonts.gstatic.com https://cdn.jsdelivr.net https://kit.fontawesome.com https://ka-f.fontawesome.com; " +
	                "img-src 'self' data:; " +
	                "connect-src 'self' https://ka-f.fontawesome.com https://cdn.jsdelivr.net; " +
	                "frame-ancestors 'self'; " +
	                "worker-src 'self'; " +
	                "object-src 'none';"
	            )
	    ));
        return http.build();
    } 
}
