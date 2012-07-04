package ca.ubc.cs.beta.smac.executors;

/**
 * Class that stores return values for various conditions
 * <p>
 * <b>NOTE:</b>For the most part these shouldn't be changed
 * if there was ever a need to change these it might break some script
 * compatibility, however for the most part you should <b>NOT<b> change
 * {@see SMACReturnValues.RETURN_SUCESS} as 0 is generally successful execution
 * 
 * 
 * @author Steve Ramage 
 */
final class SMACReturnValues {

	
	/**
	 * Return value for SUCCESS
	 * SEE NOTE AT THE TOP OF FILE DO NOT CHANGE THIS VALUE FROM 0 
	 */
	static final int SUCCESS = 0;
	
	static final int PARAMETER_EXCEPTION = 1;
	
	static final int TRAJECTORY_DIVERGENCE = 2;
	
	static final int SERIALIZATION_EXCEPTION = 3;
	
	static final int OTHER_EXCEPTION = 255;
	
	
	
	private SMACReturnValues()
	{
		throw new IllegalArgumentException();
	}
}
