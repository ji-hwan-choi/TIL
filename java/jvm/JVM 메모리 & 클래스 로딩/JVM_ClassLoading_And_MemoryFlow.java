// JVM 어떤 구성 요소를 가지고 있는지
// JVM이 메모리를 어떤 식으로 구분하고 있는지
// class A는 어떤 식으로 로드되는지에 대해서 
// classloader란? <<< 이거 자세히 설명
class A { //어떤 메모리 영역에 로드 되는지
  private static final String STR = "ABC";
  private static Long l = Long.valueOf(-1L);
  private static int i = -1;
  private static C c = new C();

  private final String a = "ABC";
  private final int ii = 1;
  private C cc = new C();

  public static void main(...) { // main() 이 어떻게 실행이 되는지
    // JVM Memory에서 무슨 일이 일어나는지 설명해 봅시다.
    A a = new A(); // 힙이라는 공간에서 어느 정도의 메모리가 할당되고, 그 공간들이 어떤 식으로 초기화가 되는지
    B b = new B();
  }
}

class B extends A {
  // ...
}

class C {
  // ...
}