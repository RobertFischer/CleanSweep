package com.smokejumperit.cleanSweep;

import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
* This class is responsible for monitoring and performing clean-up on garbage collected objects.
*/
public class Sweeper {

	private final ReferenceQueue<Object> queue = new ReferenceQueue<Object>();
	private final Executor executor;	

	private interface RunnableReference extends Runnable {
		public Runnable consumeAction();
	}

	/**
	* An action performed when a sweep occurs and the target is known.
	*/
	public static abstract class SweepAction<T> implements Runnable {

		private T target;

		/**
		* Retrives the object which triggered the cleanup action.
		*/
		public T getTarget() { 
			return target; 
		}

		/**
		* Sets the object which triggered the cleanup action.
		*/
		public void setTarget(T target) {
			this.target = target;
		}

		/**
		* Performs the action to clean up. When this class is called by {@link Sweeper}, 
		* {@link #setTarget(Object)} is guaranteed to have been called with a non-{@code null} 
		* value.
		*/
		public abstract void run();

	}

	private final class RunnablePhantomReference extends PhantomReference<Object> implements RunnableReference {
		private volatile Runnable action;

		public RunnablePhantomReference(Object toRun, Runnable action) {
			super(toRun, queue);
			this.action = action;
		}

		public synchronized Runnable consumeAction() {
			if(action == null) return null;
			Runnable toReturn = action;
			action = null;
			return toReturn;
		}

		public void run() {
			Runnable action = consumeAction();
			if(action != null) action.run();
			return;
		}
	}

	private final class RunnableWeakReference<T> extends WeakReference<T> implements RunnableReference {
		private volatile Runnable action;

		public RunnableWeakReference(T toRun, Runnable action) {
			super(toRun, queue);
			this.action = action;
		}

		public synchronized Runnable consumeAction() {
			if(action == null) return null;
			Runnable toReturn = action;
			action = null;
			if(toReturn instanceof SweepAction) {
				((SweepAction<T>)toReturn).setTarget(this.get());
			}
			return toReturn;
		}

		public void run() {
			Runnable action = consumeAction();
			if(action != null) action.run();
			return;
		}
	}

	private final Queue<RunnableReference> bag = new ConcurrentLinkedQueue<RunnableReference>();

	private final class RemoveFromBag implements Runnable {
		private final RunnableReference toRemove;
		
		public RemoveFromBag(RunnableReference toRemove) {
			if(toRemove == null) npe("Cannot remove a null RunnableReference");
			this.toRemove = toRemove;
		}

		public void run() {
			bag.remove(toRemove);
		}
	}

	/**
	* Constructs a default instance that uses daemon threads at maximum priority for the polling and cleanup 
	* tasks.
	*/
	public Sweeper() {
		this(createThreadFactory());
	}

	/**
	* Constructs an instance that uses the given ThreadFactory to create threads for polling and cleanup tasks.
	* The ThreadFactory is used to construct an {@link ExecutorService}. Currently, this is a {@link ThreadPoolExecutor},
	* but that may change in a later implementation.
	*/
	public Sweeper(final ThreadFactory forExecutor) {
		this(
			forExecutor == null ? 
				npe(ExecutorService.class, "Cannot accept a null ThreadFactory as an argument") : 
				createExecutor(forExecutor)
		);
	}

	/**
	* Constructs an instance that uses the given {@link Executor} to execute polling and cleanup tasks,
	* including a background sweeping thread.
	* Note that the executor is assumed to be able to execute blocking tasks. If (for instance)
	* the executor executes all the tasks directly, the calling thread will hang.
	*/
	public Sweeper(final Executor executor) {
		this(executor, true);
	}

	/**
	* Constructs an instance that uses the given {@link Executor} to execute polling and cleanup tasks.
	* If the second argument is {@code false}, the background sweeping thread will not be launched, and 
	* the user will need to use {@link #sweep()} or {@link #queueingSweep()} to manually trigger polling
	* of the queue.
	*/
	public Sweeper(final Executor executor, final boolean backgroundSweeping) {
		if(executor == null) npe("Cannot accept a null Executor as an argument");
		this.executor = executor;
		if(backgroundSweeping) this.executor.execute(new Runnable() {
			public void run() {
				RunnableReference ref = null;
				try {
					for(ref = (RunnableReference)queue.remove(); ref != null; ref = (RunnableReference)queue.poll()) {
						executor.execute(new RemoveFromBag(ref));
						executor.execute(ref);
					}

					// Now queue ourselves up to run again
					executor.execute(this);
				} catch(InterruptedException e) {
					if(ref != null) {
						bag.remove(ref);
						ref.run();
					}
					return;
				}
			}
		});
	}

	/**
	* Registers a shutdown hook to execute the clean-up on anything that didn't get GC'ed.
	* <b>Note that cleanup will be executed for any keys that are still alive.</b>
	* See also caveats from {@link Runtime#addShutdownHook(Thread)}.
	*/
	public void registerShutdownHook() {
		Thread t = new Thread(new Runnable() {
			public void run() {
				// Reject any new tasks
				if(executor instanceof ExecutorService) {
					((ExecutorService)executor).shutdown();
				}

				// Execute everything in the queue, trying to do it on GC if possible
				sweep();
				Runnable action = null;
				while((action = bag.poll()) != null) {
					action.run();
					action = null;
					Thread.yield();
					sweep();
				}
			}
		});
		t.setPriority(Thread.MIN_PRIORITY);
		Runtime.getRuntime().addShutdownHook(t);
	}

	/**
	* Register an action to be performed when the key is eligible to be garbage collected.
	* More precisely, it is performed at some point after the key becomes weakly reachable.
	*/
	public <T> void onWeakGC(final T key, final SweepAction<T> behavior) {
		bag.add(new RunnableWeakReference<T>(key, behavior));
	}

	/**
	* Register an action to be performed when the key is eligible to be garbage collected.
	* More precisely, it is performed at some point after the key becomes weakly reachable.
	*/
	public <T> void onWeakGC(final T key, final Runnable behavior) {
		bag.add(new RunnableWeakReference<T>(key, behavior));
	}

	/**
	* Register an action to be performed after the key is garbage collected.
	*/
	public void onGC(final Object key, final Runnable behavior) {
		bag.add(new RunnablePhantomReference(key, behavior));
	}

	private static ThreadFactory createThreadFactory() {
		final AtomicLong ctr = new AtomicLong(Long.MAX_VALUE);
		return new ThreadFactory() {
			public Thread newThread(final Runnable r) {
				final Thread t = new Thread(r);
				t.setName("Sweeper Cleanup Thread #" + Long.toHexString(ctr.getAndDecrement()));
				t.setPriority(Thread.MIN_PRIORITY);
				t.setDaemon(true);
				return t;
			}
		};
	}

	private static ExecutorService createExecutor(ThreadFactory threadFactory) {
		final int coreThreads = 2;
		final ThreadPoolExecutor executor = new ThreadPoolExecutor(
			coreThreads, Math.max(coreThreads, Runtime.getRuntime().availableProcessors()), 
			1, TimeUnit.MINUTES, 
			new LinkedBlockingQueue(), 
			threadFactory
		);
		executor.prestartAllCoreThreads();
		return executor;
	}

	/**
	* Prevents the Sweeper from processing new objects, and returns a 
	* collection of {@link Runnable} objects that have not been cleaned up. Note: 
	* the keys may not have been grabage collected for the returned Runnables.
	*/
	public List<Runnable> shutdown() {
		List<Runnable> toProcess = new ArrayList<Runnable>();
		toProcess.addAll(bag);
		if(executor instanceof ExecutorService) {
			((ExecutorService)executor).shutdown();
		}
		return toProcess;
	}

	private static void npe(String message) {
		npe(Object.class, message);
	}

	private static <T> T npe(Class<T> returnType, String message) {
		throw new NullPointerException(message);
	}

	/**
	* Performs a sweep and cleanup in the current thread.
	* 
	* @return Whether work was found during this poll.
	*/
	public boolean sweep() {
		boolean workFound = false;
		RunnableReference action = null;
		while( (action = (RunnableReference)queue.poll()) != null) {
			bag.remove(action);
			action.run();
			action = null;
			workFound = true;
			Thread.yield();
		}
		return workFound;
	}

	/**
	* Performs a sweep in the current thread, but cleanup work is done via the {@link Executor}.
	* 
	* @return Whether work was found during this poll.
	*/
	public boolean queueingSweep() {
		boolean workFound = false;
		RunnableReference action = null;
		while( (action = (RunnableReference)queue.poll()) != null) {
			workFound = true;
			executor.execute(new RemoveFromBag(action));
			executor.execute(action);
			action = null;
			Thread.yield();
		}
		return workFound;
	}

	/**
	* Enqueue a {@link #queueingSweep()} to be performed by the {@link Executor}.
	* There is no feedback fromt his sweep, so it is fire-and-forget.
	*/
	public void runSweep() {
		executor.execute(new DoSweep());
	}

	/**
	* Enqueue a {@link #queueingSweep()} to be performed by the {@link Executor}.
	*
	* <b>Implementation Note:</b><br />
	* There is a nontrivial effficiency hit if you used {@link #Sweeper(Executor}}
	* and did <i>not</i> pass in a {@link ExecutorService}. We end up having to
	* allocate a wrapper for each call to this method to make pretend it's a
	* service. See {@link #runSweep()} for a better option in that case.
	*
	* @return A {@link Future} denoting whether work was found during this sweep.
	*/
	public Future<Boolean> enqueueSweep() {
		if(executor instanceof ExecutorService) {
			return enqueueSweepInService((ExecutorService)executor);
		} else {
			return enqueueSweepInExecutor();
		}
	}

	private class DoSweep implements Callable<Boolean>,Runnable {
		public Boolean call() throws Exception {
			return queueingSweep();
		}

		public void run() {
			queueingSweep();
		}
	}

	private Future<Boolean> enqueueSweepInService(final ExecutorService service) {
		return service.submit((Callable<Boolean>)new DoSweep());
	}

	private Future<Boolean> enqueueSweepInExecutor() {
		return new ExecutorCompletionService(
			executor, new ArrayBlockingQueue<Future<Boolean>>(1,false)
		).submit(new DoSweep());
	}

}
