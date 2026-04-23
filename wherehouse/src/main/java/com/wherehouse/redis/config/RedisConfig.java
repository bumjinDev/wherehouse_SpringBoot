package com.wherehouse.redis.config;

import java.util.List;
import java.util.Map;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.protocol.ProtocolVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherehouse.restapi.mapdata.model.MapDataEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

/**
 * Redis 환경 설정
 *
 * 이 클래스는 두 개의 독립된 계층을 설정한다.
 *
 * [TCP 연결 계층] redisConnectionFactory()
 *   - Lettuce 클라이언트가 Redis 서버(61.75.54.208:6379)와 TCP 연결을 맺고 유지하는 방식을 설정한다.
 *   - 커넥션 풀 크기, RESP 프로토콜 버전, Pipeline flush 정책이 이 계층에 속한다.
 *
 * [데이터 변환 계층] redisTemplate(), redisTemplateAllMapData(), redisTemplateChoiceMapData()
 *   - Java 객체와 Redis 바이트 간 변환 규칙(Serializer)을 설정한다.
 *   - Redis 서버는 Key/Value를 모두 바이트 배열로 저장하며, 그 바이트가 UTF-8인지 JSON인지 알지 못한다.
 *   - 따라서 "어떤 형식의 바이트로 변환하여 저장/조회할 것인가"는 클라이언트 측에서 Serializer로 결정한다.
 *
 * 두 계층 사이에 RESP 프레이밍(byte[]를 RESP 프로토콜 형식으로 조립하여 TCP 스트림에 기록하는 과정)이
 * 존재하지만, 이는 Lettuce 드라이버가 프로토콜 스펙에 따라 자동 처리하며 이 설정 클래스에서 관여하지 않는다.
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    //    @Bean
//    public RedisConnectionFactory redisConnectionFactory() {
//        return new LettuceConnectionFactory(host, port);
//    }

    /**
     * Redis 서버와의 TCP 연결을 관리하는 ConnectionFactory Bean.
     *
     * 세 가지를 설정한다:
     *   (1) 커넥션 풀 — 동시 TCP 연결 수 관리 (GenericObjectPoolConfig)
     *   (2) 프로토콜 버전 — Redis 서버와 통신할 RESP 버전 (RESP2)
     *   (3) Pipeline flush 정책 — executePipelined() 사용 시 Command를 TCP 소켓에 언제 전송할지 결정
     *
     * 현재 Redis 서버: 3.x 버전 (RESP3 미지원)
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {

        /*
         * ──── (1) 커넥션 풀 설정 ────
         *
         * Lettuce는 기본적으로 단일 TCP 연결을 Netty EventLoop 기반으로 멀티플렉싱하지만,
         * LettucePoolingClientConfiguration을 사용하면 Apache Commons Pool2 기반의
         * 커넥션 풀이 활성화되어 여러 TCP 연결을 관리한다.
         *
         * maxTotal(8):  풀 내 최대 커넥션 수. 8개 초과 요청은 풀에 반환될 때까지 blocking 대기한다.
         *               트러블슈팅 #3(Oracle IN절 Chunking)에서 HikariCP Pool 6개 대비 동시 요청 시
         *               Connection 경합(Waiting 최대 6건, NOT_ADDED 162건)이 발생한 사례와 동일한 원리다.
         *
         * maxIdle(4):   요청이 없어도 유지하는 유휴 커넥션 최대 수.
         *               4개를 초과하는 유휴 커넥션은 풀이 회수하여 서버 측 리소스를 절약한다.
         *
         * minIdle(2):   최소 유지 유휴 커넥션 수.
         *               트래픽 유입 시 TCP 3-way 핸드셰이크 없이 즉시 사용 가능한 커넥션을 보장한다.
         */
        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(8);
        poolConfig.setMaxIdle(4);
        poolConfig.setMinIdle(2);

        /*
         * ──── (2) 프로토콜 버전 설정 ────
         *
         * protocolVersion(ProtocolVersion.RESP2):
         *   Redis 서버와 통신할 RESP 프로토콜 버전을 RESP2로 고정한다.
         *
         *   RESP2: Redis 1.2+ 에서 사용. *N(배열), $N(벌크 문자열), +(단순 문자열), -(에러), :(정수)
         *          5가지 데이터 타입으로 명령과 응답을 인코딩한다.
         *   RESP3: Redis 6.0+ 에서 도입. Map, Set, Double 등 추가 타입을 지원한다.
         *
         *   Lettuce는 기본적으로 연결 시 RESP3 협상(HELLO 3)을 시도하는데,
         *   현재 Wherehouse의 Redis 서버가 3.x 버전이라 RESP3을 지원하지 않는다.
         *   명시적으로 RESP2를 지정하여 HELLO 명령 자체를 보내지 않도록 하여 호환성 문제를 방지한다.
         */
        LettucePoolingClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .clientOptions(ClientOptions.builder()
                        .protocolVersion(ProtocolVersion.RESP2)
                        .build())
                .poolConfig(poolConfig)
                .build();

        /* Redis 서버 접속 정보 (host, port는 application.properties에서 주입) */
        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration(host, port);
//        serverConfig.setPassword("abed1234");   // 로컬은 패스워드 없음. 원격 서버(61.75.54.208) 접속 시 사용하고 로컬 접속할 때는 이 부분 주석 처리 해야됨

        LettuceConnectionFactory factory = new LettuceConnectionFactory(serverConfig, clientConfig);

        /*
         * ──── (3) Pipeline Flush 정책 설정 ────
         *
         * 이 설정은 트러블슈팅 #6(Redis Pipeline RTT 최적화)의 핵심 전제 조건이다.
         *
         * redisTemplate.executePipelined(RedisCallback) 호출 시,
         * 콜백 내부에서 connection.zRangeByScore()를 3회 호출하면
         * 각 Command가 Lettuce의 write buffer(Netty ChannelBuffer)에 적재된다.
         *
         * 이때 flush 정책이 buffer에 쌓인 Command를 TCP 소켓에 언제 전송할지를 결정한다.
         *
         * ┌──────────────────────────┬───────────────────────────────────────────────────┐
         * │ 정책                      │ 동작                                              │
         * ├──────────────────────────┼───────────────────────────────────────────────────┤
         * │ flushEachCommand() (기본) │ connection.zRangeByScore() 호출마다 즉시           │
         * │                          │ TCP 소켓에 flush한다.                              │
         * │                          │ → Pipeline으로 묶었음에도 실질적으로 3회의 독립      │
         * │                          │   TCP 전송이 발생하여 RTT 절감 효과가 사라진다.     │
         * ├──────────────────────────┼───────────────────────────────────────────────────┤
         * │ flushOnClose() (현재)     │ 콜백이 return null로 종료되는 시점에 buffer에 쌓인  │
         * │                          │ 3개 Command를 하나의 TCP Write로 일괄 flush한다.    │
         * │                          │ → 지역구당 RTT가 3회에서 1회로 감소한다.            │
         * │                          │ → 실측: 강남구 제외 24개 지역구 평균               │
         * │                          │   MethodTime 128.95ms → 58.80ms (54.4% 감소)      │
         * ├──────────────────────────┼───────────────────────────────────────────────────┤
         * │ bufferedFlushing(N)      │ buffer에 N개 Command가 적재될 때마다 flush한다.     │
         * │                          │ → 현재 지역구당 정확히 3개 Command이므로 N=3 설정   │
         * │                          │   시 flushOnClose()와 동일 효과이나, Command 수가   │
         * │                          │   변경되면 N도 수정해야 하므로 유지보수 부담이 있다. │
         * └──────────────────────────┴───────────────────────────────────────────────────┘
         *
         * flushOnClose()는 Command 수에 의존하지 않고 콜백 종료 시점에 일괄 전송하므로,
         * 향후 Command가 추가/변경되어도 정책 수정이 불필요하다.
         */
        factory.setPipeliningFlushPolicy(
                LettuceConnection.PipeliningFlushPolicy.flushOnClose()
        );

        return factory;
    }

    /**
     * 범용 RedisTemplate Bean.
     *
     * 추천 서비스(MonthlyRecommendationService, CharterRecommendationService)에서
     * Sorted Set 조회(opsForZSet().rangeByScore())에 사용하는 RedisTemplate이다.
     *
     * 직렬화(Serializer)는 JVM 힙 메모리 상의 Java 객체를 연속 바이트 배열로 변환하는 규칙이다.
     * Redis 서버는 Key/Value를 모두 바이트 배열로 저장하고 그대로 반환할 뿐,
     * 그 바이트가 UTF-8인지 JSON인지 관여하지 않는다.
     * Serializer가 이 변환 형식을 결정하며, 저장과 조회 시 반드시 동일한 Serializer를 사용해야 한다.
     *
     * [Key]   StringRedisSerializer         — Java String → UTF-8 byte[]
     * [Value] Jackson2JsonRedisSerializer    — Java Object → JSON 문자열의 UTF-8 byte[]
     *
     * Pipeline(executePipelined) 사용 시 주의:
     *   opsForZSet().rangeByScore()는 이 Serializer를 자동 적용하여 Set<Object>를 반환하지만,
     *   executePipelined() 내부의 connection.zRangeByScore()는 low-level RedisConnection API이므로
     *   이 Serializer를 거치지 않고 raw byte[] 또는 String을 반환한다.
     *   → Phase 2(Pipeline 적용) 코드에서 convertToStringSet()으로 수동 변환 처리가 추가된 이유.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {

        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory());

        /*
         * ──── Key Serializer: StringRedisSerializer ────
         *
         * Java String을 UTF-8 바이트 배열로 변환한다.
         *
         *   Java String "idx:deposit:강남구"
         *     ↓ StringRedisSerializer.serialize()
         *   byte[] { 0x69,0x64,0x78,0x3A, ... 0xEA,0xB0,0x95,0xEB,0x82,0xA8,0xEA,0xB5,0xAC }
         *           (ASCII 문자: 1바이트, 한글: 3바이트 — UTF-8 인코딩 규칙)
         *
         * redis-cli에서 조회 시 사람이 읽을 수 있는 문자열로 표시된다.
         *
         * setHashKeySerializer: Redis Hash 자료구조(HSET/HGET)의 Hash Field에 대한 직렬화.
         *                       추천 서비스의 Sorted Set(ZADD/ZRANGEBYSCORE) 조회에서는 사용되지 않는다.
         */
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());

        /*
         * ──── Value Serializer: Jackson2JsonRedisSerializer ────
         *
         * Java Object를 Jackson ObjectMapper로 JSON 문자열 변환 후 UTF-8 바이트로 인코딩한다.
         *
         * [저장 시] Java → Redis
         *   Java String "APT_12345"
         *     ↓ ObjectMapper.writeValueAsBytes("APT_12345")
         *   byte[] { 0x22, 0x41,0x50,0x54,0x5F,0x31,0x32,0x33,0x34,0x35, 0x22 }
         *           ↑ JSON 쌍따옴표(0x22)가 양쪽에 포함됨
         *
         * [조회 시] Redis → Java
         *   byte[] { 0x22, 0x41,...0x35, 0x22 }
         *     ↓ ObjectMapper.readValue(bytes, Object.class)
         *   Java String "APT_12345"
         *
         * 타입 파라미터가 Object.class이므로 역직렬화 시 구체 타입 정보가 없다.
         *   - JSON String → Java String
         *   - JSON Object → LinkedHashMap
         *   - JSON Array  → ArrayList
         * 추천 서비스의 Sorted Set member는 단순 String이므로 문제없지만,
         * 복합 객체를 정확한 타입으로 복원하려면 전용 RedisTemplate에 구체 타입을 명시해야 한다.
         * (아래 redisTemplateAllMapData, redisTemplateChoiceMapData 참조)
         *
         * setHashValueSerializer: Redis Hash Value에 대한 직렬화.
         *                         추천 서비스의 Sorted Set 조회에서는 사용되지 않는다.
         */
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);
        redisTemplate.setValueSerializer(serializer);
        redisTemplate.setHashValueSerializer(serializer);

        // redisTemplate.setDefaultSerializer(new StringRedisSerializer()); // 이 줄은 삭제하거나 위와 같이 변경해도 된다.

        return redisTemplate;
    }

    /**
     * 지도 데이터 전체 조회 전용 RedisTemplate.
     *
     * 범용 redisTemplate()과의 차이:
     *   범용은 Jackson2JsonRedisSerializer<>(Object.class)로 타입을 Object로 지정하여
     *   역직렬화 시 LinkedHashMap/ArrayList로 반환된다.
     *   이 Bean은 TypeReference<Map<String, List<Map<String, Double>>>>를 명시하여
     *   Jackson이 역직렬화 시 정확한 제네릭 타입으로 복원한다.
     *
     * 메서드 파라미터로 RedisConnectionFactory를 받는 이유:
     *   Spring이 위에서 정의한 redisConnectionFactory() Bean을 자동 주입한다.
     *   redisTemplate()처럼 redisConnectionFactory()를 직접 호출하지 않고
     *   파라미터 주입 방식을 사용하여 동일 ConnectionFactory 인스턴스를 공유한다.
     */
    @Bean
    public RedisTemplate<String, Map<String, List<Map<String, Double>>>> redisTemplateAllMapData(RedisConnectionFactory redisConnectionFactory) {

        RedisTemplate<String, Map<String, List<Map<String, Double>>>> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        /*
         * TypeReference를 사용한 구체 타입 지정.
         * Java의 Type Erasure로 인해 런타임에 제네릭 타입 정보가 소거되는데,
         * TypeReference는 익명 클래스의 상위 타입 정보를 통해 제네릭 타입을 보존하여
         * Jackson이 Map<String, List<Map<String, Double>>> 구조로 정확히 역직렬화할 수 있게 한다.
         */
        ObjectMapper objectMapper = new ObjectMapper();
        Jackson2JsonRedisSerializer<Map<String, List<Map<String, Double>>>> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper.getTypeFactory().constructType(new TypeReference<Map<String, List<Map<String, Double>>>>() {}));

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        return template;
    }

    /**
     * 지도 데이터 선택 조회 전용 RedisTemplate.
     *
     * 특정 guId 기준으로 지도 데이터를 조회할 때 사용한다.
     * TypeReference<List<MapDataEntity>>를 명시하여
     * Jackson이 역직렬화 시 List 내부 원소를 MapDataEntity 객체로 정확히 복원한다.
     *
     * 만약 범용 redisTemplate()(Object.class)로 동일 데이터를 조회하면
     * List<LinkedHashMap>으로 반환되어 MapDataEntity의 필드에 직접 접근할 수 없다.
     */
    @Bean
    public RedisTemplate<String, List<MapDataEntity>> redisTemplateChoiceMapData(RedisConnectionFactory redisConnectionFactory) {

        RedisTemplate<String, List<MapDataEntity>> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        ObjectMapper objectMapper = new ObjectMapper();

        /* TypeReference<List<MapDataEntity>>로 구체 타입 지정 — redisTemplateAllMapData와 동일 원리 */
        Jackson2JsonRedisSerializer<List<MapDataEntity>> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper.getTypeFactory().constructType(new TypeReference<List<MapDataEntity>>() {}));

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        return template;
    }

}