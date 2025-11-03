# CompletableFuture 완전 통합 문서

## 서론: Future의 구조적 한계

### 전통적 Future의 설계 결함

Java 5에서 도입된 `Future<T>` 인터페이스는 비동기 작업의 결과를 표현하는 최초의 표준 추상화였다. 하지만 이 설계는 근본적인 구조적 결함을 내포했다.

```java
ExecutorService executor = Executors.newFixedThreadPool(3);
Future<String> future = executor.submit(() -> {
    Thread.sleep(1000);
    return "결과";
});

// 문제: 결과를 얻으려면 반드시 블로킹해야 함
String result = future.get();  // Main Thread가 여기서 블로킹됨
```

### 문제 1: 블로킹 방식의 결과 조회

`Future.get()`은 작업이 완료될 때까지 호출 스레드를 블로킹한다. 이는 비동기 작업을 시작했음에도 불구하고, 결과를 소비하는 시점에서 동기 방식으로 회귀한다는 모순을 의미한다. 작업을 별도 스레드에서 실행시켜놓고, 메인 스레드는 그 작업이 끝날 때까지 아무것도 하지 못하고 대기한다.

### 문제 2: 작업 조합의 불가능

여러 비동기 작업을 순차적으로 연결하거나 병렬로 조합하려면, 개발자가 직접 스레드 조율 로직을 작성해야 했다.

```java
// "작업 A 완료 후 작업 B 실행" 구현
Future<String> futureA = executor.submit(() -> fetchDataA());
String resultA = futureA.get();  // 블로킹 대기

Future<String> futureB = executor.submit(() -> processData(resultA));
String resultB = futureB.get();  // 또 블로킹

// "작업 A, B, C를 모두 실행하고 모두 완료되면 통합" 구현
Future<String> f1 = executor.submit(() -> task1());
Future<String> f2 = executor.submit(() -> task2());
Future<String> f3 = executor.submit(() -> task3());

// 하나씩 get() 호출하며 블로킹 → 병렬 실행의 이점 상실
String r1 = f1.get();
String r2 = f2.get();
String r3 = f3.get();
```

### 문제 3: 완료 시점의 콜백 부재

작업이 완료되었을 때 자동으로 실행될 후속 작업을 등록하는 메커니즘이 없다. 개발자는 폴링 방식으로 `isDone()`을 반복 호출하거나, `get()`으로 블로킹해야 한다.

**CompletableFuture는 이 세 가지 구조적 결함을 해결하기 위해 Java 8에서 도입된 완전한 비동기 조합 프레임워크다.** 블로킹 없는 결과 소비, 선언적 작업 조합, 완료 시점 콜백 등록을 모두 지원한다.

---

## CompletableFuture의 정의와 본질

### CompletableFuture가 무엇인가

CompletableFuture는 **Java의 클래스**다. `java.util.concurrent.CompletableFuture<T>`라는 전체 경로를 가지며, JDK 8부터 도입되었다. 이것은 `Future<T>`와 `CompletionStage<T>` 두 인터페이스를 동시에 구현한다.

```java
public class CompletableFuture<T> implements Future<T>, CompletionStage<T> {
    // OpenJDK 소스 코드
}
```

이 클래스의 본질은 **"아직 완료되지 않은 비동기 연산을 표현하는 객체이자, 그 완료 시점에 실행될 후속 작업들을 체이닝할 수 있는 컨테이너"**다.

비유하자면, CompletableFuture는 "미래의 어느 시점에 값이 들어올 빈 상자"이며, 이 상자에는 "값이 들어오면 자동으로 실행될 작업들의 리스트"가 함께 붙어 있다.

### CompletableFuture의 내부 구조

OpenJDK 소스를 단순화하면 다음과 같은 핵심 필드들이 있다.

```java
public class CompletableFuture<T> {
    
    // 1. 작업의 최종 결과 또는 예외를 담는 필드
    volatile Object result;
    
    // 2. 완료 시 실행될 후속 작업들의 연결 리스트 (스택 구조)
    volatile Completion stack;
    
    // 3. 작업 완료 여부를 판단하는 상태 플래그
    // result 필드의 값으로 판단: null이면 미완료, 값이 있으면 완료
}
```

#### 필드 1: result

`result` 필드는 작업의 최종 결과를 담는다. 이 필드의 타입이 `Object`인 이유는 다음 세 가지 상태를 모두 표현하기 위함이다.

- `null`: 작업이 아직 완료되지 않음
- 실제 값 (예: `"Hello"`): 작업이 성공적으로 완료됨
- `AltResult` 객체: 작업이 예외로 완료됨 (예외를 래핑한 특수 객체)

`volatile` 키워드는 멀티 스레드 환경에서 이 필드의 변경사항이 모든 스레드에게 즉시 가시성을 보장하기 위함이다. 한 스레드가 `result`에 값을 쓰면, 다른 모든 스레드는 CPU 캐시를 거치지 않고 메인 메모리에서 최신 값을 읽는다.

#### 필드 2: stack (Completion Stack)

`stack` 필드는 완료 시 실행될 후속 작업들의 연결 리스트다. 이것은 단일 연결 리스트(singly-linked list) 형태로, 각 노드는 `Completion` 객체다.

```java
abstract static class Completion extends ForkJoinTask<Void> {
    volatile Completion next;  // 다음 Completion을 가리키는 포인터
    
    abstract CompletableFuture<?> tryFire(int mode);
}
```

`thenApply()`, `thenAccept()`, `thenCompose()` 같은 메서드를 호출할 때마다, 새로운 `Completion` 객체가 생성되어 이 스택에 추가된다. 작업이 완료되면, 이 스택에 쌓인 모든 `Completion`들이 순차적으로 실행된다.

### CompletableFuture 객체의 생성

CompletableFuture 객체는 두 가지 방식으로 생성된다.

#### 방식 1: 명시적 생성 (수동 완료)

```java
CompletableFuture<String> future = new CompletableFuture<>();
// 이 시점에서 future는 미완료 상태
// result = null, stack = null

// 언젠가 외부에서 완료시킴
future.complete("결과값");
```

이 방식은 비동기 작업의 완료 시점을 외부에서 제어할 수 있게 한다. 예를 들어, 네트워크 콜백에서 응답이 도착했을 때 `complete()`를 호출하여 작업을 완료시킬 수 있다.

#### 방식 2: 정적 팩토리 메서드 (자동 완료)

```java
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    return "결과";
});
```

이 방식은 CompletableFuture 객체를 생성함과 동시에, 제공된 람다 함수를 별도 스레드에서 실행한다. 람다가 반환하면 자동으로 `complete()`가 호출된다.

---

## 핵심 메커니즘: supplyAsync()의 동작 원리

### supplyAsync() 메서드의 정의

```java
public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
    return asyncSupplyStage(ForkJoinPool.commonPool(), supplier);
}

public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier,
                                                     Executor executor) {
    return asyncSupplyStage(executor, supplier);
}
```

`supplyAsync()`는 정적 메서드다. 이것은 새로운 CompletableFuture 객체를 생성하고, 제공된 `Supplier` 람다를 지정된 Executor에서 비동기로 실행한다.

### asyncSupplyStage() 메서드의 내부 동작

실제 OpenJDK 소스 코드를 보면 다음과 같다.

```java
static <U> CompletableFuture<U> asyncSupplyStage(Executor e, Supplier<U> f) {
    // 1. 새로운 CompletableFuture 객체 생성
    CompletableFuture<U> d = new CompletableFuture<U>();
    
    // 2. AsyncSupply 작업 객체 생성 및 Executor에 제출
    e.execute(new AsyncSupply<U>(d, f));
    
    // 3. 즉시 CompletableFuture 반환 (작업은 별도 스레드에서 실행 중)
    return d;
}
```

이 메서드가 하는 일을 단계별로 분석하면:

#### 단계 1: CompletableFuture 객체 생성

```java
CompletableFuture<U> d = new CompletableFuture<U>();
```

이 시점에서 `d` 객체는 다음 상태다.

- `result = null` (미완료)
- `stack = null` (후속 작업 없음)

#### 단계 2: AsyncSupply 작업 생성

```java
e.execute(new AsyncSupply<U>(d, f));
```

`AsyncSupply`는 `Runnable`을 구현한 내부 클래스다. 이 객체는 두 가지를 포함한다.

- `d`: 완료시킬 CompletableFuture 객체의 참조
- `f`: 실행할 Supplier 람다

OpenJDK 소스 코드:

```java
static final class AsyncSupply<T> extends ForkJoinTask<Void>
        implements Runnable, AsynchronousCompletionTask {
    
    CompletableFuture<T> dep;  // 완료시킬 CompletableFuture
    Supplier<? extends T> fn;  // 실행할 람다
    
    AsyncSupply(CompletableFuture<T> dep, Supplier<? extends T> fn) {
        this.dep = dep;
        this.fn = fn;
    }
    
    public void run() {
        CompletableFuture<T> d;
        Supplier<? extends T> f;
        
        if ((d = dep) != null && (f = fn) != null) {
            dep = null;
            fn = null;
            
            if (d.result == null) {
                try {
                    // 람다 실행
                    T t = f.get();
                    // 결과로 CompletableFuture 완료
                    d.completeValue(t);
                } catch (Throwable ex) {
                    // 예외로 CompletableFuture 완료
                    d.completeThrowable(ex);
                }
            }
            d.postComplete();
        }
    }
}
```

`executor.execute(new AsyncSupply(...))`가 호출되면, Executor의 작업 큐에 이 AsyncSupply 객체가 삽입된다. 이것은 ExecutorService의 `workQueue.offer()` 메커니즘과 동일하다.

#### 단계 3: 워커 스레드에서 AsyncSupply.run() 실행

Executor의 워커 스레드 중 하나가 작업 큐에서 이 AsyncSupply 객체를 꺼내어 `run()` 메서드를 호출한다.

```java
public void run() {
    // 1. Supplier 람다 실행
    T t = fn.get();
    
    // 2. CompletableFuture 객체에 결과 설정
    dep.completeValue(t);
    
    // 3. 후속 작업들을 실행
    dep.postComplete();
}
```

**이 시점에서 중요한 사실:**

- `run()` 메서드는 **워커 스레드**에서 실행된다.
- `fn.get()`을 호출하는 주체는 워커 스레드다.
- 람다 내부에서 네트워크 I/O나 Thread.sleep() 같은 블로킹 작업이 있다면, 워커 스레드가 블로킹된다.

#### 단계 4: 메인 스레드는 즉시 진행

```java
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    Thread.sleep(1000);  // 워커 스레드가 블로킹
    return "결과";
});

System.out.println("작업 제출 완료");  // 메인 스레드는 즉시 여기 도달
```

메인 스레드는 `supplyAsync()`를 호출한 즉시 반환받는다. 실제 작업은 워커 스레드에서 진행 중이며, 메인 스레드는 블로킹되지 않고 다음 코드를 계속 실행한다.

---

## 작업 완료 메커니즘: completeValue()와 postComplete()

### completeValue(): 결과 설정

워커 스레드가 람다 실행을 완료하면, 그 결과를 CompletableFuture 객체에 저장한다.

```java
final boolean completeValue(T t) {
    return RESULT.compareAndSet(this, null, (t == null) ? NIL : t);
}
```

이 메서드는 CAS(Compare-And-Swap) 원자적 연산을 사용한다. `RESULT`는 `VarHandle`로, Java 9부터 도입된 low-level 메모리 접근 API다. 이것은 다음을 보장한다.

- 다른 스레드가 이미 `result`를 설정했다면, 이 CAS는 실패한다 (false 반환).
- 성공하면 `result` 필드에 값이 설정되고 true 반환.
- 모든 작업이 원자적으로 실행되어 Race Condition이 발생하지 않는다.

### postComplete(): 후속 작업 실행

결과가 설정되면, 이제 등록된 후속 작업들을 실행해야 한다.

```java
final void postComplete() {
    CompletableFuture<?> f = this;
    Completion h;
    
    while ((h = f.stack) != null) {
        // 스택에서 Completion 하나씩 꺼내기
        f.stack = h.next;
        h.next = null;
        
        // 해당 Completion 실행
        h.tryFire(NESTED);
    }
}
```

이 메서드는 `stack` 필드에 쌓인 모든 `Completion` 객체들을 순회하며 실행한다. 각 Completion은 `tryFire()` 메서드를 통해 실제 작업을 수행한다.

---

## 후속 작업 체이닝: thenApply()의 동작

### thenApply() 메서드

```java
CompletableFuture<Integer> future1 = CompletableFuture.supplyAsync(() -> 10);
CompletableFuture<String> future2 = future1.thenApply(n -> "결과: " + n);
```

이 코드가 실행될 때 정확히 무슨 일이 일어나는가?

### thenApply()의 내부 구현

OpenJDK 소스 코드:

```java
public <U> CompletableFuture<U> thenApply(Function<? super T,? extends U> fn) {
    return uniApplyStage(null, fn);
}

private <V> CompletableFuture<V> uniApplyStage(
        Executor e, Function<? super T,? extends V> f) {
    
    // 1. 새로운 CompletableFuture 생성 (결과를 담을 객체)
    CompletableFuture<V> d = new CompletableFuture<V>();
    
    // 2. 원본 future가 이미 완료되었는지 확인
    if (result != null) {
        // 이미 완료됨 → 즉시 함수 적용
        d.uniApply(this, f, null);
    } else {
        // 미완료 → Completion을 스택에 추가
        unipush(new UniApply<T,V>(e, d, this, f));
    }
    
    return d;
}
```

### 단계 1: 새로운 CompletableFuture 생성

```java
CompletableFuture<V> d = new CompletableFuture<V>();
```

`thenApply()`는 즉시 새로운 CompletableFuture 객체를 생성한다. 이 객체는 변환 함수를 적용한 결과를 담을 컨테이너다. 이 시점에서 `d.result = null` (미완료).

### 단계 2: 원본의 완료 상태 확인

```java
if (result != null) {
    // 원본이 이미 완료됨
} else {
    // 원본이 아직 미완료
}
```

**케이스 A: 원본이 이미 완료된 경우**

```java
CompletableFuture<Integer> future1 = CompletableFuture.completedFuture(10);
CompletableFuture<String> future2 = future1.thenApply(n -> "결과: " + n);
```

`future1`은 이미 완료된 상태이므로, `thenApply()`는 즉시 함수를 적용한다.

```java
d.uniApply(this, f, null);  // 즉시 실행

// uniApply()의 내부
final <S> boolean uniApply(CompletableFuture<S> a,
                           Function<? super S,? extends T> f,
                           UniApply<S,T> c) {
    S s = a.result;  // 원본 결과 가져오기 (10)
    T t = f.apply(s);  // 함수 적용 ("결과: 10")
    this.completeValue(t);  // 새 future에 결과 설정
    return true;
}
```

이 경우 모든 작업이 **호출 스레드(메인 스레드)**에서 동기적으로 실행된다.

**케이스 B: 원본이 아직 미완료인 경우**

```java
CompletableFuture<Integer> future1 = CompletableFuture.supplyAsync(() -> {
    Thread.sleep(1000);
    return 10;
});

CompletableFuture<String> future2 = future1.thenApply(n -> "결과: " + n);
```

`future1`이 아직 완료되지 않았으므로, `thenApply()`는 Completion 객체를 생성하여 `future1`의 스택에 추가한다.

```java
unipush(new UniApply<T,V>(e, d, this, f));

// UniApply는 Completion의 하위 클래스
static final class UniApply<T,V> extends UniCompletion<T,V> {
    Function<? super T,? extends V> fn;
    
    final CompletableFuture<V> tryFire(int mode) {
        CompletableFuture<T> a = dep;
        T t = a.result;  // 원본 결과 가져오기
        V v = fn.apply(t);  // 함수 적용
        dst.completeValue(v);  // 새 future에 결과 설정
        return dst;
    }
}
```

이제 `future1`의 스택에는 이 UniApply 객체가 추가되었다.

```
future1.stack → UniApply(fn: n -> "결과: " + n, dst: future2)
```

워커 스레드가 `supplyAsync()`의 람다를 완료하면:

1. `future1.completeValue(10)` 호출
2. `future1.postComplete()` 호출
3. 스택에서 UniApply를 꺼내서 `tryFire()` 실행
4. `fn.apply(10)` → "결과: 10"
5. `future2.completeValue("결과: 10")`

이 모든 과정이 **워커 스레드**에서 실행된다.

---

## 병렬 조합: allOf()의 동작 원리

### allOf() 메서드의 목적

```java
CompletableFuture<String> f1 = CompletableFuture.supplyAsync(() -> task1());
CompletableFuture<String> f2 = CompletableFuture.supplyAsync(() -> task2());
CompletableFuture<String> f3 = CompletableFuture.supplyAsync(() -> task3());

CompletableFuture<Void> all = CompletableFuture.allOf(f1, f2, f3);
all.join();  // 세 작업이 모두 완료될 때까지 대기
```

`allOf()`는 여러 CompletableFuture를 입력받아, "모든 작업이 완료되었을 때 완료되는 새로운 CompletableFuture"를 반환한다.

### allOf()의 내부 구현

OpenJDK 소스 코드:

```java
public static CompletableFuture<Void> allOf(CompletableFuture<?>... cfs) {
    return andTree(cfs, 0, cfs.length - 1);
}

static CompletableFuture<Void> andTree(CompletableFuture<?>[] cfs,
                                        int lo, int hi) {
    CompletableFuture<Void> d = new CompletableFuture<Void>();
    
    if (lo > hi) {
        // 빈 배열 → 즉시 완료
        d.result = NIL;
    } else {
        // 모든 future에 대해 BiRelay Completion 등록
        for (int i = lo; i <= hi; i++) {
            CompletableFuture<?> f = cfs[i];
            BiRelay<?,?> relay = new BiRelay<>(d, f);
            f.unipush(relay);
        }
    }
    
    return d;
}
```

### BiRelay의 동작 메커니즘

`BiRelay`는 "카운터"를 관리하는 Completion이다. 각 입력 future가 완료될 때마다 카운터를 감소시키고, 카운터가 0이 되면 결과 future를 완료시킨다.

```java
static final class BiRelay<T,U> extends BiCompletion<T,U,Void> {
    final CompletableFuture<Void> tryFire(int mode) {
        CompletableFuture<?> a = src;
        
        if (a == null || a.result == null) {
            // 아직 완료 안 됨
            return null;
        }
        
        // 원자적으로 카운터 감소
        if (decrementCount() == 0) {
            // 모든 작업 완료 → 결과 future 완료
            dst.completeNull();
        }
        
        return null;
    }
}
```

실제로는 더 복잡한 트리 구조로 구현되어 있지만, 핵심 아이디어는:

1. 각 입력 future의 스택에 BiRelay 추가
2. 각 future가 완료될 때마다 BiRelay의 `tryFire()` 호출
3. 모든 future가 완료되면 결과 future 완료

### allOf() 사용 후 결과 수집

```java
CompletableFuture<String> f1 = CompletableFuture.supplyAsync(() -> "A");
CompletableFuture<String> f2 = CompletableFuture.supplyAsync(() -> "B");
CompletableFuture<String> f3 = CompletableFuture.supplyAsync(() -> "C");

CompletableFuture<Void> all = CompletableFuture.allOf(f1, f2, f3);
all.join();  // 모든 작업 완료 대기

// 각 future에서 결과 추출
String r1 = f1.join();
String r2 = f2.join();
String r3 = f3.join();
```

`allOf()`는 `CompletableFuture<Void>`를 반환한다. 즉, 완료 여부만 알려주고 실제 결과는 담지 않는다. 개별 결과는 원본 future들(`f1`, `f2`, `f3`)에서 직접 추출해야 한다.

`all.join()` 시점에 모든 작업이 이미 완료되었으므로, 이후 `f1.join()`, `f2.join()`, `f3.join()`은 블로킹 없이 즉시 결과를 반환한다.

---

## join() vs get(): 결과 수집 방식

### join() 메서드

```java
public T join() {
    Object r;
    if ((r = result) == null) {
        // 미완료 → 블로킹 대기
        r = waitingGet(false);
    }
    return (T) reportJoin(r);
}
```

`join()`은 작업이 완료될 때까지 호출 스레드를 블로킹한다. 내부적으로 `LockSupport.park()`를 사용하여 스레드를 대기 상태로 전환한다.

### get() 메서드

```java
public T get() throws InterruptedException, ExecutionException {
    Object r;
    if ((r = result) == null) {
        r = waitingGet(true);  // 인터럽트 가능
    }
    return (T) reportGet(r);
}
```

`join()`과 `get()`의 차이:

**예외 처리:**
- `join()`: 언체크 예외(`CompletionException`)를 던진다.
- `get()`: 체크 예외(`ExecutionException`, `InterruptedException`)를 던진다.

**인터럽트 처리:**
- `join()`: 인터럽트를 무시한다.
- `get()`: 인터럽트 시 `InterruptedException`을 던진다.

실무에서는 람다 내부에서 사용할 때 예외 처리가 간편한 `join()`이 선호된다.

```java
List<String> results = futures.stream()
    .map(CompletableFuture::join)  // 예외 처리 불필요
    .collect(Collectors.toList());
```

---

## Executor 지정: 어느 스레드에서 실행되는가

### 기본 Executor: ForkJoinPool.commonPool()

```java
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> "결과");
```

이 코드는 `ForkJoinPool.commonPool()`을 사용한다. 이것은 JVM 전체에서 공유되는 ForkJoinPool 인스턴스이며, 스레드 수는 `Runtime.getRuntime().availableProcessors() - 1`개다.

8코어 CPU → 7개 스레드

이 풀은 CPU-bound 작업(계산 집약적 작업)에 최적화되어 있다. 하지만 I/O-bound 작업(네트워크 대기, DB 조회)에는 부적합하다.

### 커스텀 Executor 지정

```java
ExecutorService executor = Executors.newFixedThreadPool(15);

CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    // 이 람다는 executor의 워커 스레드에서 실행됨
    return kakaoApiService.getAddress(lat, lon);
}, executor);
```

두 번째 인자로 Executor를 전달하면, 해당 Executor의 스레드 풀에서 작업이 실행된다.

### thenApply()의 Executor

```java
CompletableFuture<Integer> f1 = CompletableFuture.supplyAsync(() -> 10, executor1);
CompletableFuture<String> f2 = f1.thenApply(n -> "결과: " + n);
```

`thenApply()`는 기본적으로 Executor를 지정하지 않는다. 이 경우 함수가 실행되는 스레드는:

- `f1`이 완료될 때 `f1`을 완료시킨 스레드에서 즉시 실행된다.
- 즉, `f1`의 워커 스레드가 람다를 완료한 직후, 같은 스레드에서 `thenApply()`의 함수를 실행한다.

만약 별도 스레드에서 실행하고 싶다면:

```java
CompletableFuture<String> f2 = f1.thenApplyAsync(n -> "결과: " + n, executor2);
```

`thenApplyAsync()`를 사용하면, 함수가 `executor2`의 스레드에서 실행된다.

---

## 예외 처리 메커니즘

### exceptionally(): 예외를 결과로 변환

```java
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    if (Math.random() > 0.5) {
        throw new RuntimeException("실패");
    }
    return "성공";
}).exceptionally(ex -> {
    log.error("오류 발생", ex);
    return "기본값";
});
```

`exceptionally()`는 예외가 발생했을 때만 실행되는 함수를 등록한다. 내부적으로는 `UniExceptionally` Completion이 스택에 추가된다.

```java
static final class UniExceptionally<T> extends UniCompletion<T,T> {
    Function<Throwable, ? extends T> fn;
    
    final CompletableFuture<T> tryFire(int mode) {
        CompletableFuture<T> a = dep;
        Object r = a.result;
        
        if (r instanceof AltResult) {
            // 예외로 완료됨
            Throwable x = ((AltResult) r).ex;
            T t = fn.apply(x);  // 예외 처리 함수 실행
            dst.completeValue(t);
        } else {
            // 정상 완료 → 그대로 전달
            dst.completeValue((T) r);
        }
        
        return dst;
    }
}
```

### handle(): 성공과 예외를 모두 처리

```java
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> task())
    .handle((result, ex) -> {
        if (ex != null) {
            return "실패: " + ex.getMessage();
        } else {
            return "성공: " + result;
        }
    });
```

`handle()`은 성공 결과와 예외를 모두 받는 BiFunction을 등록한다. 이것은 `exceptionally()`보다 더 유연하다.

---

## 실제 사용 패턴: 15개 카카오 API 병렬 호출

### 순차 실행 (현재 코드)

```java
String address = kakaoApiService.getAddress(lat, lon);           // 100ms
Map<String, List<...>> amenity = kakaoApiService.searchAll(...); // 300ms
Double rate = arrestRateRepository.findByAddr(gu).get().getRate(); // 20ms

// 총 시간: 420ms
```

### 병렬 실행 (CompletableFuture)

```java
ExecutorService executor = Executors.newFixedThreadPool(15);

// 1. 주소 변환 API 시작
CompletableFuture<AddressDto> addressFuture = CompletableFuture.supplyAsync(() -> {
    return kakaoApiService.getAddress(lat, lon);  // 100ms
}, executor);

// 2. 편의시설 API 시작 (주소와 독립적)
CompletableFuture<Map<String, List<...>>> amenityFuture = CompletableFuture.supplyAsync(() -> {
    return kakaoApiService.searchAllAmenities(lat, lon, radius);  // 300ms
}, executor);

// 3. 주소 완료 후 검거율 조회
CompletableFuture<Double> rateFuture = addressFuture.thenApplyAsync(address -> {
    String gu = extractGu(address.getRoadAddress());
    return arrestRateRepository.findByAddr(gu)
        .map(ArrestRate::getRate)
        .orElse(0.0);  // 20ms
}, executor);

// 4. 모든 작업 완료 대기
CompletableFuture<Void> allDone = CompletableFuture.allOf(
    addressFuture,
    amenityFuture,
    rateFuture
);

allDone.join();  // 블로킹 대기 (하지만 총 시간은 가장 긴 작업 기준)

// 5. 결과 수집
AddressDto address = addressFuture.join();  // 즉시 반환 (이미 완료)
Map<String, List<...>> amenity = amenityFuture.join();
Double rate = rateFuture.join();

// 총 시간: max(100ms + 20ms, 300ms) = 300ms
```

### 타임라인 분석

**순차 실행:**
```
Main Thread: [주소 100ms] → [편의시설 300ms] → [검거율 20ms]
             ↑ 블로킹        ↑ 블로킹            ↑ 블로킹
             
총 시간: 420ms
```

**병렬 실행:**
```
Worker-1: [주소 100ms] → [검거율 20ms]
Worker-2: [편의시설 300ms]
Worker-3: (유휴)
...
Main Thread: [supplyAsync 호출] → [다른 작업 가능] → [allOf.join() 블로킹 300ms]

총 시간: 300ms (주소 100ms + 검거율 20ms는 편의시설 300ms 안에 완료)
```

---

## Thread 상태 분석

### 순차 실행 시 Thread 상태

```
Main Thread (http-nio-8080-exec-1):
[0ms]    RUNNABLE (주소 API 호출 준비)
[1ms]    WAITING (네트워크 I/O 대기)
[100ms]  RUNNABLE (응답 수신)
[101ms]  WAITING (편의시설 API 대기)
[401ms]  RUNNABLE (응답 수신)
[402ms]  WAITING (검거율 DB 조회 대기)
[422ms]  RUNNABLE (완료)

유휴 시간: 419ms (대부분의 시간을 WAITING으로 소비)
```

### 병렬 실행 시 Thread 상태

```
Main Thread:
[0ms]    RUNNABLE (supplyAsync 호출)
[1ms]    RUNNABLE (다른 작업 수행 가능)
[300ms]  RUNNABLE (allOf.join() 호출)
[301ms]  WAITING (완료 대기)
[320ms]  RUNNABLE (완료)

Worker Thread 1 (pool-1-thread-1):
[0ms]    RUNNABLE (주소 API 호출)
[1ms]    WAITING (네트워크 대기)
[100ms]  RUNNABLE (응답 수신)
[101ms]  RUNNABLE (검거율 조회)
[102ms]  WAITING (DB I/O)
[122ms]  RUNNABLE (완료)

Worker Thread 2 (pool-1-thread-2):
[0ms]    RUNNABLE (편의시설 API 호출)
[1ms]    WAITING (네트워크 대기)
[300ms]  RUNNABLE (응답 수신)
[301ms]  유휴

Worker Thread 3-15:
유휴 상태 유지
```

### Context Switching 비용

각 Thread가 WAITING → RUNNABLE 상태로 전환될 때마다 Context Switching이 발생한다. 하지만 I/O-bound 작업에서는 이 비용이 무시 가능하다.

Context Switch 비용: 약 1-10μs (마이크로초)
네트워크 I/O 대기 시간: 약 100ms = 100,000μs

비율: 0.01% (무시 가능)

---

## 메모리 구조와 객체 생명주기

### CompletableFuture 객체의 메모리 위치

```java
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> "결과");
```

이 코드가 실행되면 힙 메모리에 다음 객체들이 생성된다.

**힙 메모리:**
- `CompletableFuture` 객체 (약 40 bytes)
  - `result` 필드 (8 bytes, 참조)
  - `stack` 필드 (8 bytes, 참조)
- `AsyncSupply` 객체 (약 32 bytes)
  - `dep` 필드 (CompletableFuture 참조)
  - `fn` 필드 (람다 참조)
- 람다 객체 (약 24 bytes)

**스택 메모리:**
- 메인 스레드 스택: `future` 변수 (8 bytes, 참조)
- 워커 스레드 스택: `AsyncSupply.run()` 메서드의 로컬 변수들

### 가비지 컬렉션

CompletableFuture가 완료되고 더 이상 참조되지 않으면, GC의 수거 대상이 된다.

```java
CompletableFuture<String> f = CompletableFuture.supplyAsync(() -> task());
String result = f.join();
f = null;  // 참조 제거

// 이 시점부터 CompletableFuture 객체는 GC 대상
```

---

## 결론: CompletableFuture가 제공하는 설계적 의의

CompletableFuture는 비동기 프로그래밍의 제어권을 개발자에게 완전히 이전했다. 

**Future의 한계:**
- 블로킹 방식의 결과 조회만 가능
- 작업 조합 불가능
- 완료 시점 콜백 부재

**CompletableFuture의 해결:**
- `thenApply()`, `thenAccept()`, `thenCompose()`: 비블로킹 체이닝
- `allOf()`, `anyOf()`: 선언적 작업 조합
- `exceptionally()`, `handle()`: 포괄적 예외 처리
- 외부 Executor 지정으로 Thread Pool 제어 가능

이 설계가 갖는 아키텍처적 의의는, **비동기 작업의 생명주기 관리가 "명시적 스레드 조율"에서 "선언적 작업 조합"으로 이동**했다는 것이다. 개발자는 더 이상 CountDownLatch, Semaphore, synchronized 같은 저수준 동기화 프리미티브를 직접 다루지 않으며, 작업 간 의존성을 메서드 체이닝으로 표현한다.

단, CompletableFuture는 여전히 Thread Pool + Blocking I/O 모델을 전제로 설계되었다는 한계가 있다. 워커 스레드가 네트워크 응답을 기다리며 블로킹되는 동안, 해당 스레드는 다른 작업을 처리할 수 없다. 이 한계를 극복하는 것이 Reactor Netty의 Non-blocking I/O와 Kotlin Coroutine의 Structured Concurrency다.

---

## 구글링 키워드

```
java completablefuture tutorial
completablefuture allof example
completablefuture custom executor
completablefuture exception handling
completablefuture vs future
completablefuture internal implementation
completablefuture thread pool size
completablefuture thencompose vs thenapply
```

---

## 실용 문서 링크

**1. Baeldung - CompletableFuture 완전 가이드**
```
https://www.baeldung.com/java-completablefuture
```
실전 예제 중심. supplyAsync, thenApply, allOf 사용법을 코드와 함께 제공.

**2. Oracle 공식 문서**
```
https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html
```
모든 메서드의 공식 명세. 각 메서드의 정확한 동작 보장과 예외 처리 규칙.

**3. OpenJDK 소스 코드**
```
https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/concurrent/CompletableFuture.java
```
실제 구현 코드. asyncSupplyStage(), postComplete(), tryFire() 메서드 확인 가능.

**4. Jakob Jenkov - CompletableFuture Tutorial**
```
http://tutorials.jenkov.com/java-util-concurrent/java-completable-future.html
```
그림과 함께 설명. Stage 체이닝과 완료 메커니즘 시각화.