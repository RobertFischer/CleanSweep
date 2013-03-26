import com.smokejumperit.phantomFinal.Sweeper;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.*;

public class Main {

	public static void main(String[] args) {
		final Sweeper sweeper = new Sweeper();
		sweeper.registerShutdownHook();

		Object it = new Object();
		final AtomicBoolean stop = new AtomicBoolean(false);
		sweeper.onGC(it, new Runnable() {
			public void run() {
				System.out.println("onGC action run!");
				System.out.flush();
				stop.set(true);
			}
		});

		for(int i = 0; !stop.get(); i++) {
			it = new ArrayList<Integer>(i);
			for(int k = 0; k < i; k++) {
				((List)it).add(new Integer(k));
			}
			Thread.yield();
		}
		System.out.println(((List)it).size());

		sweeper.shutdown();
	}

}
