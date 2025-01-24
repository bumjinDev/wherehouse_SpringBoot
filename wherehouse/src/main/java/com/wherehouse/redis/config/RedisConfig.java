package com.wherehouse.redis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 환경 설정
 *
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    /**
     * Redis 연결을 위한 'Connection' 생성한다.
     *
     * @return RedisConnectionFactory
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
    	System.out.println("host : " + host + ", port : " + port);
        return new LettuceConnectionFactory(host, port);
    }

    /**
     * 해당 구성된 RedisTemplate을 통해서 데이터 통신으로 처리되는 대한 직렬화를 수행하기 위한 직렬화 설정,
     * 즉 

     * @return RedisTemplate<String, Object>
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
    	
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();

        // Spring Boot 에서는 "redisTemplate" 라는 클라이언트 프로그램 통해 Redis 서버와 연결해서 작업을 하는데, 그 연결 정보를 생성하는 팩토리 인스턴스를 내부에 설정 값으로 지정.
        redisTemplate.setConnectionFactory(redisConnectionFactory());

        /* "redisTemplate" 를 사용해서 특정 자료구조 형태로 저장 요청을 할 때 문자열 데이터를 UTF-8 형식의 byte 배열로 변환하여 저장하고, byte 배열을 다시 문자열로 변환해준다.
        	이후 redis 서버가 이를 받은 후 내부적으로 바이트 형태로 저장하는 것이다.
         */
        
        // Key-Value 형태로 직렬화를 수행
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());

        // Hash Key-Value 형태로 직렬화를 수행
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new StringRedisSerializer());

        // 위의 지정한 데이터 셋 저장 형태외의 다른 형태는 기본적으로 직렬화를 수행하도록 지정.
        redisTemplate.setDefaultSerializer(new StringRedisSerializer());

        return redisTemplate;
    }

    /**
     * redis 내부에 저장된 리스트에 접근하여 다양한 연산을 수행하는 객체 반환.
     *
     * @return ListOperations<String, Object>
     */
    public ListOperations<String, Object> getListOperations() {
        return this.redisTemplate().opsForList();
    }

    /**
     * redis 내부에 저장된 key-value 형태 데이터 셋에 접근하여 다양한 연산을 수행할 수 있는 객체 반환.
     *
     * @return ValueOperations<String, Object>
     */
    public ValueOperations<String, Object> getValueOperations() {
        return this.redisTemplate().opsForValue();
    }

    /**
     * Redis 작업중 등록, 수정, 삭제에 대해서 처리 및 예외처리를 수행
     *
     * @param operation
     * @return
     */
    public int executeOperation(Runnable operation) {
        try {
            operation.run();
            return 1;
        } catch (Exception e) {
            System.out.println("Redis 작업 오류 발생 :: " + e.getMessage());
            return 0;
        }
    }
}