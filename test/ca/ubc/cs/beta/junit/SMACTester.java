package ca.ubc.cs.beta.junit;

/*
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.Assert;
import org.testng.AssertJUnit;
*/


import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.*;
import static org.junit.Assert.*;

import ca.ubc.cs.beta.aclib.options.scenario.ScenarioOptions;

import com.beust.jcommander.JCommander;

public class SMACTester {
	

	public static String smacDeployment = null;
	public static Long seedOffset = Math.abs((new Random()).nextLong());
	
	@BeforeClass
	public static void initDeploymentToTest()
	{
		
				
			
			String s = ClassLoader.getSystemClassLoader().getResource("lastbuild-deploy.txt").getFile();
			//System.out.println("CL Name:"+ ClassLoader.getSystemClassLoader().getClass().getName());
			if(s == null || s.trim().length() == 0)
			{
				throw new AssertionError("Could not find deployment file lastbuild-deploy.txt on classpath");
			}
			File f = new File(s);
			try {
				BufferedReader r = new BufferedReader(new FileReader(f));
				smacDeployment = r.readLine();
				System.out.println("SMAC DEPLOYMENT: " + smacDeployment);
				r.close();
			} catch (FileNotFoundException e) {
				throw new AssertionError("Could open the deployment file lastbuild-deploy.txt");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				throw new AssertionError(e);
			}
			
			//System.out.println(s);
			
			/*System.out.println(f.getAbsolutePath())*/
			
	}
	
		public String getExecString(String scenarioFile, boolean adaptiveCapping, int iterationLimit, boolean ROARMode, int restoreIteration, int id, boolean verifyHashCodes, boolean checkInstances, boolean surrogate)
		{
			String experimentDir = (new File(scenarioFile)).getParentFile().getParent();
			String runID = new File(scenarioFile).getName() + "-JUNIT";
			
			/*ScenarioConfig options = new ScenarioConfig();
			String[] args = {"--scenarioFile", scenarioFile};
			JCommanderHelper.parse(new JCommander(options), args);
			
			System.out.println(options);
			if (true) return true;
			*/
	
			String instanceCheck = (checkInstances) ? " --checkInstanceFilesExist true " : " --checkInstanceFilesExist false ";
			
			String execString = "./smac --cleanOldStateOnSuccess false --scenarioFile " +  scenarioFile + " --numIterations " + iterationLimit + " --runGroupName "  + runID + "-" + id + " --experimentDir " + experimentDir + " --numRun 0 "  +  instanceCheck+  " --doValidation false --seedOffset " + seedOffset;
			
			if(surrogate)
			{
				execString +=" --tae SURROGATE";
			}
			
			
			if(restoreIteration > 0)
			{
				execString += " --restoreStateFrom " +  (new File(scenarioFile)).getParentFile().getAbsolutePath() + File.separator + "paramils-out" + File.separator +  runID + "-" + (id-1)+ File.separator + "state-run0";
				execString += " --restoreIteration " + restoreIteration+ " ";
				if(verifyHashCodes)
				{
					execString += " --runHashCodeFile " + (new File(scenarioFile)).getParentFile().getAbsolutePath()  + File.separator + "paramils-out" + File.separator +  runID + "-" + (id-1)+ File.separator + "runhashes-run0.txt";
				}
			}
			
			if(adaptiveCapping)
			{
				execString += " --adaptiveCapping true";
			}
			
			if(ROARMode)
			{
				execString += " --executionMode ROAR";
			}
			
			return execString;
			
		}
		
		public int runSMAC(String scenarioFile, boolean adaptiveCapping, int iterationLimit, boolean ROARMode, int restoreIteration, int id, String messageClass, String message, boolean verifyHashCodes, boolean checkInstances, boolean surrogate )
		{
			
			
			
			int iteration=0;
			
			final ScenarioOptions sc = new ScenarioOptions();
			//System.out.println("Location:" + this.getClass().getClassLoader().getResource("com/beust/jcommander/JCommander.class"));
			
			JCommander jcom = new JCommander(sc, true, true);
			String[] args = {"--scenarioFile",scenarioFile};
			
			jcom.parse(args);
			
			boolean deterministic = (sc.algoExecOptions.deterministic);
			
			
			
			boolean resultFound = false;
			String execString = getExecString(scenarioFile, adaptiveCapping, iterationLimit, ROARMode, restoreIteration, id, verifyHashCodes, checkInstances,surrogate);
			
			System.out.println("Execution Directory:" + smacDeployment);
			String runDir = smacDeployment;
			Queue<String> last10Lines = new LinkedList<String>();
			boolean messageFound = false;
			try {
				
				Process p;
			
					System.out.println(execString);
					p = Runtime.getRuntime().exec(execString, new String[0], new File(runDir));
					BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
					
					
					
					
					String it = "Iteration ";
					
					String line;
					System.out.print("RUNNING:");
					System.out.flush();
					String regex = ".*run for config \\d+ \\(0x[0-9A-Z]+\\) on instance \\d+ with seed (-?\\d+) and captime \\d+.*";
					//String regex = "running options \\d+ on instance \\d+ with seed (-?\\d+) and captime \\d+";
					Pattern pat = Pattern.compile(regex);
					
					while((line = in.readLine()) != null)
					{
						//System.out.println(line);
						
						if(Thread.interrupted())
						{
							p.destroy();
							throw new InterruptedException();
						}
						
						
						if(line.contains(messageClass) && line.contains(message))
						{
							messageFound = true;
						}
						
						
						
						
						
						//System.out.println(line);
						last10Lines.add(line);
						if(last10Lines.size() > 100)
						{
							last10Lines.poll();
						}
						
						if(line.contains("{}"))
						{
							for(String s : last10Lines)
							{
								System.out.println(s);
							}
							throw new IllegalStateException("Application Exited With Failure ({}) detected, this is a log bug generally");
						}
						
						
						if(line.contains("SMAC Completed Successfully"))
						{
							System.out.println(" [SUCCESS]");
							resultFound = true;
						} if(line.contains("Exiting Application with failure"))
						{
							System.out.println(" [FAILURE DETECTED]");
							for(String s : last10Lines)
							{
								System.out.println(s);
							}
							throw new IllegalStateException("Application Exited With Failure");
						} if(line.contains(it + iteration))
						{
							System.out.print(" " + iteration  + ":");
							System.out.flush();
							iteration++;
						}if(line.contains(it + restoreIteration) && restoreIteration > 0)
						{
							iteration = restoreIteration+1;
							
							System.out.print(" " + iteration  + ":");
							System.out.flush();
							
						}
						
						
						Matcher m = pat.matcher(line);
						
						if(m.find())
						{
							System.out.print("R");
							
							int seed = Integer.valueOf(m.group(1));//System.out.println(m.group(1));
							
							
							if(deterministic)
							{
									if(seed != -1) 
									{
										p.destroy();
										in.close();
										Assert.fail("Expected seed to be -1 on run: " + m.group(0));
									}
							} else
							{
								if(seed == -1) 
								{
									p.destroy();
									in.close();
									Assert.fail("Expected seed to be >0 on run: " + m.group(0));
								}
							}
							
							
						
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
			
			if((!resultFound) || (!messageFound))
			{
				for(String s : last10Lines)
				{
					System.out.println(s);
				}
				assertTrue("Could not find Successful Exit Message", resultFound);
				assertTrue("Could not find required message in output",messageFound);
				
				
			}
			
			
			return iteration;
			
			
			
		}
		
		/**
		 * Tries to run the sceraio
		 * @param scenarioFile - File to run
		 * @param messageClass What to look for in the log: [Something like ERROR, INFO]
		 * @param message Message to find in the log.
		 */
		public void testFailSMAC(String scenarioFile, String messageClass, String message, int iterations)
		{
			String execString = getExecString(scenarioFile, false, iterations, false, 0, -1, false, false, false);
			
			Queue<String> last10Lines = new LinkedList<String>();
			try {
			
				Process p;
				
				System.out.println(execString);

				
				String runDir = smacDeployment;
				p = Runtime.getRuntime().exec(execString, new String[0], new File(runDir));
				BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
				
				boolean resultFound = false;
				
				
				String it = "Iteration ";
				
				String line;
				System.out.print("RUNNING:");
				System.out.flush();
				String regex = "running options \\d+ on instance \\d+ with seed (-?\\d+) and captime \\d+";
				Pattern pat = Pattern.compile(regex);
				
				boolean errorFound = false;
				int iteration = 0;
				while((line = in.readLine()) != null)
				{
					
					
					if(Thread.interrupted())
					{
						p.destroy();
						throw new InterruptedException();
					}
					
					if(line.contains(messageClass) && line.contains(message))
					{
						errorFound = true;
					}
					
					
					
					
					//System.out.println(line);
					last10Lines.add(line);
					if(last10Lines.size() > 20)
					{
						last10Lines.poll();
					}
					if(line.contains("SMAC Completed Successfully"))
					{
						System.out.println(" [SUCCESS]");
						resultFound = true;
					} if(line.contains("Exiting SMAC with failure"))
					{
						System.out.println(" [FAILURE DETECTED]");
						for(String s : last10Lines)
						{
							System.out.println(s);
						}
					}if(line.contains(it + iteration))
					{
						System.out.print(" " + iteration  + ":");
						System.out.flush();
						iteration++;
					}
					
					
					Matcher m = pat.matcher(line);
					
					if(m.find())
					{
						System.out.print("R");
					}
					
					
					
					
					
				}
				
				
				BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
				
				
				while( (line = err.readLine()) != null)
				{
					System.out.print("\nERR>" + line);
					System.out.flush();
				}
				
				p.waitFor();
				
				
				if(resultFound)
				{
					fail("SMAC should not have completed successfully");
				}
				
				if(!errorFound)
				{
					fail("SMAC did not produce the error we wanted");
				} 
			} catch (IOException e) {
	
				e.printStackTrace();
				
			} catch(InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
			
			
						
			
		}
		
		
		
		public int restoreIteration(int v)
		{
			/**
			 * See: http://graphics.stanford.edu/~seander/bithacks.html#RoundUpPowerOf2
			 */
			v--;
			v |= v >> 1;
			v |= v >> 2;
			v |= v >> 4;
			v |= v >> 8;
			v |= v >> 16;
			v++;
			
			v /= 2;
			
			return v;
			
		}
	
		
		public void testSMAC(String scenarioFile)
		{
			//Ugly hack, but we will pass if we find a line with a space in it ;) 
			testSMAC(scenarioFile, " ", " ", false, false, false);
		}
		
		//public void testSMAC(String scenarioFile, String messageClass, String message, boolean verifyHashCodes)
		//{
		//	testSMAC(scenarioFile, messageClass, message, verifyHashCodes, false);
		//}

		
		public void testSMAC(String scenarioFile, String messageClass, String message, boolean verifyHashCodes, boolean checkInstances, boolean surrogate)
		{
			/**
			 * AC, Iteration Limit, ROAR Mode, Restore Iteration
			 */
			int id=0;
			int lastIteration;
			System.out.println("ROAR");
			lastIteration = runSMAC(scenarioFile, false, 18, true, 0, id++, messageClass, message, verifyHashCodes, checkInstances, surrogate);
			lastIteration = restoreIteration(lastIteration);
			System.out.println("Restore");
			runSMAC(scenarioFile, false, 21, true, lastIteration,id++, messageClass, message, verifyHashCodes, checkInstances, surrogate);
			
			
			System.out.println("ROAR+AC");
			lastIteration = runSMAC(scenarioFile, true, 18, true, 0, id++, messageClass, message, verifyHashCodes, checkInstances,surrogate);
			lastIteration = restoreIteration(lastIteration);
			System.out.println("Restore+AC");
			runSMAC(scenarioFile, true, 21, true, lastIteration,id++, messageClass, message, verifyHashCodes, checkInstances, surrogate);
			
			
			System.out.println("SMAC");
			lastIteration = runSMAC(scenarioFile, false, 18, false, 0, id++, messageClass, message, verifyHashCodes, checkInstances, surrogate);
			lastIteration = restoreIteration(lastIteration);
			System.out.println("Restore");
			runSMAC(scenarioFile, false, 21, false, lastIteration,id++, messageClass, message, verifyHashCodes, checkInstances, surrogate);
			
			
			System.out.println("SMAC+AC");
			lastIteration = runSMAC(scenarioFile, true, 18, false, 0, id++, messageClass, message, verifyHashCodes, checkInstances, surrogate);
			lastIteration = restoreIteration(lastIteration);
			
			System.out.println("Restore+AC");
			runSMAC(scenarioFile, true, 21, false, lastIteration,id++, messageClass, message, verifyHashCodes, checkInstances, surrogate);

			
		}
		
	
		
		@Test
		public void testSPEARSurrogateWeirdSeed()
		{
			
			String scenarioFile = "/ubc/cs/home/s/seramage/arrowspace/smac-test/spear/spear-surrogate-weirdseeds.txt";
			testFailSMAC(scenarioFile,"ERROR", "All Training Instances must have the same number of seeds in this version of SMAC",18);
		
			
		}
		
		@Test
		public void testCPLEXMini()
		{

			String scenarioFile = "/ubc/cs/home/s/seramage/arrowspace/smac-test/cplex_surrogate/scenario-Cplex-BIGMIX-mini.txt";
			testSMAC(scenarioFile, "INFO", "Clamping number of runs to 8 due to lack of instance/seeds pairs",true, false, true);
		}
		
		@Test
		/**
		 * Related to Bug 1346 
		 */
		public void testCPLEXRelativePath()
		{
			String scenarioFile = "/ubc/cs/home/s/seramage/arrowspace/smac-test/cplex_surrogate/scenario-Cplex-BIGMIX-mini-relativeInstances.txt";
			testSMAC(scenarioFile, "INFO", "Clamping number of runs to 8 due to lack of instance/seeds pairs",true, true, true);
		}
		
		
		
		@Test
		public void testSPEARSurrogate()
		{
			String scenarioFile = "/ubc/cs/home/s/seramage/arrowspace/smac-test/spear/spear-surrogate.txt";
			testSMAC(scenarioFile," ", " ", true, false, true);
		
			
		}
		
		
		
		/*
		private void testSMAC(String scenarioFile, boolean verifyHashCodes) {
			testSMAC(scenarioFile, " ", " ", verifyHashCodes );
			
		}*/

		@Test
		public void testSATENSTEIN()
		{
			
			String scenarioFile = "/ubc/cs/home/s/seramage/arrowspace/smac-test/satenstein/satenstein.txt";
			testSMAC(scenarioFile);
		}
		
		@Test
		public void testSPEAR()
		{
			System.out.println(smacDeployment);
			String scenarioFile = smacDeployment+"/example_spear/scenario-Spear-SWGCP-sat-small-train-small-test.txt";
			testSMAC(scenarioFile);
		}
		
		@Test
		public void testSAPS()
		{
			
			String scenarioFile = smacDeployment+"/example_saps/scenario-Saps-SWGCP-sat-small-train-small-test.txt";
			testSMAC(scenarioFile);
		}
		
		@Test
		public void testCPLEXSurrogate()
		{
			String scenarioFile = "/ubc/cs/home/s/seramage/arrowspace/smac-test/cplex_surrogate/scenario-Cplex-BIGMIX.txt";
			testSMAC(scenarioFile," ", " ", true, false, true);
		}
		
		
		
		/*
	}
	*/
	
}

