package ca.ubc.cs.beta.smac.ac.exceptions;

public class OutOfTimeException extends SMACRuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3562273461188581045L;

	public OutOfTimeException() {
		super("SMAC is out of time.");
	}

}
