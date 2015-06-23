/**
 * 
 */
package ca.ubc.cs.beta.smac.lib.aeatk;

import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.RunningAlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.kill.KillHandler;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.kill.StatusVariableKillHandler;
import ca.ubc.cs.beta.aeatk.misc.watch.StopWatch;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorRunObserver;

/**
 * {@link Runnable} that executes the decorators for JAVA TAEs like
 * {@link SyncJavaTargetAlgorithmEvaluator}.
 * 
 * @author Simon Bartels
 *
 */
public class DecoratorRunnable implements Runnable {

	/**
	 * Signals if one of the decorators killed our objective function.
	 */
	private volatile boolean wasKilled = false;

	/**
	 * Reference to the thread the objective function is executed in.
	 */
	private final Thread objectiveFunctionThread;

	/**
	 * ID used to set the name of this thread.
	 */
	private final Integer myToken;

	/**
	 * Reference to the stopwatch that measures the runtime of our objective
	 * function.
	 */
	private final StopWatch stopWatch;

	/**
	 * Killhandler.
	 */
	private final KillHandler killHandler = new StatusVariableKillHandler();

	/**
	 * The observers we want to run.
	 */
	private final TargetAlgorithmEvaluatorRunObserver runObserver;

	/**
	 * The run configuration for this run.
	 */
	private final AlgorithmRunConfiguration runConfig;

	/**
	 * Update frequency how often we call the observers.
	 */
	private final int observerFrequency;

	/**
	 * Logger.
	 */
	private final Logger log;

	/**
	 * @param objThread
	 *            thread where the objective function runs
	 * @param runStatusObserver
	 *            observers to be called
	 * @param token
	 *            an id used in naming this thread
	 * @param sw
	 *            a stopwatch that measures the execution time of our objective
	 *            function (not necessarily started)
	 * @param config
	 *            the currently executed configuration
	 * @param observerFrequency
	 *            how often observers should be notified in ms (+25)
	 * 
	 */
	public DecoratorRunnable(Thread objThread,
			TargetAlgorithmEvaluatorRunObserver runStatusObserver,
			Integer token, StopWatch sw, AlgorithmRunConfiguration config,
			int observerFrequency) {
		objectiveFunctionThread = objThread;
		runObserver = runStatusObserver;
		myToken = token;
		stopWatch = sw;
		runConfig = config;
		log = LoggerFactory.getLogger(getClass());
		if(observerFrequency < 0)
			throw new IllegalArgumentException("The observer frequency must be more or equal 0.");
		this.observerFrequency = observerFrequency + 25;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		Thread.currentThread().setName(
				"Java TAE (Observer Thread - #" + myToken + ")");
		while (true) {
			double currentTime = stopWatch.time() / 1000.0;
			/*
			 * Simon: I don't distinguish runtime and wall-clock time. Usually
			 * the algorithm itself should report how much wall-clock time it
			 * actually got but I think for this TAE we can safely assume it's
			 * approximately the same. Depends on the used observers of
			 * course...
			 */
			runObserver
					.currentStatus(Collections
							.singletonList((AlgorithmRunResult) new RunningAlgorithmRunResult(
									runConfig, currentTime, 0, 0, runConfig
											.getProblemInstanceSeedPair()
											.getSeed(), currentTime,
									killHandler)));
			try {
				// Sleep here so that maybe anything that wanted us dead will
				// have gotten to the killHandler
				Thread.sleep(25);
				if (killHandler.isKilled()) {
					wasKilled = true;
					log.trace("Trying to kill run: {} latest time: {} ",
							runConfig, currentTime);
					objectiveFunctionThread.interrupt();
					return;
				}
				Thread.sleep(observerFrequency - 25);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	/**
	 * Signals whether on of the observers killed the objective function.
	 * 
	 * @return true or false
	 */
	public boolean wasKilled() {
		return wasKilled;
	}

}
