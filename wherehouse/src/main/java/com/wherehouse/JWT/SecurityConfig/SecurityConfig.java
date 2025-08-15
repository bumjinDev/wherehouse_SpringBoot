package com.wherehouse.JWT.SecurityConfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
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
    private final Environment env;

    public SecurityConfig(
    		
        AuthenticationConfiguration authenticationConfiguration,
        UserEntityDetailService userEntityDetailService,
        UserEntityRepository userEntityRepository,
        JWTUtil jwtUtil,
        CookieUtil cookieUtil,
        Environment env
    ) {
        this.authenticationConfiguration = authenticationConfiguration;
        this.userEntityDetailService = userEntityDetailService;
        this.userEntityRepository = userEntityRepository;
        this.jwtUtil = jwtUtil;
        this.cookieUtil = cookieUtil;
        this.env = env;
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
    
    /* [회원관리 서비스 - 로그인] : 로그인 요청 처리를 처리 */
    @Bean
    public SecurityFilterChain loginFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/login")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterAt(new LoginFilter(authenticationManager(), jwtUtil), UsernamePasswordAuthenticationFilter.class);
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
            );  return http.build();
    }
    /* [회원관리 서비스 - 모든 요청] : Spring MVC Controller 아닌 Spring Security Handler 적용. */
    @Bean
    public SecurityFilterChain membersServiceFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/members/**")
        .authorizeHttpRequests(auth -> auth
				.requestMatchers("/members/loginSuccess").authenticated()
				.requestMatchers(HttpMethod.POST,  "/members/edit").authenticated()
				.requestMatchers(HttpMethod.DELETE,  "/members/edit").authenticated()
				.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterAt(new JwtAuthProcessorFilter(cookieUtil, jwtUtil, env), UsernamePasswordAuthenticationFilter.class)
	        .exceptionHandling(exception ->
		        exception.authenticationEntryPoint(new JwtAuthenticationFailureHandler()) // JWT 인증 실패 시 실행될 핸들러 등록
		        		 .accessDeniedHandler(new JwtAccessDeniedHandler()));               // 인가 실패 처리
        return http.build();
    }
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
                .requestMatchers(HttpMethod.POST, "/boards/{id}").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/boards/{id}").authenticated()
                .anyRequest().permitAll()
            )
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterAt(new JwtAuthProcessorFilter(cookieUtil, jwtUtil, env), UsernamePasswordAuthenticationFilter.class)
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
	    ));  return http.build();
    } 
}
