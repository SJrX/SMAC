package ca.ubc.cs.beta.smac.matlab;

import ca.ubc.cs.beta.smac.PCA;
import ca.ubc.cs.beta.smac.model.data.PCAModelDataSanitizer;

public class MatlabModelDataSanitizer extends PCAModelDataSanitizer {

	public MatlabModelDataSanitizer(double[][] instanceFeatures,
			double[][] paramValues, int numPCA, double[] responseValues,int[] usedInstanceIdxs,
			boolean logModel) {
		super(instanceFeatures, paramValues, numPCA, responseValues, usedInstanceIdxs, logModel);
		// TODO Auto-generated constructor stub
	}

	/**
	 * Fixes data for matlab output
	 */
	@Override
	public int[] getDataRichIndexes()
	{
		int[] vals = super.getDataRichIndexes().clone();
		
		for(int i=0; i < vals.length; i++)
		{
			vals[i]++;
		}
		
		return vals;
	}
	
	public int[][] getDataRichIndexesColumn()
	{
		int[][] data = new int[1][];
		data[0] = getDataRichIndexes();
		new PCA();
		return data;
		
	}
}
