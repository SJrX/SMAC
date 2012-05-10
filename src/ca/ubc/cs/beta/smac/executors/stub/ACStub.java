package ca.ubc.cs.beta.smac.executors.stub;

import java.io.IOException;

public class ACStub {

	public static void pause()
	{
		try {
			System.in.read();
			System.in.read();
			System.in.read();
			System.in.read();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		pause();
		//ca.ubc.cs.beta.executors.SMACExecutor.main(args);
		//AlgorithmRunConfiguratorExecutor.main(args);
		try {
			Thread.sleep(1000000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		

	}

}
