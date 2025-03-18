package com.wherehouse.TestInitializer;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import com.wherehouse.redis.handler.RedisHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class TestApplicationInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(TestApplicationInitializer.class);

    private final RedisHandler redisHandler;
    private final Environment environment;

    public TestApplicationInitializer(RedisHandler redisHandler, Environment environment) {
        this.redisHandler = redisHandler;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("TestApplicationInitializer.run() 시작");

        String jwtUser = environment.getProperty("TestJWT.user");
        String jwtSecretKey = environment.getProperty("TestJWT.secretKey");

        if (jwtUser == null || jwtSecretKey == null) {
            logger.warn("환경 변수 누락: TestJWT.user={}, TestJWT.secretKey={}", jwtUser, jwtSecretKey);
            return; // 환경 변수가 없으면 Redis에 저장하지 않음
        }

        logger.info(">>> TestJWT.user: {}", jwtUser);
        logger.info(">>> TestJWT.secretKey: {}", jwtSecretKey);

        try {
            if (redisHandler != null && redisHandler.getValueOperations() != null) {
                redisHandler.getValueOperations().set(jwtUser, jwtSecretKey);
                logger.info("Redis 저장 성공: key={}, value={}", jwtUser, jwtSecretKey);
            } else {
                logger.warn("RedisHandler 또는 ValueOperations가 null입니다.");
            }
        } catch (Exception e) {
            logger.error("Redis 저장 중 오류 발생", e);
        }
    }
}
