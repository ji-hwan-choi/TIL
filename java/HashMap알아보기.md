# HashMap 내부 구조 살펴보기

## HashMap 의 주요 필드

- `static final int DEFAULT_INITIAL_CAPACITY = 1 << 4;`

  - HashMap 내부 자료구조의 초기 설정 크기이다.
  - `new HashMap();` 시 **기본 크기는 16**이다.

- `static final int MAXIMUM_CAPACITY = 1 << 30;`

  - 내부 자료구조의 맥시멈 사이즈 이다.
  - 맥시멈 사이즈는 1,073,741,824 이다.

- `static final float DEFAULT_LOAD_FACTOR = 0.75f;`

  - 생성자에서 명시하지 않을 경우 해당 값으로 임계치를 계산한다.
  - 해당 값은 `loadFactor` 값에 담겨 사용된다.

- `final float loadFactor;`

  - 초기 임계치 계산 할때 쓰이는 값으로, 생성자에서 직접 명시하거나 명시하지 않을시 DEFAULT_LOAD_FACTOR 값이 기본으로 쓰인다.

- `int threshold;`

  - 설정된 임계치 값이다.
  - 최초에만`capacity * loadFactor`로 계산되고, 이후 `resize()`할 때는 `newThr = oldThr << 1;` 로 단순히 2배씩 증가한다.
  - `size` 값이 해당 임계치에 도달하면 내부 자료 구조의 크기를 키운다.
    > ```java
    > if (++size > threshold)
    >   resize();
    > ```

- `transient Node<K,V>[] table;`

  - HashMap 내부에서 사용되는 자료구조이다.
  - **table의 크기는 항상 2의 거듭제곱이다.**
  - 아래는 실제 주석 내용 번역이다.
    > 이 테이블은 처음 사용할 때 초기화되며, 필요에 따라 크기가 조정됩니다.
    > **할당될 때는 항상 길이가 2의 거듭제곱이 되도록 합니다.**
    > (또한 일부 내부 초기화 과정을 위해, 현재는 필요하지 않지만 일부 연산에서 길이가 0인 상태도 허용하고 있습니다.)

- `transient int size;`
  - `table`에 실제 존재하는 `Node`의 개수를 나타낸다.

---

## put(), putVal() 메서드

`map.put("key", "value");` 메서드를 호출하면,  
put 메서드 내부에서 다음과 같이 putVal 메서드를 호출한다.  
`putVal(hash(key), key, value, false, true);`  
내부 자료구조인 `table`에 노드를 생성해서 값을 넣어야 하는데, 몇번째 배열에 넣을지 계산을 해야한다.  
보통의 해시법은 `hash % capacity(table.length)` 계산으로 몇번째 배열에 넣을지 계산한다.  
하지만 `capacity`는 항상 2의 거듭제곱인 점을 이용해 `(capacity-1) & hash` 이와 같이 비트와이즈 연산을 통해 table의 몇번째 배열에 넣을지 계산한다.  
`capacity`의 값이 2의 거듭제곱일 시, `hash % capacity(table.length)` 와 `(capacity-1) & hash` 연산은 **완벽하게 동일한 결과**가 나온다.
이렇게 capacity가 2의 거듭제곱인 점을 이용해 **비트와이즈 연산을 하는 이유는 나머지 연산보다 속도가 훨씬 빠르기 때문**이다.  
결국 나머지 연산보다 더 좋은 성능을 내기 위해 capacity를 2의 거듭제곱으로 설정하는 이유이기도 하다.

### `hash % n` == `(n-1) & hash` 결과가 같은 이유

n이 2의 거듭제곱일 시, 나머지 연산은 다음과 같다.  
q == 몫  
r == 나머지  
`hash = n * q + r`  
hash=123, n=16일때 위 식에 대입하면 다음과 같다.  
`123 = 16 * 7 + 11`
**이때 `n`은 2의 거듭제곱**이니, `n * q` 의 하위 비트는 무조건 0이다.

> **하위 비트의 기준**  
> n이 2의 몇 제곱인지에 따라 결정된다.  
> 즉, n = 2^k 일 때 **k** 개의 하위 비트는 무조건 0 이다.  
> n \* q 는 q 를 왼쪽으로 k개수만큼 시프트(<< k) 한 결과이다.  
> 참고로 x에 10을 곱하면 x 뒤에 0을 붙이는 것과 같은 원리이다.

> 16 : 0001 0000  
> 32 : 0010 0000  
> 64 : 0100 0000  
> 112: 0111 0000

따라서 `r`의 비트가 1인것만 찾을 수 있다면, 나머지를 쉽게 구할 수 있게 된다.

> 123의 2진수 : 0111 1011  
> `r` (11) 의 2진수 : 1011

123의 하위 비트인 1011만 찾아내서 그 값을 10진수로 바꾸기만 하면 된다.  
그 공식이 바로 (n-1) & hash 인 것 이다.
n이 2의 거듭 제곱이니 n-1의 하위 비트는 무조건 1일 것이다.  
그 1값과 hash를 AND 연산하면 hash 하위비트의 1값만 나올 것 이다.

이렇게 나온 값이 나머지이니, `hash % n` 과 완벽하게 동일한 값이 나온다.

---

## resize() 메서드

아래와 같이, `size`값이 `threshold` 값을 넘기면 resize() 메서드를 호출한다.

```java
if (++size > threshold)
    resize();
```

`resize()` 메서드의 주요 동작은, `table`을 새로 만들어 기존 `table`의 값을 복사하는 것 이다. 새로 만든 table의 크기는 기존 table 길이에 2의 제곱을 한 크기로 한다.  
해당 메서드의 중요하다 생각되는 부분은 특정 인덱스에 노드가 여러개 있을때 처리하는 방식이다.
해당 코드는 다음과 같다.

```java
for (int j = 0; j < oldCap; ++j) {
    Node<K,V> e;
    if ((e = oldTab[j]) != null) {
        oldTab[j] = null;
        if (e.next == null)
            newTab[e.hash & (newCap - 1)] = e;
        else if (e instanceof TreeNode)
            ((TreeNode<K,V>)e).split(this, newTab, j, oldCap);
        else { // oldTab[j] 에 여러개의 노드가 있을때 //
            Node<K,V> loHead = null, loTail = null;
            Node<K,V> hiHead = null, hiTail = null;
            Node<K,V> next;
            do {
                next = e.next;
                if ((e.hash & oldCap) == 0) { // 기존 인덱스 사용
                    if (loTail == null)
                        loHead = e;
                    else
                        loTail.next = e;
                    loTail = e;
                }
                else { // 새로운 인덱스 사용
                    if (hiTail == null)
                        hiHead = e;
                    else
                        hiTail.next = e;
                    hiTail = e;
                }
            } while ((e = next) != null);
            if (loTail != null) {
                loTail.next = null;
                newTab[j] = loHead;
            }
            if (hiTail != null) {
                hiTail.next = null;
                newTab[j + oldCap] = hiHead;
            }
        }
    }
}
```

핵심 조건을 뜯어보자.

## `if ((e.hash & oldCap) == 0)`

위 조건이 참이면 기존과 같은 인덱스 (`j`)를 재사용 한다.  
위 조건이 참인 노드들 끼리 연결하여, 새로운 table의 기존 인덱스 버킷에 넣어야 한다.

거짓이면 새로운 인덱스를 계산해야 한다.  
위 조건이 거짓인 노드들 끼리 연결하여, 새로운 table의 새로운 인덱스로 버킷에 넣어야 한다.

새로운 인덱스를 구한 다음 기존 인덱스와 비교하면 되는데,  
왜 조건식이 아래 두 개중 1번일까?

1. `if ((e.hash & oldCap) == 0) {...}`
2. `if (hash & (newCap - 1) == j) {...}`

그 이유를 예시와 함께 알아보자.

```java
// 예시 값
oldCap = 16
newCap = 32
hash = 321
```

`(e.hash & oldCap) == 0` 이 연산과 똑같은 다른 표현 방법은  
`hash & (newCap - 1) == j` 이와 같다. 이걸 더 풀어 보면  
`hash & (newCap - 1) == hash & (oldCap - 1)` 이렇게 된다.  
아직까진 단순히 새로운 인덱스와 기존 인덱스를 비교하는 조건문이다.

`hash`, `newCap - 1`, `oldCap - 1` 을 각각 2진수로 표현하면 다음과 같다.

```
     (hash) 321 : 0100 0001

(newCap - 1) 31 : 0001 1111
(oldCap - 1) 15 : 0000 1111
```

31과 15는 4번째 비트가 0이냐 1이냐의 차이 이고, 그 외 비트는 모두 1이라는 걸 알 수 있다.

`hash & (newCap - 1) == hash & (oldCap - 1)`  
그렇다면 이 두개의 AND 연산에서 핵심은 hash(321)의 4번째 비트가 1이냐 0이냐로 조건이 참이냐 거짓이냐 나뉘게 된다.

```
hash & (newCap - 1)
  0100 0001
& 0001 1111
  ---------
  0000 0001
```

```
hash & (oldCap - 1)
  0100 0001
& 0000 1111
  ---------
  0000 0001
```

hash 의 4번째 비트가 0이므로 두 결과는 동일하게 나온다.  
결국, **hash의 4번째 비트가 0이냐, 1이냐만 찾으면 기존 인덱스와 동일한지, 새로 계산해야 되는지 알 수 있게 된다.**  
4번째 비트가 0인지, 1인지 찾기 위해선 hash 값에 4번째 비트만 1로 두고, AND 연산을 하면 된다.

그 계산식이 바로, `hash & oldCap` 인 것 이다.  
`oldCap` 은 0001 0000 이니, `hash` AND 연산을 하면 4번째 비트가 0인지, 1인지 알 수 있게 된다.

> 이러한 연산이 가능하려면 반드시 **capacity의 값은 2의 거듭제곱이어야 하는 것 이다.**

---

## `newTab[j + oldCap]`

새로운 인덱스를 계산하려면 `newTab[hash & (newCap - 1)]` 을 해야되지만 자바에선  
`newTab[j + oldCap]` 연산을 사용한다.

이것이 가능한 이유는 다음과 같다.  
우선 기존 인덱스에 `oldCap`을 더할 수 있는건 상위 조건문에서 기존 인덱스와 동일하지 않다는 것을 확정 지은 상태이기 때문에 다음 설명이 가능하다.  
(`if ((e.hash & oldCap) != 0)`)

`j` 를 풀어보면 `(hash & (oldCap - 1))` 이 된다.  
`oldCap - 1` 은 0000 1111 이고,  
`newCap - 1` 은 0001 1111 이다.  
두 값의 차이는 0001 0000 이다.

즉 기존 인덱스 구하는 `(hash & (oldCap - 1))` 연산과  
새로운 인덱스 구하는 `hash & (newCap - 1)` 연산의 차이는 무조건 있을 수 밖에 없는 상태이고,
그 차이는 0001 0000 차이만 난다. 0001 0000 은 `oldCap` 이다.  
그러니 단순하게 기존에 구한 인덱스에 `oldCap` 만 더해주면 `hash & (newCap - 1)` 값과 동일한 결과가 나오는 것 이다.

우선 현재 조건식이 가능한 이유는 앞선 조건식이 false이기 때문에 가능하다.
(e.hash & oldCap) == 0 이게 false 이면 hash 의 2진수가 oldCap이 1인 부분의 값이 1인 것이다.
그럼 hash & (newCap - 1) , hash & (oldCap - 1) 두 결과의 차이는 oldCap인 것이다.
여기서 hash & (oldCap - 1)는 j 이니, 여기에 oldCap만 더해주면 hash & (newCap - 1) 이것과 동일한 결과가 나온다.
