CleanSweep
============

Finalization substitute and cache assistance based on a reference queues and the Java `Executor` concurrency structure.

Example Use Cases
-------------------

* Delete files when an object is garbage collected
* Return objects to a cache when they are no longer being used
* Close streams and other I/O resources when they fall out of scope

Synposis
----------

```java

include com.smokejumperit.cleanSweep.Sweeper;

/* ... */

Sweeper s = new Sweeper();
s.onGC(someObjectToTrack, new Runnable() {
	public void run() {
		/* Implement behavior to perform on garbage collection here */
	}
});

s.onWeakGC(someObjectToTrack, new Sweeper.SweepAction<SomeObjectToTracksType>() {
	public void run() {
		/* Implement behavior to perform when the object is softly reachable here */
		/* The object can be retrieved by calling getTarget(). */
	}
});

```

Features
-----------

* Sane concurrency configuration provided by default, or override it with your favorite `Executor` or `ThreadFactory`.

* Execute code on garbage collection or right before garbage collection (weakly reachable).

* Ability to register a shutdown hook, whether or not to do background sweeping, and different strategies for sweeping.

* Extremely simple API.

Proper Usage
-------------

* Check out this code. 

* Execute `./gradlew clean jar javadoc` in the root directory of the project. 

* The jar and the JavaDoc are under `./build/libs` and `./build/docs/javadoc`, respectively.

* Enjoy.

Lazy Usage
------------

* Pull down the [jar directly from GitHub](https://github.com/RobertFischer/CleanSweep/raw/master/build/libs/CleanSweep.jar).

* Use your favorite code inspector to see the documentation for `com.smokejumperit.cleanSweep.Sweeper`.

* Enjoy.

Usage Notes
--------------

* *MAKE SURE YOU DO NOT HOLD ONTO A REFERENCE OF THE TARGET IN THE HANDLER.* Your handler counts as a root, so if you
  are holding onto a reference to the object that you want to get garbage collected, it will never get garbage collected
	and your code will never be executed.

* If you really don't want to use any kind of threading for some reason, then just pass the trivial `Executor` that calls
	`action.run()` on the tasks. Be sure to disable background threading through the `(Executor,boolean)` constructor, though,
	or your main thread will block forever.

License
---------

See [`./LICENSE.md`](https://github.com/RobertFischer/CleanSweep/blob/master/LICENSE.md)
