package com.wherehouse.setupunittest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.wherehouse.JWT.Filter.Util.JWTUtil;

@Configuration
public class BoardServiceUnitTestConfig {

	@Bean
    public JWTUtil jwtUtil() {
        return new JWTUtil();
    }
}
