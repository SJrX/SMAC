package ca.ubc.cs.beta.smac.lib.aeatk;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.ExistingAlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.RunStatus;
import ca.ubc.cs.beta.aeatk.concurrent.threadfactory.SequentiallyNamedThreadFactory;
import ca.ubc.cs.beta.aeatk.misc.watch.StopWatch;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.AbstractSyncTargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorRunObserver;
import ca.ubc.cs.beta.smac.lib.ObjectiveFunctionResult;

/**
 * 
 * {@link TargetAlgorithmEvaluator} for objective functions written in Java.
 * 
 * @author Simon Bartels
 *
 */
public class SyncJavaTargetAlgorithmEvaluator extends
		AbstractSyncTargetAlgorithmEvaluator {

	/**
	 * The options for this evaluator.
	 */
	private SyncJavaTargetAlgorithmEvaluatorOptions options;

	public SyncJavaTargetAlgorithmEvaluator(
			SyncJavaTargetAlgorithmEvaluatorOptions options) {
		this.options = options;
	}

	@Override
	public boolean isRunFinal() {
		return false;
	}

	@Override
	public boolean areRunsPersisted() {
		return false;
	}

	@Override
	public boolean areRunsObservable() {
		return true;
	}

	@Override
	protected void subtypeShutdown() {
	}

	@Override
	public List<AlgorithmRunResult> evaluateRun(
			List<AlgorithmRunConfiguration> runConfigs,
			TargetAlgorithmEvaluatorRunObserver runStatusObserver) {
		Logger log = LoggerFactory.getLogger(getClass());
		List<AlgorithmRunResult> returnList = new ArrayList<AlgorithmRunResult>();
		boolean useObservers = options.observerFrequency >= 0;
		for (AlgorithmRunConfiguration config : runConfigs) {
			RunStatus runStatus = RunStatus.ABORT;
			double quality = Double.POSITIVE_INFINITY;
			double runlength = -1;
			StopWatch sw = new StopWatch();
			ObjectiveFunctionRunnable objectiveFunctionRunnable = new ObjectiveFunctionRunnable(
					options.objectiveFunction, config);
			Thread objThread = new Thread(objectiveFunctionRunnable);

			ExecutorService threadPoolExecutor = Executors
					.newCachedThreadPool(new SequentiallyNamedThreadFactory(
							"Java Target Algorithm Evaluator Thread "));
			DecoratorRunnable observerRunnable = null;
			if (useObservers) {
				/*
				 * The CommandLineTargetAlgorithmEvaluator gets tokens from a
				 * queue with |queue|=#cores. But since we have only one
				 * objective function thread the solution below should be
				 * sufficient.
				 */
				Integer token = 1;
				observerRunnable = new DecoratorRunnable(objThread,
						runStatusObserver, token, sw, config,
						options.observerFrequency);
			}
			Timer t = new Timer("JAVA TAE Algorithm Evaluator Cap Time Killer",
					true);
			ObjectiveFunctionKillTimerTask objectiveFunctionKiller = new ObjectiveFunctionKillTimerTask(
					objThread, t, sw, config.getCutoffTime());
			long cutOffTimeInMs = objectiveFunctionKiller
					.calculateCutOffTimeInMs(config.getCutoffTime());

			System.gc();
			sw.start();
			try {
				// threadPoolExecutor.execute(objectiveFunctionThread);
				objThread.start();
				t.schedule(objectiveFunctionKiller, cutOffTimeInMs);
				if (useObservers)
					threadPoolExecutor.execute(observerRunnable);
				// now we have to wait until the objective function is finished
				// or killed by an observer
				objThread.join();
			} catch (RuntimeException e) {
				log.error(
						"There was a problem executing the target algorithm: ",
						e);
			} catch (InterruptedException e) {
				objThread.interrupt();
				threadPoolExecutor.shutdownNow();
				// Thread.currentThread().interrupt();
				// TODO (Simon): is this the correct behavior? what should I
				// return in this case?
			}
			double runtime = sw.stop() / 1000.0;
			t.cancel();
			threadPoolExecutor.shutdownNow();
			ObjectiveFunctionResult y = objectiveFunctionRunnable.getResult();
			if (y != null) {
				runStatus = y.isSat() ? RunStatus.SAT : RunStatus.UNSAT;
				quality = y.getQuality();
				runlength = y.getRunlength();
			}
			if (config.getCutoffTime() < runtime)
				runStatus = RunStatus.TIMEOUT;
			if (useObservers && observerRunnable.wasKilled())
				runStatus = RunStatus.KILLED;
//			runConfig, RunStatus runResult, double runtime, double runLength, double quality, long resultSeed, String additionalRunData, double wallclockTime)
			AlgorithmRunResult result = new ExistingAlgorithmRunResult(config,
					runStatus, runtime, runlength, quality, config
							.getProblemInstanceSeedPair().getSeed(), "", runtime);
			returnList.add(result);
		}
		return returnList;
	}

}
