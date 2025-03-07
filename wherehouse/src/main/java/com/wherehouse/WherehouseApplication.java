package com.wherehouse;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@MapperScan("com.wherehouse.information.dao")
@SpringBootApplication
public class WherehouseApplication extends SpringBootServletInitializer{


	@Override 
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) { 
		return application.sources(WherehouseApplication.class);
	}
	
	public static void main(String[] args) {
		
	    SpringApplication.run(WherehouseApplication.class, args);
	}
	
	
}
