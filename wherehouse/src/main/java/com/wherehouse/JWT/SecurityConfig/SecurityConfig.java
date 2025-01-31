package com.wherehouse.JWT.SecurityConfig;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.wherehouse.JWT.Filter.JWTAuthenticationFilter;
import com.wherehouse.JWT.Filter.JwtComponent;
import com.wherehouse.JWT.Filter.LoginFilter;
import com.wherehouse.JWT.Filter.RequestAuthenticationFilter;
import com.wherehouse.JWT.Filter.Util.CookieUtil;
import com.wherehouse.JWT.Filter.Util.JWTUtil;
import com.wherehouse.JWT.Provider.UserAuthenticationProvider;
import com.wherehouse.redis.handler.RedisHandler;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private AuthenticationConfiguration authenticationConfiguration;
    
    @Autowired
    CookieLogoutHandler cookieLogoutHandler;
    

   @Autowired
   RedisHandler redisHandler;

    @Bean
    public UserAuthenticationProvider userAuthenticationProvider() {
        return new UserAuthenticationProvider();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration,
            UserAuthenticationProvider customAuthenticationProvider) throws Exception {

        AuthenticationManager authenticationManager = configuration.getAuthenticationManager();
        ((ProviderManager) authenticationManager).getProviders().add(customAuthenticationProvider);
        return authenticationManager;
    }

    @Bean
    public JWTUtil loadJwtUtil() {
        return new JWTUtil();
    }
    
    @Bean
    CookieUtil cookieUtil() {
    	return new CookieUtil();
    }
    
    @Bean
    JwtComponent jwtComponent() {
    	return new JwtComponent();
    }
    
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DefaultSecurityFilterChain loginFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/login")
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterAt(new LoginFilter(authenticationManager(authenticationConfiguration, userAuthenticationProvider()), redisHandler, loadJwtUtil(), cookieUtil()),
                         UsernamePasswordAuthenticationFilter.class)
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
    
    /* 로그인 성공 페이지 요청 : 'apiFilterChain' 와 기능이 거의 동일하나 단순 로그인 관련 처리는 각 jsp 에서 담당하므로 별도의 필터 클래스로 작성 */
    @Bean
    public DefaultSecurityFilterChain loginSuccessFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/loginSuccess")
            .authorizeHttpRequests(auth -> auth
                .anyRequest().hasAuthority("ROLE_USER")
            )
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            /* 현재 요청들에 대해서는 로그인 폼이 아닌, 단순 JWT 전처리 및 시그니처 검증만 수행 하므로 대신 필터 체인 적용.  */
            .addFilterAt(new JWTAuthenticationFilter(cookieUtil(), jwtComponent()),
                         UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    
    /* Logout 요청 : 'HttpSecurity.logout' 를 설정해서 "LogoutFilter"가 별도의 필터 체인으로써 등록되어 실행된다. 그렇기 때문에
     * 별도로 설정을 한다. */
    @Bean
    public DefaultSecurityFilterChain LogOutFilterChain(HttpSecurity http, CookieLogoutHandler cookieLogoutHandler) throws Exception {
        http.securityMatcher("/logout")
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .logout(logout -> logout
                    // 로그아웃 요청을 처리할 URL 설정
                    .logoutUrl("/logout")
                    // 로그아웃 성공 시 리다이렉트할 URL 설정
                    .addLogoutHandler(cookieLogoutHandler)
                    .logoutSuccessUrl("/")
                    // 로그아웃 성공 핸들러 추가 (리다이렉션 처리)
            );
        return http.build();
    }
    
    /* ===== 여기서 부터는 로그인 / 로그 아웃 이 아닌 실제 서비스 이용 시 권한이 필요한 요청 경로에 대한 필터링 설정 ===== */
    
    /* 단순 게시판 띄우는 것들은 별도의 필터를 적용 받을 필요 없으므로 별도의 요청으로 빼 놓는 다. */
    @Bean
    public DefaultSecurityFilterChain boardListFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/list/**")
            .formLogin(formLogin -> formLogin.disable()) // 폼 로그인 비활성화
            .csrf(csrf -> csrf.disable())		// hostonly 쿠키 사용하기 때문에 별도의 설정 하지 않음.
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
            /* 현재 요청들에 대해서는 로그인 폼이 아닌, 단순 JWT 전처리 및 시그니처 검증만 수행 하므로 대신 필터 체인 적용.  */

        /* 페이지 로드 시 필요한 csp 설정 */
        http.headers(headers -> headers
                //.contentTypeOptions(contentTypeOptions -> contentTypeOptions.disable()) // MIME 타입 엄격 검사 : 설정 끄려면 disable(), 설정 유지하려면 삭제 가능
                // Content-Security-Policy 설정
                .contentSecurityPolicy(csp -> csp
                		.policyDirectives(
                                "default-src 'self'; " + // 기본 리소스는 현재 도메인에서만 로드
                                "script-src 'self' https://cdn.jsdelivr.net https://ajax.googleapis.com https://kit.fontawesome.com; " + // 스크립트 허용
                                "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://kit.fontawesome.com; " + // 스타일 허용
                                "img-src 'self' data:; " + // 이미지 허용 (data URL 포함)
                                "font-src 'self' https://kit.fontawesome.com https://ka-f.fontawesome.com https://cdn.jsdelivr.net/gh/projectnoonnu/noonfonts_2302_01@1.0/TheJamsil5Bold.woff2; " + // 폰트 허용
                                "connect-src 'self' https://ka-f.fontawesome.com https://cdn.jsdelivr.net; " + // 외부 API 호출 허용
                                "frame-ancestors 'self'; " + // iframe 클릭재킹 방지
                                "worker-src 'self'; " + // Worker 허용
                                "object-src 'none';" // 플러그인 차단
                            )
			    )
	//                .frameOptions(frameOptions -> frameOptions.sameOrigin()) // SAMEORIGIN으로 설정
            );
        return http.build();
    }
    
    /* 여기서 요청들은 모두 JWT 필요한, 즉 로그인이 되어 있는 상태여야만 함. */
    @Bean
    public DefaultSecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/writepage", "/boardwrite", "/modifypage", "/modify", "/delete/**", "/replyWrite")
        	.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated()
            )
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            /* 현재 요청들에 대해서는 로그인 폼이 아닌, 단순 JWT 전처리 및 시그니처 검증만 수행 하므로 대신 필터 체인 적용.  */
            .addFilterAt(new RequestAuthenticationFilter(cookieUtil(), jwtComponent()),
                         UsernamePasswordAuthenticationFilter.class);
        
        http.headers(headers -> headers
        		.contentSecurityPolicy(csp -> csp
        				.policyDirectives(
        						"frame-ancestors 'self'; " // ifrmae 내 클릭재킹 방지
        						)
        				)
              .frameOptions(frameOptions -> frameOptions.sameOrigin()) // SAMEORIGIN으로 설정
        		);
        return http.build();
    }    
}
