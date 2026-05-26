package com.wherehouse.JWT.SecurityConfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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
import com.wherehouse.JWT.exceptionHandler.ApiAuthenticationEntryPoint;
import com.wherehouse.JWT.exceptionHandler.JwtAuthenticationFailureHandler;

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

    /* [Static Resource] : JS/CSS/이미지 등 정적 리소스는 Security Filter 적용 없이 바로 허용 */
    @Bean
    @Order(0)
    public SecurityFilterChain staticResourceFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/js/**", "/css/**", "/images/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
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

    /* [추천 서비스] : 선택적 인증 — JWT 있으면 검증·주입, 없으면 비인증 통과 (F005 설계 섹션 3.2) */
    @Bean
    public SecurityFilterChain recommendationServiceFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/recommendations/**")
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterAt(new JwtAuthProcessorFilter(cookieUtil, jwtUtil, env), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /* [매물 관리 서비스 - 뷰] : 매물 게시판·등록·수정 JSP 페이지. 선택적 인증 (F004 버튼 노출 제어용)
     *   iframe 내 로드를 위해 X-Frame-Options: SAMEORIGIN 설정 필수.
     *   Spring Security 기본값이 DENY 이므로 명시적으로 sameOrigin 지정. */
    @Bean
    public SecurityFilterChain propertyViewFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/properties/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .addFilterAt(new JwtAuthProcessorFilter(cookieUtil, jwtUtil, env), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /* [매물 관리 서비스 - API] : 등록·수정·상태변경(POST·PATCH)은 JWT 인증 필수, 조회(GET)는 비인증 허용 */
    @Bean
    public SecurityFilterChain propertyServiceFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1/properties/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/properties/**").authenticated()    // F001 등록
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/properties/**").authenticated()   // F002 수정, F003 상태변경
                        .requestMatchers(HttpMethod.GET, "/api/v1/properties/**").permitAll()          // F004 조회
                        .anyRequest().denyAll()
                )
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterAt(new JwtAuthProcessorFilter(cookieUtil, jwtUtil, env), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(new ApiAuthenticationEntryPoint())
                                .accessDeniedHandler(new JwtAccessDeniedHandler())
                );
        return http.build();
    }

    /* [리뷰 서비스 - 모든 요청] : 리뷰 작성/수정/삭제는 인증 필요, 조회는 비인증 허용 */
    @Bean
    public SecurityFilterChain reviewServiceFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1/reviews/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/reviews").authenticated()       // 리뷰 작성
                        .requestMatchers(HttpMethod.POST, "/api/v1/reviews/update").authenticated() // 리뷰 수정
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/reviews/**").authenticated()   // 리뷰 삭제
                        .anyRequest().permitAll()                                                    // 리뷰 조회 (GET)
                )
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterAt(new JwtAuthProcessorFilter(cookieUtil, jwtUtil, env), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(new ApiAuthenticationEntryPoint())
                                .accessDeniedHandler(new JwtAccessDeniedHandler())
                );
        return http.build();
    }

    /* [매물 방문 예약 서비스 - 회원 페이지] : 본인 자원 조회 페이지 4 종 (/visit/me/**).
     *
     *   /visit/me/reservations  — 탐색자 예약 현황
     *   /visit/me/subscriptions — 탐색자 구독 현황
     *   /visit/me/slots         — 등록자 슬롯 관리
     *   /visit/me/notifications — 방문 예약 알림
     *
     *   모든 경로는 JWT 인증 필수. 비인증 접근은 401 — 페이지 컨텍스트에서는 JS 가 로그인 페이지로 유도. */
    @Bean
    public SecurityFilterChain visitReservationPageFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/visit/me/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // iframe 내 로드를 위해 X-Frame-Options: SAMEORIGIN 설정 필수.
                // Spring Security 기본값이 DENY 이므로 명시적으로 sameOrigin 지정.
                // 부모 페이지 /wherehouse/main 의 iframe 에서 본 페이지 4종 (reservations/subscriptions/slots/notifications) 진입을 허용.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .addFilterAt(new JwtAuthProcessorFilter(cookieUtil, jwtUtil, env), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(new JwtAuthenticationFailureHandler())
                                .accessDeniedHandler(new JwtAccessDeniedHandler())
                );
        return http.build();
    }

    /* [매물 방문 예약 서비스 - API] : 방문 예약 기능 전체 경로 (설계 명세서 섹션 3.1).
     *
     *   GET /api/v1/visit/properties/{propertyId}/slots 는 비인증 허용 (선택적 인증).
     *   그 외 모든 메서드·경로는 JWT 인증 필수.
     *
     *   인증 정보 추출은 기존 JwtAuthProcessorFilter 를 재사용한다. 인증 실패는
     *   ApiAuthenticationEntryPoint 가 401 로 응답하고, 인가 실패는 JwtAccessDeniedHandler
     *   가 403 으로 응답한다. 비즈니스 예외 (E7xxx) 는 GlobalExceptionHandlerVisit 가 처리. */
    @Bean
    public SecurityFilterChain visitReservationServiceFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1/visit/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/visit/properties/*/slots").permitAll()  // F003 선택적 인증
                        .requestMatchers(HttpMethod.POST,   "/api/v1/visit/**").authenticated()           // 윈도우 공개, 슬롯 예약, 구독 신청
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/visit/**").authenticated()           // 윈도우 철회, 예약 취소, 구독 해제
                        .requestMatchers(HttpMethod.PATCH,  "/api/v1/visit/**").authenticated()           // 결과 분류, 알림 읽음
                        .requestMatchers(HttpMethod.GET,    "/api/v1/visit/**").authenticated()           // 그 외 GET 은 본인 자원 조회
                        .anyRequest().denyAll()
                )
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterAt(new JwtAuthProcessorFilter(cookieUtil, jwtUtil, env), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(new ApiAuthenticationEntryPoint())
                                .accessDeniedHandler(new JwtAccessDeniedHandler())
                );
        return http.build();
    }
}