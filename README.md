# What is pouli?

An Object Pool. A shared repository of reusable, "expensive" objects
which can be borrowed.

Currently version 0.1 because it is awesome and battle-tested, and
ready for massive worry free deployments.

# Creating a pool:

In order to create a pool, we need to provide an instance of the
Poolable typeclass. This typeclass tells the pool how to:

  1. create Foo objects
  2. clean up when Foo objects are destroyed (optional)
  3. activate a Foo object that is being borrowed (optional)
  4. passivate a Foo object which is being returned to the pool (optional)

In the case where you don't need to be called for any of the optional
peices, and you just need to tell the pool how to create objects,
there is a "simple" helper on the Poolable companion object which
takes a Task[A] which can be run to create a new object.

```

class Foo // an object we wish to pool

implicit val poolableFoo: Poolable[Foo] = Poolable.simple(Task.delay(new Foo))

```

Now that we have a `Poolabel[Foo]` we can create a pool of Foo
objects:

```
val pool = new Pool[Foo]()
```

# Using the pool
## Borrowing from the pool

In order to borrow an item from the pool, you just need to ask nicely:

```
val foo: Task[Foo] = pool.please
```

## Returning objects to the pool

When you are done with the object you borrowed, you must return it to
the pool. There are two different ways to return an object to the
pool. If you had no problems with the object you borrowed, you say
thanks:

```
pool.thanksFor(foo)
```

If you think that the object you borrowed is broken, and should not be
returned to the pool, say so:

```
pool.thisIsBroken(foo)
```

# Grow Strategies

When a pool is created, it is assigned a `GrowStrategy`, which tells
the pool what to do when someone requests an object but there are no
objects in the pool. By default, the pool uses the "infinite" strategy
which will always create a new object when one is requested and there
are none in the pool.

Alternatively there is a maxSize strategy which will put an upper
limit on how many objects are in existence at any given time. When a
request comes in and there are no objects in the pool to lend, the
borrower will be put into a queue of borrowers, which gets serviced as
objects are returned to the pool by other borrowers

# Benchmarks

There is a benchmarks sub-project which benchmarks this pool against
commons-pool2. you can run the benchmarks with "sbt 'benchmarks/run'
numThreads" where numThreads is the number of worker threads to pound
on the pool.  As of this writing, pouli is running at about 40% of the
speed of commons-pool2. We'll work on that...