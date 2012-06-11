package ca.ubc.cs.beta.junit;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

import org.junit.Test;

public class SMACTester {
	
	/*
	@Test
	public void test()
	{
		Class[] cls = { ParallelTest.class};
		Result r = JUnitCore.runClasses(ParallelComputer.methods(), cls);
		
		if(r.getFailureCount() > 0)
		{
			fail("Failed Test Occured");
			
		}
	}
		
	

	public static class ParallelTest 
	{
		*/
		
		public boolean runSMAC(String scenarioFile, boolean adaptiveCapping, int iterationLimit, boolean ROARMode, int restoreIteration, int id )
		{
			String experimentDir = (new File(scenarioFile)).getParent();
			String runID = new File(scenarioFile).getName() + "-JUNIT";
			
			/*ScenarioConfig config = new ScenarioConfig();
			String[] args = {"--scenarioFile", scenarioFile};
			JCommanderHelper.parse(new JCommander(config), args);
			
			System.out.println(config);
			if (true) return true;
			*/
			
			String execString = "./smac --scenarioFile " +  scenarioFile + " --numIterations " + iterationLimit + " --runID "  + runID + "-" + id + " --experimentDir " + experimentDir + " --seed " + Math.abs((new Random()).nextInt()) + " --skipInstanceFileCheck --skipValidation ";
			
			
			
			String runDir = "/ubc/cs/research/arrow/seramage/software/smac";
			if(restoreIteration > 0)
			{
				execString += " --restoreStateFrom " + experimentDir + File.separator + "paramils-out" + File.separator +  runID + "-" + (id-1)+ File.separator + "state";
				execString += " --restoreIteration " + restoreIteration+ " ";
			}
			
			if(adaptiveCapping)
			{
				execString += " --adaptiveCapping ";
			}
			
			if(ROARMode)
			{
				execString += " --executionMode ROAR";
			}
				
				
			boolean resultFound = false;
			Queue<String> last10Lines = new LinkedList<String>();
			
			try {
				Process p;
			
					System.out.println(execString);
					p = Runtime.getRuntime().exec(execString, new String[0], new File(runDir));
					BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
					
					
					
					
					String it = "Iteration ";
					int iteration=0;
					String line;
					System.out.print("RUNNING:");
					System.out.flush();
		
					while((line = in.readLine()) != null)
					{
						//System.out.println(line);
						last10Lines.add(line);
						if(last10Lines.size() > 20)
						{
							last10Lines.poll();
						}
						if(line.contains("SMAC Completed Successfully"))
						{
							System.out.println("SUCCESS");
							resultFound = true;
						} if(line.contains("Exiting Application with failure"))
						{
							System.out.println("FAILURE DETECTED");
							for(String s : last10Lines)
							{
								System.out.println(s);
							}
							throw new IllegalStateException("Application Exited With Failure");
						} if(line.contains(it + iteration))
						{
							System.out.print(iteration  + ",");
							System.out.flush();
							iteration++;
						}
					}
					
					BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
					
					
					while( (line = err.readLine()) != null)
					{
						System.out.print("\nERR>" + line);
						System.out.flush();
					}
					
					p.waitFor();
			} catch (IOException e) {

				e.printStackTrace();
				
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				
			}
			
			
			System.out.println("");
			
			if(!resultFound)
			{
				for(String s : last10Lines)
				{
					System.out.println(s);
				}
			}
			return resultFound;
			
			
		}
		
		
		public void testSMAC(String scenarioFile)
		{
			/**
			 * AC, Iteration Limit, ROAR Mode, Restore Iteration
			 */
			int id=0;
			System.out.println("ROAR");
			assertTrue(runSMAC(scenarioFile, false, 18, true, 0, id++));
			System.out.println("Restore");
			assertTrue(runSMAC(scenarioFile, false, 21, true, 4,id++));
			System.out.println("ROAR+AC");
			assertTrue(runSMAC(scenarioFile, true, 18, true, 0, id++));
			System.out.println("Restore+AC");
			assertTrue(runSMAC(scenarioFile, true, 21, true, 4,id++));
			
			
			System.out.println("SMAC");
			assertTrue(runSMAC(scenarioFile, false, 18, false, 0, id++));
			System.out.println("Restore");
			assertTrue(runSMAC(scenarioFile, false, 21, false, 4,id++));
			System.out.println("SMAC+AC");
			assertTrue(runSMAC(scenarioFile, true, 18, false, 0, id++));
			System.out.println("Restore+AC");
			assertTrue(runSMAC(scenarioFile, true, 21, false, 4,id++));
			
			
			
			
		}
		
		@Test
		public void testSPEARSurrogate()
		{
			String scenarioFile = "/ubc/cs/home/s/seramage/arrowspace/smac-test/spear/spear-surrogate.txt";
			testSMAC(scenarioFile);
		
			
		}
		
		@Test
		public void testSATENSTEIN()
		{
			
			String scenarioFile = "/ubc/cs/home/s/seramage/arrowspace/smac-test/satenstein/satenstein.txt";
			testSMAC(scenarioFile);
		}
		
		@Test
		public void testSPEAR()
		{
			String scenarioFile = "/ubc/cs/home/s/seramage/arrowspace/smac-test/example_spear/scenario-Spear-SWGCP-sat-small-train-small-test.txt";
			testSMAC(scenarioFile);
		}
		
		@Test
		public void testSAPS()
		{
			
			String scenarioFile = "/ubc/cs/home/s/seramage/arrowspace/smac-test/example_saps/scenario-Saps-SWGCP-sat-small-train-small-test.txt";
			testSMAC(scenarioFile);
		}
		
		
		
		/*
	}
	*/
	
}
