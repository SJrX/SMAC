package ca.ubc.cs.beta.smac.matlab;

import java.util.Random;

import ca.ubc.cs.beta.aclib.model.data.SanitizedModelData;
import ca.ubc.cs.beta.aclib.options.RandomForestOptions;

public class ACMatlabBridge {

	
	public static void bridge( SanitizedModelData mds, int[][] theta_inst_indxs, int[] censoredRuns,  int imputationIterations, double cutoffTime, double penaltyFactor)
	{
		
		boolean[] X = new boolean[censoredRuns.length];
		for(int i=0; i < censoredRuns.length; i++)
		{
			X[i] = (censoredRuns[i] == 1);
		}
		
		bridge(mds, theta_inst_indxs, X, imputationIterations, cutoffTime, penaltyFactor);
	}
	public static void bridge( SanitizedModelData mds, int[][] theta_inst_indxs, boolean[] censoredRuns,  int imputationIterations, double cutoffTime, double penaltyFactor)
	{
		
		RandomForestOptions opts = new RandomForestOptions();
		
	
		Random rand = new Random(0);
		
		
		//ModelBuilder b  = new AdaptiveCappingModelBuilder(mds, opts, theta_inst_indxs, censoredRuns, rand, imputationIterations, cutoffTime, penaltyFactor);
		
		
		
	 return;
	}
}
