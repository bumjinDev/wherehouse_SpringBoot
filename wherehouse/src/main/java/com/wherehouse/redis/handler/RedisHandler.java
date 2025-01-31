package com.wherehouse.redis.handler;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisHandler {

	@Autowired
	public RedisTemplate<String, Object> redisTemplate;

	@Autowired
	RedisConnectionFactory redisConnectionFactory; // 여기에 자동 주입됨
	
    /**
     * 리스트에 접근하여 다양한 연산을 수행합니다.
     *
     * @return ListOperations<String, Object>
     */
    public ListOperations<String, Object> getListOperations() {
        return redisTemplate.opsForList();
    }

    /**
     * 단일 데이터에 접근하여 다양한 연산을 수행합니다.
     *
     * @return ValueOperations<String, Object>
     */
    public ValueOperations<String, Object> getValueOperations() {
        return redisTemplate.opsForValue();
    }

    /**
     * Redis 작업 중 등록, 수정, 삭제에 대해서 처리 및 예외처리를 수행합니다.
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
    
    public void clearCurrentRedisDB() {
    	redisConnectionFactory.getConnection().serverCommands().flushDb();  // 현재 선택된 DB 초기화
        System.out.println("✅ Redis 현재 DB 초기화 완료");
    }
}
