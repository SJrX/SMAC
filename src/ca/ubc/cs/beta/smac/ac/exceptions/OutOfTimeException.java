package ca.ubc.cs.beta.smac.ac.exceptions;

import ca.ubc.cs.beta.aclib.algorithmrun.AlgorithmRun;

public class OutOfTimeException extends SMACRuntimeException {

	
	private final AlgorithmRun run;
	/**
	 * 
	 */
	private static final long serialVersionUID = 3562273461188581045L;

	public OutOfTimeException(AlgorithmRun run) {
		super("SMAC is out of time.");
		this.run = run;
	}

	public AlgorithmRun getAlgorithmRun()
	{
		return run;
	}
}
