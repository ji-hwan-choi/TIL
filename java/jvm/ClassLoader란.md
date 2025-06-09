# JVM의 ClassLoader 정리

> 자바에서 클래스는 프로그램 실행 시점에 **필요할 때마다 동적으로 메모리에 로딩**되며, 이 과정을 담당하는 것이 `ClassLoader`이다.  
> JVM은 클래스 로딩 시 **부모 클래스 로더에게 먼저 로딩을 위임하는 방식**인 <strong>Parent Delegation Model(부모 위임 모델)</strong>을 사용한다.  
> 이 모델은 핵심 클래스가 임의로 재정의되는 것을 방지하고, JVM 전체에서 **일관된 클래스 정의와 보안성**을 보장하기 위해 설계되었다.

---

## 1. ClassLoader의 종류

JVM에는 대표적으로 다음 세 가지 주요 ClassLoader가 존재한다.

### 1. **Bootstrap ClassLoader**

- JVM에 내장된 **native 코드**로 구현되어 있다.
- **Java 8까지**는 `$JAVA_HOME/lib/rt.jar` 등 핵심 JAR 파일을 로드한다.
- **Java 9 이상**에서는 `$JAVA_HOME/lib/modules` 파일에서 `java.base`, `jdk.internal.*` 등의 **핵심 모듈을 로드**한다.
- 예시 클래스: `java.lang.String`, `java.util.List`, `java.io.InputStream` 등

### 2. **Platform ClassLoader** (Java 9 이상)

> Java 8까지는 `Extension ClassLoader`

- 암호화, XML, 사운드, 보안 등 **플랫폼 API를 제공하는 모듈을 로드**한다.
- Java 8 기준: `JAVA_HOME/lib/ext/*.jar`의 JAR 파일들을 로드
- **Java 9 이상**: `$JAVA_HOME/lib/modules` 내부의 `java.xml`, `java.sql`, `jdk.crypto.ec` 등의 모듈을 로드

### 3. **Application ClassLoader**

- 개발자가 작성한 **애플리케이션 코드 및 외부 라이브러리**를 로드한다.
- 로딩 대상:
  - `-classpath` 또는 `-cp` 옵션에 지정된 디렉토리 및 JAR
  - 또는 `--module-path`를 통해 지정된 사용자 모듈
  - 일반적으로 현재 디렉토리 (`.`) 포함

---

## 2. Parent Delegation Model (부모 위임 모델)

클래스를 로드할 때, ClassLoader는 다음과 같은 절차를 따른다:

1. **이미 로드된 클래스인지 확인**  
   → `findLoadedClass(String name)` 호출

2. **부모에게 로딩 위임**  
   → 현재 ClassLoader는 자신의 부모에게 `loadClass()` 호출  
   → 이 위임은 재귀적으로 최상위인 **Bootstrap ClassLoader**까지 이어진다

3. **부모가 차례로 로딩 시도**  
   → 최상위인 **Bootstrap ClassLoader부터 순차적으로 로딩을 시도**  
   → 클래스가 자신의 책임 범위에 없다면 `ClassNotFoundException`을 던지고 다음 부모로 제어가 넘어감

4. **최종적으로 자식이 직접 로딩 시도**  
   → 모든 부모가 **\*로딩 실패**시, 자식이 `findClass()`를 통해 직접 로드 시도

---

### ❗ "로딩 실패"란 무엇인가?

각 ClassLoader는 <strong>자신의 책임 영역(모듈 또는 JAR 경로)</strong>에 존재하는 클래스만 로드할 수 있다.  
책임 범위에 없으면 `ClassNotFoundException`을 던진다.

### 예시: `javax.crypto.Cipher` 클래스 로딩

```text
1. ApplicationClassLoader.loadClass("javax.crypto.Cipher") 호출됨
2. 자신이 로드한 적 없으니 → PlatformClassLoader에게 위임
3. PlatformClassLoader는 → BootstrapClassLoader에게 다시 위임
4. BootstrapClassLoader는 → 해당 클래스가 자신의 책임 영역이 아님을 판단하고 → ClassNotFoundException 던짐
5. PlatformClassLoader가 자신의 모듈(jdk.crypto.ec)에서 직접 로딩 시도
6. 클래스 로딩 성공 → Class 객체 반환
```

---

## 3. 왜 Parent Delegation Model을 사용할까?

| 목적       | 설명                                                                       |
| ---------- | -------------------------------------------------------------------------- |
| **보안성** | 사용자 코드가 java.lang.String 같은 JVM 핵심 클래스를 덮어쓰지 못하게 막음 |
| **일관성** | JVM 전역에서 항상 동일한 클래스 정의를 사용하도록 보장                     |
| **안정성** | 중복 로딩, 충돌을 방지하고 예측 가능한 로딩 순서를 유지                    |

---

## 4. Java 9에서 Extension ClassLoader → Platform ClassLoader로의 전환

### 배경: Java 9의 큰 변화 — JPMS 도입

Java 9에서 도입된 **모듈 단위의 컴포넌트 시스템이다.**  
기존에는 자바 애플리케이션이 모든 클래스를 **classpath에 JAR 단위로 나열**해서 사용했지만,  
이제는 **module 단위로 구성하고, 명시적으로 의존 관계를 선언**할 수 있도록 바뀌었다.

JPMS는 자바 생태계의 다음과 같은 문제를 해결하고자 도입되었다.

- 클래스 충돌 및 중복 로딩 문제
- 명시적 의존성 관리 부재
- 패키지 수준 접근 제어 한계
- JDK 자체의 비대화

---

### JPMS 도입 전후 구조 비교

| 항목                 | Java 8 이전 (Classpath + JAR)       | Java 9 이상 (JPMS + Module)    |
| -------------------- | ----------------------------------- | ------------------------------ |
| 코드 단위            | JAR 파일                            | Module 단위                    |
| 의존성 관리          | 암시적 (순서에 의존)                | 명시적 (`module-info.java`)    |
| 패키지 공개 범위     | `public`클래스는 어디서든 접근 가능 | `exports`된 패키지만 접근 가능 |
| 내부 클래스 접근     | 가능 (리플렉션 포함)                | `opens`된 경우에만 허용        |
| 확장 라이브러리 구조 | `lib/ext/*.jar`에 자동 포함         | platform module로 모듈화       |
| JDK 구성             | monolithic JAR (rt.jar 등)          | 모듈화된 구조 (`lib/modules`)  |

---

### Extension ClassLoader 제거 이유

**모든 JDK 구성 요소를 모듈화**하기 위해 기존 확장 로딩 방식(ext 디렉토리)을 제거하고,  
**JPMS에 통합된 platform module을 로드하는 새로운 ClassLoader가 필요했기 때문이다.**

#### Java 8까지의 Extension ClassLoader

- `lib/ext/*.jar` 경로의 JAR 파일을 자동 로딩
- 보안적으로 취약: **외부 JAR을 해당 위치에 넣으면 JVM이 자동으로 로딩**
- 클래스 충돌이나 중복 정의를 방지할 수 있는 장치 없음

#### Java 9부터 제거된 이유

- JPMS로 인해 JDK 자체가 모듈화됨
- JDK 내부 API들도 모두 `java.xml`, `java.sql`, `jdk.crypto.ec` 등으로 모듈화
- 확장 기능도 이제는 **모듈로 선언된 platform module**로 관리
- 더 이상 "ext 디렉토리" 기반 로딩 구조는 불필요하게 됨

---

### Platform ClassLoader의 등장

#### Java 9에서 새롭게 추가된 ClassLoader

- JPMS 환경에서 **platform module**을 로딩하는 전용 ClassLoader
- `Extension ClassLoader`를 대체함
- 예시 모듈:
  - `java.xml`, `java.sql`, `java.desktop`, `jdk.crypto.ec`, `javax.activation` 등
- 사용자가 직접 접근 가능:
  ```java
  ClassLoader platformCL = ClassLoader.getPlatformClassLoader();
  ```
