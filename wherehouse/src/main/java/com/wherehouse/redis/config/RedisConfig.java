package com.wherehouse.redis.config;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherehouse.restapi.mapdata.model.MapDataEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

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

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(host, port);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
    	
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        
        redisTemplate.setConnectionFactory(redisConnectionFactory());

        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new StringRedisSerializer());

        redisTemplate.setDefaultSerializer(new StringRedisSerializer());
        
        return redisTemplate;
    }
    
    /* 테이블 "MapData" 내 모든 테이터 한꺼번에 가져오는 것. */
    @Bean
    public RedisTemplate<String, Map<String, List<Map<String, Double>>>> redisTemplateAllMapData(RedisConnectionFactory redisConnectionFactory) {
        
    	RedisTemplate<String, Map<String, List<Map<String, Double>>>> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // JSON 직렬화 설정 (타입 명시적으로 지정)
        ObjectMapper objectMapper = new ObjectMapper();
        Jackson2JsonRedisSerializer<Map<String, List<Map<String, Double>>>> serializer =
            new Jackson2JsonRedisSerializer<>(objectMapper.getTypeFactory().constructType(new TypeReference<Map<String, List<Map<String, Double>>>>() {}));

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        return template;
    }

    /* 테이블 "MapData" 내 guIds 를 기준으로 데이터를 가져오는 것. */
    @Bean
    public RedisTemplate<String, List<MapDataEntity>> redisTemplateChoiceMapData(RedisConnectionFactory redisConnectionFactory) {
        
    	RedisTemplate<String, List<MapDataEntity>> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // JSON 직렬화 설정
        ObjectMapper objectMapper = new ObjectMapper();
        
        // ✅ 올바른 타입 사용
        Jackson2JsonRedisSerializer<List<MapDataEntity>> serializer =
            new Jackson2JsonRedisSerializer<>(objectMapper.getTypeFactory().constructType(new TypeReference<List<MapDataEntity>>() {}));

        template.setKeySerializer(new StringRedisSerializer()); // Key는 문자열 직렬화
        template.setValueSerializer(serializer); // Value 직렬화
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        return template;
    }

}