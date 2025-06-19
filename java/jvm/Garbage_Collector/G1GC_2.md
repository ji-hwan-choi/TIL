# G1GC 심화 내용

## 1. Card Table과 RSet의 역할 정리

#### 개념 요약

- **Card Table**은 Old 영역의 객체가 **다른 객체를 참조할 때 변경이 일어난 위치를 추적하기 위한 테이블**이다.
  - Old 영역을 전체 탐색하지 않아도 되게 만든 메커니즘이다.
- **RSet(Remembered Set)** 은 각 Region이 **자기 자신을 참조하고 있는 외부 Region의 카드 정보(Card Index)를 저장하는 집합**이다.
- 이 두 구조 덕분에 GC는 **전체 Heap을 스캔하지 않고도** 필요한 참조 관계를 빠르게 탐색할 수 있다.
  - RSet은 Card Table 전체를 다 뒤지지 않기 위해 '요약본'을 유지하는 구조이다.

---

### Card란 무엇인가?

- Heap은 Region으로 나뉘고, **각 Region은 다시 Card라는 고정 크기(보통 512 byte)** 단위로 나뉜다.
- 이 카드 하나하나에 대해 객체 참조 변경이 일어났는지 여부를 `Card Table`이라는 별도 메모리 구조에 **"Dirty" 비트**로 기록한다.

### Card Table이란 무엇인가?

| 항목          | 설명                                                                                              |
| ------------- | ------------------------------------------------------------------------------------------------- |
| **정의**      | JVM이 Heap을 **작은 카드 단위(보통 512byte)** 로 나눈 후, 각 카드의 상태를 저장한 **바이트 배열** |
| **기능**      | 객체 참조가 변경되면, 해당 객체가 위치한 카드의 인덱스에 **Dirty 표시(0x01 등)**                  |
| **주 목적**   | **Old 객체가 어떤 Young 객체를 참조하는지**를 빠르게 추적                                         |
| **동작 시점** | 참조 필드 변경 시 JVM의 Write Barrier가 카드에 Dirty 표시                                         |

### Card Table이 필요한 이유: Old -> Young 참조 문제

Young GC는 Eden Region, Survivor Region의 Garbage만 수집한다.  
하지만 **Old Region에 있는 객체가 Young Region의 객체를 참조하는 경우**,  
이 Young 객체는 살아있는 객체로 간주되어야 한다. 이러한 참조 관계를 정확히 추적해야 한다.  
따라서 GC는 **Old Region 전체를 스캔해야 하는 상황**이 발생할 수도 있다.

**그러나,** Old 영역은 수 GB 이상일 수 있고, 전체를 다 스캔하면 시간이 너무 오래 걸린다.

이 문제를 해결하기 위해 **Card Table과 RSet(Remembered Set)** 을 사용한다.

Old Region의 객체가 Young Region의 객체를 참조하면, Write Barrier가 즉시 그 위치에 해당하는 Card를 'Dirty' 상태로 표시한다.  
이후 GC Refine Thread는 백그라운드에서 이 표시들을 확인하고, 어떤 Old 객체가 어떤 Young 객체를 참조하는지에 대한 정보를 Young Region의 RSet(Remembered Set)에 최종적으로 기록한다.

결과적으로 Young GC시 Old Region 전체를 스캔하지 않고, Young Region의 RSet만 확인해서 빠르게 GC가 가능해 진다.

> ### RSet은 어떤 역할을 하나?
>
> - 특정 Region이 **외부에서 참조받는 카드 정보(카드 인덱스)** 를 모아둔 **역방향 참조 인덱스**이다.
> - 이를 통해 **GC는 “나를 참조하고 있는 외부 객체들”을 빠르게 찾을 수 있다.**
> - 즉, RSet은 **Card Table을 전체 훑는 수고조차 줄여주는 인덱스** 역할을 한다.

---

### 정리

| 항목           | 설명                                                                                  |
| -------------- | ------------------------------------------------------------------------------------- |
| **Card Table** | 어떤 객체(보통 Old 영역)가 **다른 객체를 참조할 때** 해당 카드에 Dirty 비트를 남긴다. |
| **RSet**       | 나(이 Region)를 참조하는 외부 카드 정보를 따로 모아둔 **역방향 인덱스**다.            |
| **동작 순서**  | 참조 변경 발생 → Card Table에 Dirty → RSet에 카드 번호 추가                           |

---

### Young → Young 참조일 경우?

- Young 영역 내 객체가 다른 Young 객체를 참조하는 경우, 해당 참조 변경은 **Card Table에 Dirty 표시되지 않는다.**
- 어차피 Eden, Survivor 모두 **CSet에 포함되므로**, 서로 간의 참조는 그대로 GC 탐색 범위에 포함된다.
- 따라서 **추적하지 않아도 안전**하다.

---

### 결론 요약

- **객체가 다른 객체를 참조할 때**, GC가 효율적으로 살아 있는 객체를 찾기 위해 Card Table을 활용한다.
- **Card Table은 Old 객체가 Young 객체를 참조하는 경우**에만 Dirty 처리를 한다.
- Dirty 카드만 검사하면 되므로, **전체 Old 영역을 풀스캔하지 않아도 된다.**
- 이 Dirty 카드 정보를 Region별로 모아둔 것이 **RSet**이다.
- **RSet이 없다면** GC는 모든 Card Table을 뒤져야 한다.

---

## 2. CSet(Collection Set)이란 무엇인가?

- CSet은 한 번의 GC에서 수집(Clean)할 Region들의 집합이다.
- Young GC에서는:
  - **모든 Eden Region**
  - **모든 Survivor Region** 으로 구성된다..
- Mixed GC에서는 여기에 **일부 Old Region을 포함한다.**

#### Old Region이 CSet에 포함되는 기준

- **Concurrent Marking과 Cleanup 단계**에서 수집된 메타데이터 기반
- 대표적인 선정 기준:
  - **Live Ratio가 낮은 Region 우선**
    > → 죽은 객체가 많아 수집 효율이 높기 때문 (Garbage-First)
    > → 오랫동안 수집되지 않은 Region은 죽은 객체가 많이 쌓여 있을 확률이 높으므로, 결과적으로 수집 대상 후보에 포함되기 쉽다.

---

## 3. SATB(Snapshot-At-The-Beginning)란?

Concurrent Marking 중에 객체 참조가 변경되더라도, 변경 전의 객체가 **마킹에서 누락되어 잘못 수집되는 것을 방지하는 기술**이다.

이를 위해 객체 참조가 변경될 때마다 Write Barrier가 개입하여, **덮어 써지기 이전의 객체 참조를 SATB 버퍼에 저장**해 둔다.

이렇게 버퍼에 기록된 정보는 **Remark 단계에서 최종적으로 처리**되며, 버퍼에 있던 모든 객체들은 살아있는 것으로 간주하고 마킹을 완료한다.

### SATB의 특징

SATB는 마킹 시작 시점의 '스냅샷'을 기준으로 모든 객체의 생존을 보장한다. 이 때문에 마킹 도중 참조가 모두 사라져 명백한 쓰레기가 된 객체라도 이번 GC에서는 수집되지 않는데, 이를 **Floating Garbage(떠다니는 쓰레기)** 라 한다.

> 스냅샷의 기준이 되는 시점은 Initial Mark가 시작될 때 이다.

> **Write Barrier의 두 가지 방식**  
> 이처럼 SATB가 참조가 변경되기 '이전' 에 동작하는 것을 **Pre-Write Barrier**라 한다.  
> 반면, Card Table을 Dirty로 표시하는 작업은 참조가 변경된 '이후' 에 새로 생긴 값을 확인해야 하므로 **Post-Write Barrier**라고 부른다.  
> 이 둘은 G1 GC의 목적에 따라 다르게 동작하는 Write Barrier의 두 가지 핵심 방식이다.
