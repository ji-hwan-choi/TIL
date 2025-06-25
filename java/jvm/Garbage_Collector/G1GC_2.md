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
- **Card Table은 Old 객체가 다른 객체를 참조하는 경우** Dirty 처리를 한다.
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

> ### Write Barrier의 두 가지 방식
>
> - **Pre-Write Barrier**:  
>   SATB에서는 객체의 참조가 변경되기 **이전**에 기존 참조 값을 기록해야 하므로,  
>   **변경 전의 값을 SATB 버퍼에 저장하는 작업**을 Pre-Write Barrier라고 한다.
>
> - **Post-Write Barrier**:  
>   Card Table 기반 추적에서는 참조가 변경된 **이후**에 **새로운 값이 어떤 Region을 가리키는지 확인**해야 한다.  
>   이 작업은 **카드를 Dirty로 표시**하는 방식으로 이루어지며, 이를 Post-Write Barrier라고 부른다.

---

## 4. TAMS(Top At Mark Start)란?

### TAMS(Top at Mark Start)

Old Region은 각각 **Top 포인터(Top Pointer)** 를 갖고 있으며, 이는 해당 Region에서 **다음에 객체가 할당될 위치**를 가리킨다.  
**Initial Mark** 시점에 이 Top 포인터의 값을 기록해 두는 기준선을 **TAMS(Top at Mark Start)** 라고 한다.  
이 기준선 이후에 새롭게 할당되는 객체는 **Above TAMS**, 그 이전에 이미 존재하던 객체는 **Below TAMS**로 구분된다.

마킹이 시작된 이후 해당 Old Region에 새로 할당된 객체(Above TAMS)는, 설령 이후에 참조가 끊기더라도 **이번 GC에서는 살아있는 객체로 간주**된다.  
이는 **보수적인 생존 판단 방식**으로, 실제로는 이미 쓰레기인 객체임에도 마킹 대상에서 제외되지 않아 **Floating Garbage**가 발생할 수 있다.

이러한 개념이 필요한 이유는, **Concurrent Marking 도중에 Young GC가 발생하여 객체가 Old Region으로 승격될 수 있기 때문**이다.  
Old Region에 있는 객체 A가 Young Region에 있는 B 객체를 참조하고 있다.  
B 객체는 Young GC로 인해 Old Region으로 승격 되었고, A 객체가 이미 마킹이 끝났을 경우 B 객체는 마킹 누락이 될 수 있다.  
이러한 마킹 누락을 없애기 위해 Top Pointer 이후의 객체는 모두 생존 시키려는 것이다.

> G1GC는 TAMS를 기준으로 설정하고, **Above TAMS에 위치한 객체는 모두 살아 있는 것으로 간주**함으로써 **안전성을 확보**한다.

---

## 5. **Tri-color Marking 이란?**

**Tri-color Marking은 가비지 컬렉터가 객체의 생존 여부를 판단하기 위해, 힙 내 객체들을 마킹 과정에서 세 가지 색(White, Gray, Black)으로 구분하여 관리하는 추상적 모델이다.**

이 기법은 마킹 도중 객체 간 참조 관계가 변할 수 있는 상황에서도 생존 객체의 누락을 방지하기 위해 고안되었으며, 다음과 같은 색상 규칙을 기반으로 동작한다:

- **White**: 아직 마킹되지 않은 객체. 생존 여부가 불확실하며, 최종적으로 수집 대상이 된다.
- **Gray**: 마킹은 되었으나, 이 객체가 참조하는 다른 객체들에 대한 마킹은 아직 이루어지지 않은 상태.
- **Black**: 자신과 참조하는 모든 객체들에 대한 마킹이 완료된 상태. 이 객체는 더 이상 확인하지 않는다.

Tri-color 모델은 “**검은색 객체는 흰색 객체를 참조하면 안 된다**”는 불변 조건(invariant)을 중심으로 구성되며, 이 invariant를 유지함으로써 가비지 컬렉션의 안정성과 정확성을 보장한다.

> ### Tri-color Marking Best 시나리오 요약
>
> 1. GC Root → A → B → C → D
> 2. GC Root에서 시작하여 객체 A를 Gray로 마킹.
> 3. A가 참조하는 B를 Gray로 마킹하고, A는 Black으로 전환.
> 4. B가 참조하는 C를 Gray로 마킹하고, B는 Black.
> 5. C가 참조하는 D를 Gray로 마킹하고, C는 Black.
> 6. 마지막 D도 참조가 없으면 Black으로 마킹 완료.
>
> 모든 **살아있는** 객체는 정확한 참조 순서대로 Gray → Black 처리되므로,  
> **살아있음에도 누락되는 객체는** 발생하지 않는다.
>
> ---
>
> ### SATB 개입 시나리오
>
> 조건: GC는 Gray → Black으로 객체를 마킹 중이고, 마킹 도중 애플리케이션 스레드가 참조를 변경함.  
> 초기: GC Root → A → B → D (모두 White, A부터 마킹 시작)
>
> 1. GC가 A를 Gray로 마킹하고 스캔 중
> 2. A가 참조하던 B는 아직 White 상태
> 3. 이때 애플리케이션 스레드가 A → B 참조를 끊고 C로 바꿈 (앱 스레드: A.b = C)
> 4. A는 Black으로 전환됨 (이제 B를 더 이상 스캔하지 않음)
> 5. B는 여전히 White이며, GC에서 누락 위험 발생
> 6. 하지만, SATB Pre-Write Barrier가 A의 이전 참조 대상 B를 Buffer에 저장해둠
> 7. GC는 SATB Buffer에 있는 B를 Gray로 다시 추가하여 마킹 진행
> 8. 결국 B와 B가 참조하는 D도 정상적으로 Gray → Black 처리됨
>
> Tri-color invariant(Black → White 금지)를 SATB가 보조함으로써 유지하고, 누락을 막는다.
>
> ---
>
> ### TAMS 이용 시나리오
>
> 1. Mark 시작 – GC Root → A
>    - GC Root가 A를 참조
>    - A → Gray → Black
>    - 이때 Old Region의 Top을 저장하여 TAMS 설정
> 2. Concurrent Mark 중 Young GC 발생 → C가 Old로 승격됨
>    - A(이미 Black)가 C를 참조 (A.c = C)
>    - C는 TAMS 이후 생성된 객체이므로 White 상태지만 마킹 대상 아님
> 3. C는 색칠되지 않음 → 그러나 GC는 수집하지 않음
>    - C는 Above TAMS이므로 살아있는 것으로 간주
>    - A(Black) → C(White) 참조지만 Tri-color invariant 위반 아님
>
> ---
>
> ### 참조 관계 중간에 Young Object가 있을 경우
>
> GC Root → A → B → C → D
>
> - A, B, D는 Old 영역
> - **C는 Young 영역**
>
> 1.  **Initial Mark & Concurrent Mark (객체 그래프 탐색)**
>
>     - Initial Mark: GC Root가 직접 참조하는 A를 **Gray**로 마킹한다.
>     - Concurrent Mark: Gray 상태인 A를 탐색하여 B를 **Gray**로 마킹하고, A는 **Black**으로 변경한다. 이어서 B를 탐색하고 더 이상 Old 영역 참조가 없으므로 B도 **Black**으로 변경한다.
>     - **결과: GC Root에서 시작된 직접 탐색은 B에서 멈춘다.**
>
> 2.  **Young Region 처리 방침**
>
>     - Concurrent Marker는 B가 C(Young)를 참조하는 것을 알지만, 동시성 문제를 피하기 위해 **Young 영역으로 직접 들어가 마킹하지 않는다.**
>     - 따라서 이 탐색 경로만으로는 C와 D가 모두 **White** 상태로 남게 된다.
>
> 3.  **경로 누락 문제 발생**
>
>     - GC의 직접 탐색 경로에서는 C를 건너뛰었기 때문에, **C → D라는 중요한 참조를 발견할 수 없다.**
>
> 4.  **Write Barrier와 RSet의 역할**
>
>     - (사전에) C → D 참조가 생성될 때, Write Barrier가 이를 감지하여 **D가 속한 Old Region의 RSet에 이 참조 정보를 기록해 두었다.**
>     - 이 RSet 기록 덕분에 D는 'Young 영역에서 들어오는 참조가 있음'이 증명된 상태이다.
>
> 5.  **Concurrent Mark의 RSet 스캔과 D의 생존**
>     - GC는 **바로 이 Concurrent Marking 단계에서**, Old Region들의 RSet을 스캔한다.
>     - D의 RSet을 스캔하다가 C로부터의 참조 기록을 발견한다.
>     - 이 기록을 근거로 **D를 즉시 Gray로 마킹하여 생존을 보장한다.**

---

## 5. Concurrent Marking Cycle 전체 흐름 정리

G1 GC는 Old Region의 점유율이 전체 힙의 특정 임계값(Initiating Heap Occupancy Percent)을 초과하면 Concurrent Marking Cycle을 시작한다.  
이 Cycle의 시작 여부는 주로 Young GC를 계기로 체크되며, 조건을 만족하면 Young GC의 STW(Stop-the-world)에 편승하여 첫 단계를 시작한다.

이 전체 과정은 살아있는 객체를 식별하기 위해 Tri-color Marking(White, Gray, Black)이라는 추상적인 모델을 기반으로 동작한다. 모든 객체는 초기에 '쓰레기 후보'인 White 상태에서 시작한다.

Initial Marking 단계에서는 GC Root가 직접 참조 중인 객체들만 Gray로 마킹한다. Gray는 '살아있음이 확인되었으나, 이 객체가 참조하는 다른 객체들은 아직 탐색하지 않은 상태'를 의미한다.  
이 작업은 Young GC의 STW 상태에서 함께 수행되는데, 이는 STW를 효율적으로 재사용하고 Young GC에서도 어차피 GC Root를 탐색하므로 중복 작업을 피하기 위함이다.

Initial Marking이 끝나면, 애플리케이션 스레드와 동시에 Concurrent Marking이 진행된다.

이 단계의 목표는 Gray 객체들을 탐색하여 그들이 참조하는 White 객체를 찾아내고, 이들을 다시 Gray로 만드는 작업을 반복하는 것이다.  
탐색을 마친 Gray 객체는 '완전히 확인된 생존 객체'인 Black으로 변경된다.

전체 Old Region의 객체 그래프를 탐색하며 살아있는 객체를 식별(마킹)하는 이 추적 간에, 애플리케이션 스레드가 객체 참조를 변경하면 Marking 누락이 발생할 수 있다. (예: Black 객체가 White 객체를 새롭게 참조하는 경우)

이런 누락을 방지하기 위해 SATB(Snapshot-At-The-Beginning) 기법이 있다.  
애플리케이션 스레드에서 참조 상태를 변경할 때, Write Barrier가 개입해서 변경되기 전 객체를 SATB 버퍼에 저장한다.

SATB 버퍼에 저장된 객체들은 나중에 '살아있을 가능성이 있는 객체'로 간주되어, 다시 Gray로 마킹되어 탐색 대상에 포함된다.  
이렇게 함으로써 정말 삭제하면 안 될 객체가 누락되는 것을 방지한다.

대신 참조가 이미 끊긴 객체도 생존 객체로 오인될 수 있다. 이런 객체를 **Floating Garbage(떠다니는 쓰레기)** 라 하며, G1 GC의 보수적인 처리 방식에 해당한다.

또한, Concurrent Marking 중 Young GC가 실행될 때 Old Region으로 승격되는 객체도 마킹에서 누락될 위험이 있다. 이런 누락도 방지하기 위해 TAMS(Top-At-Mark-Start) 기법이 있다.  
각 Old Region은 '다음 객체가 할당될 위치'를 가리키는 Top Pointer를 갖고 있는데, TAMS는 Initial Mark 직후 이 Top Pointer의 값을 기록해 둔 **'기준선(Boundary Line)'** 이다.

이 기준선 정보를 통해, Concurrent Marking 중 Old Region에 새로 승격된 객체도 "새롭게 들어온 것"임을 알 수 있어서, 해당 객체를 마킹 과정과 상관없이 무조건 살아있는 것으로 간주하여 안전성을 확보한다.  
TAMS 이후 들어오는 객체를 Above TAMS, 이전에 이미 존재하던 객체는 Below TAMS라고 부른다.

Concurrent Marking이 끝나면 Remark 단계가 시작된다. 이때는 잠깐의 STW 상태가 발생하며, 이 시간을 이용해 SATB 버퍼에 있는 모든 객체와 그 자손들을 마킹하여 살아있는 객체 식별을 최종 완료한다.

이 단계의 최종 목표는 힙 내에 더 이상 Gray 상태의 객체가 남아있지 않도록 하는 것이다. 모든 탐색이 끝나면 힙에는 Black 객체(생존)와 White 객체(쓰레기)만 남게 된다.

그 다음 Cleanup 단계가 진행된다. 이 단계는 대부분 애플리케이션 스레드와 병행하여 실행되지만, 일부 작업은 STW에서 수행된다.

이 단계에서는 Remark까지의 정보를 바탕으로, 마킹이 끝난 후에도 여전히 White 상태로 남아있는 모든 객체를 '쓰레기'로 간주한다.

이를 통해 어떤 Region에 쓰레기가 가장 많은지를 파악하고, 100% 쓰레기로 가득 찬 Region(즉, 모든 객체가 White인 Region)은 즉시 정리된다. (참조가 없는 Humongous 객체 포함)

마지막으로 STW와 함께 Mixed GC가 시작된다. 모든 Young Region과, Cleanup 단계에서 수집된 정보를 기준으로 '쓰레기가 많아 가장 효율적인' 일부 Old Region이 **CSet(Collection Set)** 으로 선정된다.  
GC는 CSet으로 선정된 Region들 안에서 살아있는 것으로 판명된 객체들(즉, Black으로 마킹되었던 객체들)만 새로운 Region으로 복사(Evacuation)하고, 기존 Region들은 비워내면서 사이클을 마무리한다.
