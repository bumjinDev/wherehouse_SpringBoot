package com.wherehouse.JWT.SecurityConfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.wherehouse.JWT.Filter.JWTAuthenticationFilter;
import com.wherehouse.JWT.Filter.LoginFilter;
import com.wherehouse.JWT.Filter.RequestAuthenticationFilter;
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
    @Bean
    public SecurityFilterChain loginFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/login")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterAt(new LoginFilter(authenticationManager(), redisHandler, jwtUtil), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
    @Bean
    public SecurityFilterChain loginSuccessFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/loginSuccess")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth
            		-> auth.anyRequest().hasAuthority("ROLE_USER"))
            .addFilterAt(new JWTAuthenticationFilter(cookieUtil, jwtUtil), UsernamePasswordAuthenticationFilter.class)
	        .exceptionHandling(exception ->
		        exception.authenticationEntryPoint(new JwtAuthenticationFailureHandler()) // JWT 인증 실패 시 실행될 핸들러 등록
		        		 .accessDeniedHandler(new JwtAccessDeniedHandler()));               // 인가 실패 처리
        return http.build();
    }
    @Bean
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/writepage", "/boardwrite", "/modifypage", "/modify", "/delete/**", "/replyWrite")
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
            		.anyRequest().authenticated())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterAt(new RequestAuthenticationFilter(cookieUtil, jwtUtil), UsernamePasswordAuthenticationFilter.class)	   /* 인증 예외와 인가 예외 중 "인증 예외" 에 대한 핸들러 클래스(AuthenticationEntryPoint 인터페이스 구현) */
            .exceptionHandling(exception ->
                exception.authenticationEntryPoint(new JwtAuthenticationFailureHandler()) // JWT 인증 실패 시 실행될 핸들러 등록
                		 .accessDeniedHandler(new JwtAccessDeniedHandler())               // 인가 실패 처리
            );
        http.headers(headers -> headers
        		.contentSecurityPolicy(csp -> csp
        				.policyDirectives(
        						"frame-ancestors 'self'; ")) // ifrmae 내 클릭재킹 방지
        		.frameOptions(frameOptions -> frameOptions.sameOrigin())); // SAMEORIGIN으로 설정  
        return http.build();
    }
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

    /* 게시판 목록 보는 화면은 권한이 필요 없으나 csp 정책으로 인한 필터 체인 설정 */
    @Bean
    public SecurityFilterChain boardListFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/list/**")
            .formLogin(formLogin -> formLogin.disable()) // 폼 로그인 비활성화
            .csrf(csrf -> csrf.disable()) // CSRF 비활성화
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

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
            )
        );

        return http.build();
    }
}
