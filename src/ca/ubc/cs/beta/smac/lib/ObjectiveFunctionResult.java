package ca.ubc.cs.beta.smac.lib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that saves the most basic results an objective function can return.
 * 
 * @author Simon Bartels
 *
 */
public class ObjectiveFunctionResult {
	private final double quality;
	private final boolean sat;
	private final double runlength;
	
	/**
	 * Standard constructor.
	 * @param quality
	 * 	the quality of the function evaluation
	 * @param sat
	 * 	historic parameter that states if the problem was solved or not
	 * @param runlength
	 *  problem specific measure how far the algorithm progressed.
	 *  Values may be -1 or between 0 and infinity.
	 */
	public ObjectiveFunctionResult(double quality, boolean sat, double runlength){
		this.quality = quality;
		this.sat = sat;
		if(runlength != -1 && runlength < 0){
			Logger logger = LoggerFactory.getLogger(getClass());
			logger.warn("Objective function returned illegal value for runlength. Defaulting to -1.", new IllegalArgumentException());
			runlength = -1;
		}
		this.runlength = runlength;
	}

	public double getQuality() {
		return quality;
	}

	public boolean isSat() {
		return sat;
	}

	public double getRunlength() {
		return runlength;
	}
}
