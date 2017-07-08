# Atomic DSL : nicer syntax for atomic references

Instead of using java style busy spin loops to transact on AtomicReferences, 
this dsl provides a more expressive way of achieving the same thing in scala.

The following Java snippet
```java
boolean updated = false;
while(!updated){
  T old = ref.get();
  updated = ref.compareAndSet(old, old + 1);
}
```

could just become something like the following in scala :

```scala
ref.transact { n => n + 1 }
```


## Imports
You first need to import the package `atomic._`
```scala
import org.cc.concurrent.atomic._
```

This will bring a few helpers, and enable the syntax extensions for AtomicReference
You can easily create an atomic reference the following way :
```scala
val v = atomic(1) // equivalent to val v = new AtomicReference[Int](1)
```

## Transactions
You can then use the `transact` syntax to perform changes on the atomic reference :
<br>
`transact*` methods are taking a function f (`f: T => T`) to the compute the new value to store in the AtomicReference. 
The parameter is the **current** value held by the AtomicReference.
<br> 
It returns the next value to store in the AtomicReference - if no concurrent changes have happened.
<br>
Ideally, this function should be free of side effect - or side effects should be idempotent - 
as it may be called several times (if called in a `ref.retry { ... }` block for instance).


```scala
v.tryTransact { n => n + 1 }          // try transaction only once, returns (Some(1 -> 2)) - or None if transaction failed

v.tryTransact { n => n + 1 } orFail() // same with failure

// Retrying a transaction at most 3 times or fail
v.retry(3 times) { n => n + 1 } orFail()

// Executing transaction in another thread, returning a Future[(Int, Int)]
implicit val ec: ExecutionContext =  ...
v.retryF(12 times) { n => n + 1 } map { case (old, newone) => ... } onComplete { ... }
```


## Compare and Swap

When performing a transaction, there is a way of specifying the expected value to consider for the CAS.<br>
If the AtomicReference doesn't hold the expected value, transact method will return its usual failure value.

```scala
v.tryTransact(expected=1) { n => n + 1 }          // try transaction only once. Succeed if the Atomicreference is holding teh expected value

v.tryTransact(expected=1) { n => n + 1 } orFail() // throws an AtomicRefTransactionException
```