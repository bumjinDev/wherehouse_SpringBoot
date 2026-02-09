# Redis Pipeline writeAndFlush 디버깅 - 전체 작업 내역

> **세션 날짜:** 2026-02-06 ~ 2026-02-07  
> **프로젝트:** Wherehouse (Spring Boot 3.3.5 + Lettuce 6.3.2 + Oracle + Redis 3.x)  
> **Redis 서버:** 61.75.54.208:6379 (원격, MiniPC)  
> **RTT:** ~40ms  

---

## 1. 최초 문제 신고

`RdbSyncListener`에서 `redisTemplate.executePipelined(RedisCallback)` 패턴으로 Redis에 매물 데이터를 저장하고 있으나, Lettuce DEBUG 로그에서 **매 HSET 명령마다 개별 `writeAndFlush`가 발생**하여 파이프라인이 동작하지 않는 현상.

### 비정상 로그 패턴 (현재)
```
[main] DefaultEndpoint - write() writeAndFlush command HSET   ← 전송 + 즉시 flush
[nioEventLoop] CommandHandler - Received: 4 bytes              ← ~40ms 후 응답
[main] DefaultEndpoint - write() writeAndFlush command HSET   ← 응답 후 다음 명령
[nioEventLoop] CommandHandler - Received: 4 bytes              ← 또 ~40ms
```

### 정상 파이프라인 패턴 (기대값)
```
[thread] DefaultEndpoint - write() command HSET    ← flush 없이 write만
[thread] DefaultEndpoint - write() command HSET    ← flush 없이 write만
[thread] DefaultEndpoint - write() command HSET    ← flush 없이 write만
... (수백 건 축적)
[thread] flushCommands()                           ← closePipeline 시점에 한 번 flush
[nioEventLoop] Received: N bytes                   ← 응답 일괄 수신
```

### 성능 영향
- 명령 1건당 RTT ~40ms
- 1,000건 → 40초 / 파이프라인 정상이면 1 RTT(40ms)

---

## 2. 기술적 배경: FlushOnClose 정책 메커니즘

### 2-1. 파이프라인 내부 동작 순서

```
openPipeline()
  → FlushOnClose.onOpen(dedicatedConnection)
    → dedicatedConnection.setAutoFlushCommands(false)     ← 핵심 전환점

콜백 내 명령 실행
  → FlushOnClose.onCommand() → no-op (flush 안 함)
  → Lettuce DefaultEndpoint가 autoFlushCommands 플래그 체크
    → false: write()만 수행 (TCP 버퍼 축적)
    → true: writeAndFlush() 수행 (즉시 전송)              ← 현재 이 상태

closePipeline()
  → FlushOnClose.onClose()
    → dedicatedConnection.flushCommands()                  ← 한 번에 전송
    → dedicatedConnection.setAutoFlushCommands(true)       ← 원복
```

### 2-2. LettuceConnection 내부 커넥션 구조

```
LettuceConnection
  ├─ asyncSharedConn:      일반 명령용 (전체 스레드 공유, 단일 커넥션)
  └─ asyncDedicatedConn:   pipeline/MULTI/blocking 전용 (Pool에서 할당)
```

### 2-3. Pool 활성화 여부가 결정적인 이유

`LettuceConnectionFactory.isPoolEnabled()` 내부 로직:
```java
private boolean isPoolEnabled() {
    return clientConfiguration instanceof LettucePoolingClientConfiguration;
}
```

- **Pool 활성화 시:** `openPipeline()` → pool에서 **독립된 dedicated connection** 할당 → `setAutoFlushCommands(false)` 정상 적용
- **Pool 비활성화 시:** `openPipeline()` → shared connection의 **방어적 래퍼** 반환 → `setAutoFlushCommands(false)`를 **no-op으로 삼킴** (shared connection에서 autoFlush를 끄면 다른 스레드의 일반 명령까지 flush 안 되는 참사 방지)

---

## 3. 디버깅 과정 (시간순)

### 3-1단계: commons-pool2 의존성 확인 및 추가

**진단:** `application.yml`에 pool 설정(max-active: 8 등)이 있지만, `commons-pool2`가 classpath에 없으면 Spring Boot가 에러 없이 shared connection 모드로 fallback함.

**확인:**
```cmd
gradlew dependencies --configuration runtimeClasspath | findstr "commons-pool"
```
→ 출력 없음 → **commons-pool2 미포함 확인**

**조치:** `build.gradle` 142번 줄에 추가
```groovy
implementation 'org.apache.commons:commons-pool2'
```

**결과:** ❌ 여전히 writeAndFlush 패턴 유지  
**원인:** commons-pool2가 있어도 `LettuceClientConfiguration` 타입이면 pool 미활성화

---

### 3-2단계: LettucePoolingClientConfiguration으로 교체

**진단:** `LettuceClientConfiguration.builder()`로 만들면 `instanceof LettucePoolingClientConfiguration` 체크가 실패하여 pool이 절대 활성화되지 않음. 수동 `@Bean` 정의 시 Spring Boot auto-configuration도 꺼지므로 `application.yml`의 pool 설정 무시.

**조치:** `RedisConfig.java` 전면 수정

**변경 전:**
```java
LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
        .clientOptions(ClientOptions.builder()
                .protocolVersion(ProtocolVersion.RESP2)
                .build())
        .build();
```

**변경 후:**
```java
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

// Pool 설정
GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
poolConfig.setMaxTotal(8);
poolConfig.setMaxIdle(4);
poolConfig.setMinIdle(2);

LettucePoolingClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
        .clientOptions(ClientOptions.builder()
                .protocolVersion(ProtocolVersion.RESP2)
                .build())
        .poolConfig(poolConfig)
        .build();
```

**application.yml pool 설정** (RedisConfig에서 직접 GenericObjectPoolConfig로 설정하므로 참고용):
```yaml
spring.data.redis.lettuce.pool:
  max-active: 8
  max-idle: 4
  min-idle: 2
  max-wait: -1ms
  time-between-eviction-runs: 10s
```

**결과:** 이 시점부터 로그 분석에 혼동 시작 (아래 3-4단계에서 원인 규명)

---

### 3-3단계: Reflection 기반 autoFlushCommands 진단 로그 삽입

**목적:** 파이프라인 콜백 진입 직후 `autoFlushCommands` 플래그의 실제 값을 확인  
**문제:** Lettuce 6.x에 `isAutoFlushCommands()` getter 없음  
**해결:** Reflection으로 `RedisChannelHandler.autoFlushCommands` 필드 직접 접근

**삽입 위치:** `RdbSyncListener`의 3개 메서드 콜백 시작 부분
- `syncCharterToRedis()` → 태그 `CHARTER`
- `syncMonthlyToRedis()` → 태그 `MONTHLY`
- `storeSafetyScoresToRedis()` → 태그 `SAFETY`

**삽입한 코드 (CHARTER 예시):**
```java
redisHandler.redisTemplate.executePipelined((RedisCallback<Object>) connection -> {

    // ====== [DEBUG] Pipeline + autoFlush 상태 확인 ======
    log.info(">>> [DEBUG:PIPELINE:CHARTER] isPipelined={}, connectionClass={}",
            connection.isPipelined(), connection.getClass().getName());
    if (connection instanceof LettuceConnection lc) {
        Object nativeConn = lc.getNativeConnection();
        log.info(">>> [DEBUG:PIPELINE:CHARTER] nativeConnectionClass={}",
                nativeConn.getClass().getName());
        if (nativeConn instanceof io.lettuce.core.api.StatefulConnection<?,?> sc) {
            try {
                java.lang.reflect.Field field =
                    io.lettuce.core.RedisChannelHandler.class
                        .getDeclaredField("autoFlushCommands");
                field.setAccessible(true);
                log.info(">>> [DEBUG:PIPELINE:CHARTER] autoFlushCommands={}",
                        field.get(sc));
            } catch (Exception e) {
                log.warn(">>> [DEBUG] autoFlush 확인 실패: {}", e.getMessage());
            }
        }
    }
    // ====== [DEBUG] END ======

    // ... 기존 명령 실행 코드 ...
    return null;
});
```

**결과:** ❌ **로그가 아예 찍히지 않음** → 이 코드 경로 자체가 실행되지 않고 있었음

---

### 3-4단계: "로그가 안 찍히는 이유" 분석 — 핵심 발견

**현상 1:** `io.lettuce.core: DEBUG` → Lettuce 내부 로그 출력 + HSET writeAndFlush 패턴 확인  
**현상 2:** `io.lettuce.core: info` → 아무 로그도 안 나오고 앱 기동 멈춤  
**현상 3:** 두 경우 모두 커스텀 `log.info(">>> [DEBUG:PIPELINE:CHARTER]")` 미출력

**로그 파일 분석 (DEBUG 레벨, 570줄):**

```
줄 2:    "Starting WherehouseApplication"
줄 120~: Lettuce 커넥션 생성
줄 129~570: [main] 스레드에서 HSET writeAndFlush 반복
"Started WherehouseApplication" → ❌ 없음
```

**결정적 단서: 스레드 이름이 `[main]`**

- `@Scheduled` 메서드는 `[scheduling-1]` 같은 스케줄러 스레드에서 실행됨
- `[main]`에서 HSET이 실행된다는 것은 **앱 기동 완료 전**에 다른 코드가 Redis를 호출하고 있다는 뜻

**Spring Boot 기동 순서:**
```
[main] 스레드 시작
  → Bean 생성
  → @PostConstruct 실행         ← 여기서 Redis 호출이 발생하고 있음
  → CommandLineRunner 실행
  → "Started WherehouseApplication" 출력    ← @PostConstruct가 끝나야 여기 도달
  → @Scheduled 스레드 풀 가동
    → [scheduling-1] initialDelay=1000 후 RdbSyncListener 실행
```

---

### 3-5단계: 범인 특정 — ReviewWriteService.@PostConstruct

프로젝트 전체에서 `@PostConstruct`, `CommandLineRunner`, `ApplicationRunner`, `afterPropertiesSet`, `ContextRefreshedEvent` 검색 수행.

**발견:** `ReviewWriteService.java` (98~166번 줄)

```java
@PostConstruct
public void initializeStatisticsCache() {
    List<ReviewStatistics> allStats = reviewStatisticsRepository.findAll();
    HashOperations<String, String, Object> hashOps = redisTemplate.opsForHash();

    int successCount = 0;
    for (ReviewStatistics stats : allStats) {
        try {
            String key = STATS_KEY_PREFIX + stats.getPropertyId();

            // ... null 체크, sum 역산 로직 ...

            hashOps.put(key, FIELD_COUNT, reviewCount);   // ← 개별 HSET (writeAndFlush)
            hashOps.put(key, FIELD_SUM, sum);              // ← 개별 HSET (writeAndFlush)
            successCount++;
        } catch (Exception e) { ... }
    }
}
```

**이것이 범인인 이유:**
1. `@PostConstruct`는 `[main]` 스레드에서 실행 → 로그의 `[main]` 일치
2. `hashOps.put()` = 개별 HSET → 파이프라인 아님 → `writeAndFlush` 패턴 일치
3. `ReviewStatistics` 건수 × 2 HSET × 40ms RTT = 기동 장시간 블로킹
4. 이게 끝나야 "Started WherehouseApplication" → `@Scheduled` 실행 → 우리 디버그 로그 출력
5. `io.lettuce.core: info`로 바꾸면 Lettuce 내부 DEBUG 로그 사라지고, 커스텀 로그는 아직 미실행이니 **완전 공백**

---

## 4. 현재 상태 요약

### 4-1. 변경된 파일 목록

| 파일 | 변경 내용 | 상태 |
|------|----------|------|
| `build.gradle` | `implementation 'org.apache.commons:commons-pool2'` 추가 (142번 줄) | ✅ 적용 |
| `RedisConfig.java` | `LettuceClientConfiguration` → `LettucePoolingClientConfiguration` 교체 + `GenericObjectPoolConfig` 추가 | ✅ 적용 (파이프라인 해결) |
| `RdbSyncListener.java` | 3개 pipeline 메서드에 Reflection 기반 autoFlush 진단 로그 삽입 | ⚠️ 정리 필요 |
| `application.yml` | `io.lettuce.core` 로깅 레벨 토글 | 현재 `DEBUG` (정리 시 `info`로) |
| `ReviewWriteService.java` | **미수정** — @PostConstruct 52,020건 개별 HSET (69분 블로킹) | ❌ 개선 필요 |

### 4-2. 현재 RedisConfig.java 전문 (최신)

```java
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {

        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(8);
        poolConfig.setMaxIdle(4);
        poolConfig.setMinIdle(2);

        LettucePoolingClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .clientOptions(ClientOptions.builder()
                        .protocolVersion(ProtocolVersion.RESP2)
                        .build())
                .poolConfig(poolConfig)
                .build();

        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration(host, port);
        serverConfig.setPassword("abed1234");

        LettuceConnectionFactory factory = new LettuceConnectionFactory(serverConfig, clientConfig);
        factory.setPipeliningFlushPolicy(
                LettuceConnection.PipeliningFlushPolicy.flushOnClose()
        );

        return factory;
    }

    // ... redisTemplate, redisTemplateAllMapData, redisTemplateChoiceMapData Bean들 ...
}
```

### 4-3. RdbSyncListener 스케줄러 설정

```java
@Scheduled(fixedDelay = Long.MAX_VALUE, initialDelay = 1000)
public void handleDataCollectionCompletedEvent() { ... }
```

- `fixedDelay = Long.MAX_VALUE` → 1회만 실행
- `initialDelay = 1000` → 기동 완료 후 1초 뒤 실행
- 기동이 완료되지 않으면 실행 자체가 안 됨

### 4-4. 현재 application.yml 로깅 설정

```yaml
logging:
  level:
    root: info
    com.zaxxer.hikari.pool: DEBUG
    com.zaxxer.hikari.HikariConfig: DEBUG
    io.lettuce.core: info        # ← 현재 info (DEBUG로 바꾸면 Lettuce 내부 로그 출력)
```

---

## 5. 최종 해결 결과 (2026-02-07 확인)

### 5-1. 파이프라인 정상 동작 확인 — Case A 확정

`@PostConstruct` 완료를 기다린 후 `@Scheduled` 실행 결과, **파이프라인이 정상 동작**하고 있었다.

**정상 동작 로그:**
```
[scheduling-1] writeToBuffer() buffering command HMSET   ← 버퍼 축적 (flush 없음)
[scheduling-1] writeToBuffer() buffering command ZADD    ← 버퍼 축적
[scheduling-1] writeToBuffer() buffering command ZADD    ← 버퍼 축적
[scheduling-1] writeToBuffer() buffering command HMSET   ← 버퍼 축적
... (1,088개 명령 축적)
[scheduling-1] flushCommands() Flushing 1088 commands    ← 한 번에 전송!
```

**핵심 차이점 비교:**

| 항목 | 변경 전 (비정상) | 변경 후 (정상) |
|------|-----------------|---------------|
| 스레드 | `[main]` | `[scheduling-1]` |
| 실행 코드 | `ReviewWriteService.@PostConstruct` | `RdbSyncListener.@Scheduled` |
| Lettuce 동작 | `writeAndFlush` (즉시 전송) | `writeToBuffer` (버퍼 축적) |
| 네트워크 패턴 | 명령당 1 RTT (~40ms) | 1,088 명령 = 1 RTT |
| 1,088 명령 소요시간 | ~43초 | ~40ms |

### 5-2. 근본 원인 최종 정리

**문제가 2개 겹쳐 있었다:**

1. **`LettuceClientConfiguration` → `LettucePoolingClientConfiguration` 교체 필요** (3-2단계에서 해결 완료)
   - Pool 미활성화 → shared connection 래퍼 → `setAutoFlushCommands(false)` 무시 → 파이프라인 무력화

2. **`ReviewWriteService.@PostConstruct`가 52,020건 × 2 HSET을 개별 전송** (미수정)
   - 로컬 Redis(RTT ≈ 0ms): 수 초 → 인지 불가
   - 원격 Redis(RTT ≈ 40ms): 52,020 × 2 × 40ms ≈ **69분** → 앱 기동 블로킹
   - 이 코드가 `[main]` 스레드에서 기동 완료 전에 실행되면서, `@Scheduled` 실행을 차단
   - 우리가 심어놓은 디버그 로그까지 도달 못함 → "파이프라인 여전히 안 됨"으로 오인

**왜 문제를 늦게 발견했는가:**
- 로컬 환경에서는 RTT가 없어 `@PostConstruct`가 빠르게 완료 → 병목 미인지
- Spring Boot 기동 순서(`@PostConstruct` → `@Scheduled`) 미숙지
- 로그 미출력 현상을 "코드 문제"로만 봤고, "실행 순서 문제"로 관찰하지 않음
- `[main]` vs `[scheduling-1]` 스레드명 차이를 초기에 주목하지 않음

---

## 6. 남은 작업 (후속 세션)

### 6-1. [우선순위 1] ReviewWriteService.@PostConstruct 개선 (필수)

52,020건 × 2 HSET × 40ms RTT ≈ **69분 기동 블로킹**. 반드시 개선 필요.

**개선 방안 (택 1):**

1. **executePipelined로 변경** — 모든 put을 파이프라인으로 묶어 1 RTT
```java
@PostConstruct
public void initializeStatisticsCache() {
    List<ReviewStatistics> allStats = reviewStatisticsRepository.findAll();

    redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
        for (ReviewStatistics stats : allStats) {
            // ... null 체크, sum 역산 ...
            byte[] key = redisTemplate.getStringSerializer().serialize(STATS_KEY_PREFIX + stats.getPropertyId());
            Map<byte[], byte[]> hash = new HashMap<>();
            hash.put(serialize(FIELD_COUNT), serialize(reviewCount));
            hash.put(serialize(FIELD_SUM), serialize(sum));
            connection.hashCommands().hMSet(key, hash);
        }
        return null;
    });
}
```

2. **@EventListener(ApplicationReadyEvent.class)로 변경** — 기동 완료 후 실행 (기동 블로킹 제거)
```java
@EventListener(ApplicationReadyEvent.class)
public void initializeStatisticsCache() { ... }
```

3. **@Async 조합** — 백그라운드 비동기 실행

### 6-2. [우선순위 2] 디버그 코드 정리

파이프라인 정상 동작 확인 후:
- `RdbSyncListener`의 3개 메서드에 삽입한 Reflection 진단 코드 제거
- `io.lettuce.core` 로깅 레벨을 `info`로 확정

---

## 7. 업로드된 파일 경로

| 파일 | 경로 | 설명 |
|------|------|------|
| RedisConfig.java (초기) | `/mnt/user-data/uploads/1770388211576_RedisConfig.java` | LettuceClientConfiguration 버전 |
| RedisConfig.java (수정) | `/mnt/user-data/uploads/1770389051196_RedisConfig.java` | LettucePoolingClientConfiguration 버전 |
| RdbSyncListener.java (최신) | `/mnt/user-data/uploads/1770390526355_RdbSyncListener.java` | 진단 로그 삽입 완료 |
| BatchScheduler.java | `/mnt/user-data/uploads/1770390526355_BatchScheduler.java` | @Component 주석 처리됨 (미사용) |
| ReviewWriteService.java | `/mnt/user-data/uploads/1770391258380_ReviewWriteService.java` | @PostConstruct 문제 코드 |
| build.gradle | `/mnt/user-data/uploads/1770388911654_build.gradle` | commons-pool2 추가됨 |
| application.yml (최신) | `/mnt/user-data/uploads/1770389632147_application.yml` | lettuce pool 설정 포함 |
| wherehouse.log (info) | `/mnt/user-data/uploads/1770390146942_wherehouse.log` | 기동 멈춤 로그 |
| wherehouse.log (DEBUG) | `/mnt/user-data/uploads/1770390330942_wherehouse.log` | 570줄, [main] HSET 반복 |

---

## 8. 핵심 교훈

1. **수동 `@Bean` RedisConnectionFactory → Spring Boot auto-configuration 비활성화** → yml의 pool 설정 무시
2. **`LettuceClientConfiguration` ≠ `LettucePoolingClientConfiguration`** → instanceof 체크로 pool 결정
3. **Pool 없으면 shared connection 래퍼가 `setAutoFlushCommands(false)`를 삼킴** → 파이프라인 무력화
4. **`@PostConstruct`는 `[main]` 스레드에서 기동 전 실행** → 느린 외부 I/O가 전체 기동 블로킹
5. **로그 스레드 이름 `[main]` vs `[scheduling-1]`이 코드 실행 경로의 결정적 단서**
6. **Lettuce 6.x에 `isAutoFlushCommands()` getter 없음** → Reflection 필요
7. **로컬과 원격 환경의 RTT 차이가 병목 인지 여부를 결정** → 로컬에서는 0ms RTT로 52,020건이 순식간에 끝나지만, 원격 40ms RTT에서는 69분으로 폭증
8. **문제 2개가 겹치면 디버깅이 기하급수적으로 어려워진다** → 파이프라인 미동작 + @PostConstruct 블로킹이 동시에 발생하여 원인 분리 실패
