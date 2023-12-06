# Virtual Thread Refactor Helper

## Features

### 1. Refactor synchronized methods

* Refactor non-static synchronized methods using a private ReentrantLock.
    * before refactor
      ```java
      class Foo {
          private synchronized void hi() {
              // do something
          }
      }
      ```
    * after refactor
      ```java
      class Foo {
          private ReentrantLock fooObjectLock = new ReentrantLock();
          private void hi() {
              fooObjectLock.lock();
              try{
                  // do something
              } finally{
                  fooObjectLock.unlock();
              }
          }
      }
      ```

* Refactor static synchronized methods using a private static ReentrantLock.
    * before refactor
      ```java
      class Foo {
          private static synchronized void hi() {
                // do something
          }
      }
      ```
    * after refactor
      ```java
      class Foo {
          private static ReentrantLock fooClassLock = new ReentrantLock();
          private static void hi() {
              fooClassLock.lock();
              try{
                  // do something
              } finally{
                  fooClassLock.unlock();
              }
          }
      }
      ```

### 2. Refactor synchronized blocks

* Refactor synchronized blocks that lock with the `this` object using a ReentrantLock.
    * before refactor
      ```java
        class Foo {
            private void hi() {
                synchronized(this){
                    // do something
                }
            }
        }
      ```
    * after refactor
      ```java
      class Foo {
          private ReentrantLock fooObjectLock = new ReentrantLock();
          private void hi() {
            {
              fooObjectLock.lock();
              try{
                  // do something
              } finally{
                  fooObjectLock.unlock();
              }
            }
          }
      }
      ```
* Refactor synchronized blocks that lock with an object using a ReentrantLock.
    * before refactor
      ```java
      class Foo {
          private Object object;
          private void hi() {
              synchronized(object){
                  // do something
              }
          }
      }
      ```
    * after refactor
      ```java
      class Foo{
          private ReentrantLock objectLock = new ReentrantLock();
          private Object object;
          private void hi() {
            {
              objectLock.lock();
              try{
                  // do something
              } finally{
                  objectLock.unlock();
              }
            }
          }
      }
      ```
* Refactor synchronized blocks that lock with a static object using a static ReentrantLock.
    * before refactor
      ```java
      class Foo {
          private static Object object;
          private static void hi() {
              synchronized(object){
                  // do something
              }
          }
      }
      ```
    * after refactor
      ```java
      class Foo{
          private static ReentrantLock objectLock = new ReentrantLock();
          private static Object object;
          private static void hi() {
            {
              objectLock.lock();
              try{
                  // do something
              } finally{
                  objectLock.unlock();
              }
            }
          }
      }
      ```
* Refactor synchronized blocks that lock with a component using a public ReentrantLock of the component.
    * before refactor
      ```java
      class Boo {}
      class Foo {
          private Boo boo;
          private void hi() {
              synchronized(boo){
                  // do something
              }
          }
      }
      ```
    * after refactor
      ```java
      class Boo {
          public ReentrantLock booObjectLock = new ReentrantLock();
      }
      class Foo{
          private Boo boo;
          private void hi() {
            {
              boo.booObjectLock.lock();
              try{
                  // do something
              } finally{
                  boo.booObjectLock.unlock();
              }
            }
          }
      }
      ```
* Refactor synchronized blocks that lock with a static component using a public static ReentrantLock of the component.
    * before refactor
      ```java
      class Boo {}
      class Foo {
          private static Boo boo;
          private static void hi() {
              synchronized(boo){
                  // do something
              }
          }
      }
      ```
    * after refactor
      ```java
      class Boo {
          public static ReentrantLock booClassLock = new ReentrantLock();
      }
      class Foo{
          private static Boo boo;
          private static void hi() {
            {
              boo.booClassLock.lock();
              try{
                  // do something
              } finally{
                  boo.booClassLock.unlock();
              }
            }
          }
      }
      ```

## TODO

1. Improve features of refactor synchronized methods/blocks: refactor only if the methods/blocks contain any I/O
   operations.
2. Add new features to refactor ThreadLocal usage with ScopedValue

## References

* [Java 21 Virtual Threads: Scheduling Virtual Threads and Pinned Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html#GUID-704A716D-0662-4BC7-8C7F-66EE74B1EDAD)
* [Java 21 Virtual Threads: Don't Cache Expensive Reusable Objects in Thread-Local Variables](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html#GUID-68216B85-7B43-423E-91BA-11489B1ACA61)
