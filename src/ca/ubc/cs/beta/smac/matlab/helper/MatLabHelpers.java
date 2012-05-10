package ca.ubc.cs.beta.smac.matlab.helper;

public class MatLabHelpers {
	public static double[] min(double[] A, double value)
	{
		double[] result = new double[A.length];
		for(int i=0; i < A.length; i++)
		{
			result[i] = Math.min(A[i], value);
		}
		return result;
	}
	
	
}
