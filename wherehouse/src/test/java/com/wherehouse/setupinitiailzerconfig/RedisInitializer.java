package com.wherehouse.setupinitiailzerconfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.wherehouse.redis.handler.RedisHandler;

@Component
public class RedisInitializer implements ApplicationRunner {

    @Autowired
    private RedisHandler redisHandler;

    @Autowired
    private Environment env;

    private static final Logger logger = LoggerFactory.getLogger(RedisInitializer.class);

    @Override
    public void run(ApplicationArguments args)  throws Exception{
        logger.info(" RedisInitializer 실행 중...");

        String token = env.getProperty("TestJWT.token");
        String secretKey = env.getProperty("TestJWT.secretKey");

        redisHandler.getValueOperations().set(token, secretKey);

        logger.info(" Redis 초기화 완료: token={}, secretKey: {}", token, redisHandler.getValueOperations().get(token));
    }
}
