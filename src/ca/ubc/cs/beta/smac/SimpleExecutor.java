package ca.ubc.cs.beta.smac;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.math.stat.descriptive.moment.Mean;
import org.apache.commons.math.stat.descriptive.moment.StandardDeviation;

public class SimpleExecutor {
	
	public static double[] dbl = new double[10240];
	public static int i=0;
	StandardDeviation stdev = new StandardDeviation();
	Mean mean = new Mean();
	
	public void clear()
	{
		System.out.println("CLEAR");
		i=0;
	}
	
	

	public void run(String execString, String outDir)
	{
		try {
			
			
			long time = System.currentTimeMillis();
			Process proc = Runtime.getRuntime().exec(execString, null,new File(outDir));
			
			System.out.println("Simple Executor " + execString);
			
			InputStream in = proc.getInputStream();
			int input;
			while((input = in.read()) != -1)
			{
				System.out.write(input);
			}
			InputStream err = proc.getErrorStream();
			
			while((input = err.read()) != -1)
			{
				System.out.write(input);
			}
			
			proc.destroy();
			long delta = (System.currentTimeMillis() - time);
			System.out.println("Execution Time\n" + delta + "\n");
			dbl[i++] = delta;
			
			
			
			
			
			System.out.println("Stats: " + i + " runs. Mean: " + mean.evaluate(dbl,0,i) + " stddev: "+ stdev.evaluate(dbl,0,i));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
