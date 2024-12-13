package com.wherehouse.configuration;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.wherehouse.JWT.Repository.JwtTokenRepository;

@Component
public class InitService {

	@Autowired
	JwtTokenRepository jwtTokenRepository;
	
    @PostConstruct
    public void init() {
    
        jwtTokenRepository.deleteAll();
    }
}
