# JDK 7 이전과 JDK 8 이후의 메서드 영역(PermGen vs Metaspace) 변화 정리

## 1. Method Area란?

- JVM 스펙에 정의된 **런타임 데이터 영역** 중 하나이다.
- 모든 쓰레드가 공유하는 영역이며, 클래스 수준의 메타데이터(클래스 이름, 상속 정보, 메서드 시그니처 등)를 저장
- Method Area는 **논리적인 개념**이며, 자바 버전에 따라 실제 구현체가 다르다.

| 자바 버전  | Method Area의 실제 구현체        |
| ---------- | -------------------------------- |
| JDK 7 이하 | PermGen (Permanent Generation)   |
| JDK 8 이상 | Metaspace (네이티브 메모리 기반) |

---

## 2. Method Area에 저장되는 것들

- 클래스/인터페이스 이름 및 버전
- 상속 관계·구현 관계
- 필드·메서드 메타데이터(시그니처, 접근 제어자 등)
- **static 변수의 정의 정보** (값은 아님)
- **Runtime Constant Pool**
  - 숫자·문자 상수, 심볼릭 참조 등을 담는 per-class 구조
  - _String Intern Pool_ 과는 다른 개념!
- 메서드 바이트코드
- 예외 테이블
- 클래스 로더 정보 등

> **static 변수의 값(value)** 과 **String Intern Pool**(문자열 리터럴로 만들어지는 `java.lang.String` 객체)은 모두 **Heap**에 저장된다.

---

## 3. JDK 7 이하 : PermGen

- JVM 내부의 **고정 크기**(–XX:MaxPermSize) 메모리 영역
- 메모리 영역이 고정 되어, 공간 부족 시 `OutOfMemoryError: PermGen space`
  - 해당 문제를 해결 하고자 했음
- **문자열 리터럴(String Intern Pool)** 은 Java 6까지 PermGen에 있었으나 **Java 7부터 Heap**으로 이동

| 항목                  | 저장 위치(JDK 7 이하) |
| --------------------- | --------------------- |
| 클래스 메타데이터     | PermGen               |
| static 변수 정의      | PermGen               |
| static 변수 값        | Heap                  |
| Runtime Constant Pool | PermGen               |
| String Intern Pool    | Heap (Java 7부터)     |

주요 JVM 옵션

- `XX:PermSize=<초기 크기>`
- `XX:MaxPermSize=<최대 크기>`

---

## 4. JDK 8 이상 : Metaspace

- **PermGen 제거 → Metaspace 도입**
- JVM 내부가 아닌 **OS 네이티브 메모리** 사용 → 기본값은 _동적 확장_  
  (상한은 `–XX:MaxMetaspaceSize`로 설정 가능)
- class unloading 시 사용하지 않는 메타데이터를 GC가 회수하고, 회수된 공간을 OS에 반환 가능

### 저장 위치 요약 (JDK 8 이상)

| 항목                  | 저장 위치(JDK 8 이상) |
| --------------------- | --------------------- |
| 클래스 메타데이터     | Metaspace             |
| static 변수 정의      | Metaspace             |
| static 변수 값        | Heap                  |
| Runtime Constant Pool | Metaspace             |
| String Intern Pool    | Heap                  |

### 주요 JVM 옵션

- `XX:MetaspaceSize=<초기 크기>`
- `XX:MaxMetaspaceSize=<최대 크기>`

---

## 5. Metaspace 도입으로 얻은 장점

1. **OutOfMemoryError(PermGen space) 완화**
   - 고정된 PermGen 대신, OS 메모리를 필요에 따라 가져와 사용 → 공간 부족 리스크 감소
   - 단, –XX:MaxMetaspaceSize 미설정 시 과도 사용 가능성이 있으므로 모니터링 필요
2. **더 간단한 튜닝**
   - PermGen 시절엔 초기·최대 크기를 모두 지정해야 했지만, Metaspace는 보통 _최대 크기만_ 관리하면 충분
3. **클래스 언로딩 효율 향상**
   - 언로드된 클래스의 메타데이터 블록을 즉시 OS에 반환 → 장기 실행 서버에서 메모리 파편화·누수 감소
4. **Compressed Class Pointers 지원**
   - `-XX:+UseCompressedClassPointers`(기본값 on) 로 64-bit JVM에서도 32-bit 포인터를 사용해 **메타데이터 메모리 절감**

> **주의** : Metaspace도 결국 제한된 native memory를 사용하므로 클래스 누수(classloader leak)가 심하면 `OutOfMemoryError: Metaspace` 가 여전히 발생할 수 있음  
> (JVM만 죽을 수도 있고, 상황에 따라 OS 전체가 메모리 부족으로 멈출 수도 있다.)
