package ca.ubc.cs.beta.executors;

import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration.StringFormat;

public class ConfigGen {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		ParamConfigurationSpace configSpace = new ParamConfigurationSpace(args[0]);
		System.out.println(configSpace.getDefaultConfiguration().getFormattedParamString(StringFormat.SURROGATE_EXECUTOR));
	}

}
