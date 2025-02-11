package com.wherehouse;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

import com.wherehouse.redis.handler.RedisHandler;

import jakarta.annotation.PostConstruct;

@MapperScan("com.wherehouse.information.dao")
@SpringBootApplication
public class WherehouseApplication extends SpringBootServletInitializer{

	@Autowired
	RedisHandler redisHandler;
	
	@Override 
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) { 
		return application.sources(WherehouseApplication.class);
	}
	
	public static void main(String[] args) {
		SpringApplication.run(WherehouseApplication.class, args);
	}
	
	@PostConstruct
    public void clearRedisDataOnStartup() {
        // 모든 키 삭제
		redisHandler.clearCurrentRedisDB();
    }
}
