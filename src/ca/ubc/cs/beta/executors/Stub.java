package ca.ubc.cs.beta.executors;

import java.io.File;

import ca.ubc.cs.beta.configspace.ParamConfiguration;
import ca.ubc.cs.beta.configspace.ParamConfigurationSpace;

public class Stub {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		ParamConfigurationSpace configSpace = new ParamConfigurationSpace(new File("/ubc/cs/home/s/seramage/disks/westgrid/home/seramage/paramils2.3.5-source/LKH/LKH-params.txt"));
		
		ParamConfiguration config = configSpace.getRandomConfiguration();
		
		System.out.println(config);
		for(ParamConfiguration neighbour: config.getNeighbourhood())
		{
			System.out.println(neighbour);
		}
		
		
	}

}
