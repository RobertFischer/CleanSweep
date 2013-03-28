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

s.onSoftGC(someObjectToTrack, new Sweeper.SweepAction<SomeObjectToTracksType>() {
	public void run() {
		/* Implement behavior to perform when the object is softly reachable here */
		/* The object can be retrieved by calling <tt>getTarget()</tt>. */
	}
});
```

Features
-----------

Proper Usage
-------------

* Install [Gradle](http://gradle.org) if you do not have it.

* Check out this code. 

* Execute `gradle clean jar javadoc` in the root directory of the project. 

* The jar and the JavaDoc are under `./build/libs` and `./build/docs/javadoc`, respectively.

* Enjoy.

Lazy Usage
------------

* Pull down the [jar directly from GitHub](https://github.com/RobertFischer/CleanSweep/raw/master/build/libs/CleanSweep.jar).

* Use your favorite code inspector to see the documentation for `com.smokejumperit.cleanSweep.Sweeper`.

* Enjoy.

License
---------

See [`./LICENSE.md`](https://github.com/RobertFischer/CleanSweep/blob/master/LICENSE.md)
