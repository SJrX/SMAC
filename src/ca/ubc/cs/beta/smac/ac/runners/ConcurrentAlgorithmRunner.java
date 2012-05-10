package ca.ubc.cs.beta.smac.ac.runners;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ca.ubc.cs.beta.ac.config.AlgorithmExecutionConfig;
import ca.ubc.cs.beta.ac.config.RunConfig;
import ca.ubc.cs.beta.smac.ac.runs.AlgorithmRun;

public class ConcurrentAlgorithmRunner extends AbstractAlgorithmRunner {

	private int nThreads;
	public ConcurrentAlgorithmRunner(AlgorithmExecutionConfig execConfig,
			List<RunConfig> instanceConfigs, int nThreads) {
		super(execConfig, instanceConfigs);
		this.nThreads = nThreads;
	}

	@Override
	public synchronized List<AlgorithmRun> run() {
		
		ExecutorService p = Executors.newFixedThreadPool(nThreads);
		for(AlgorithmRun run : runs)
		{
			p.submit(run);
		
		}
		
		
		p.shutdown();
		try {
			p.awaitTermination(24, TimeUnit.HOURS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return runs;
		
	}

}
