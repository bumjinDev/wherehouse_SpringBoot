package com.wherehouse;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.wherehouse.information.dao")
@SpringBootApplication
public class WherehouseApplication {

	public static void main(String[] args) {
		SpringApplication.run(WherehouseApplication.class, args);
	}

}
