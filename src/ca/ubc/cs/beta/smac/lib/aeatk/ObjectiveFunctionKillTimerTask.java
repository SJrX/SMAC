package ca.ubc.cs.beta.smac.lib.aeatk;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

import ca.ubc.cs.beta.aeatk.misc.watch.StopWatch;

public class ObjectiveFunctionKillTimerTask extends TimerTask {

	private final WeakReference<Thread> objectiveFunction;

	private final Timer t;

	private final StopWatch sw;

	private final double cutOffTimeInS;

	public ObjectiveFunctionKillTimerTask(Thread objectiveFunctionThread,
			Timer t, StopWatch sw, double cutOffTimeInS) {
		objectiveFunction = new WeakReference<Thread>(objectiveFunctionThread);
		this.t = t;
		this.sw = sw;
		this.cutOffTimeInS = cutOffTimeInS;
	}

	@Override
	public void run() {
		if (sw.time() < cutOffTimeInS * 1000.0) {
			// reschedule
			t.schedule(this, calculateCutOffTimeInMs(cutOffTimeInS));
		} else {
			Thread f = objectiveFunction.get();
			if (f != null)
				f.interrupt();
		}
	}

	/**
	 * Returns the cut off time in ms or if that number would be too large the
	 * longest time possible. This timer task will reschedule itself if it is
	 * executed too early.
	 * 
	 * @param cutoffTimeInS
	 *            the cut-off time in seconds
	 * @return the time in ms or the largest possible value (NOT Long.MAX_VALUE)
	 */
	public long calculateCutOffTimeInMs(double cutoffTimeInS) {
		if (cutoffTimeInS < Double.MAX_VALUE / 1000.0) {
			if (Math.ceil(cutoffTimeInS * 1000.0) < Long.MAX_VALUE)
				// the easy case
				return (long) Math.ceil((cutoffTimeInS * 1000.0));
		}
		// the result + currentTimeMills() must be positive
		return Long.MAX_VALUE - System.currentTimeMillis() - 1000;
	}

}
