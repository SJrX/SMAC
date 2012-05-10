package ca.ubc.cs.beta.smac.targetalgorithms;



/**
 * This file is probably garbage please delete this
 * @author seramage
 *
 */


public class Runner {

//		public void execute(TargetAlgorithm func, int[] thetaIdxs, int[] seeds, String[] instanceFileNames, int[] censorLimits) throws TargetAlgorithmException
//		{
//			
//			try {
//				int numTry = 1;
//				while(true)
//				{
//					try{
//					BatchRunResults results = startRunsInBatch(func, thetaIdxs, instanceFileNames, seeds, censorLimits);
//					double[] runtimes = MatLabHelpers.min(results.runtimes, 1e7);
//					
//					} catch(Exception e)
//					{
//						System.out.println("Num try: " + numTry );
//						if( numTry >= 2)
//						{
//							/**
//							 * delete_files();
//							 * func
//							 * 
//							 */
//							throw new TargetAlgorithmException("Still not successful after 2 tries");
//						}
//						
//					}
//					numTry++;
//					
//					
//				}
//				
//				
//			} finally
//			{ /**
//			   * Delete Files
//			   */
//				
//				
//			}
//			
//			
//			
//			
//			
//		}
//
//		private BatchRunResults startRunsInBatch(TargetAlgorithm func, int[] thetaIdxs,	String[] instanceFileNames, int[] seeds, int[] censorLimits) 
//		{
//			BatchRunResults results = startManyRuns(func, thetaIdxs, instanceFileNames, seeds, censorLimits);
//			
//			double[] solveds = new double[seeds.length];
//			double[] censored = new double[seeds.length];
//			double[] runtimes = new double[seeds.length];
//			//double[] solveds = new double[seeds.length];
//			double[] runlengths = new double[seeds.length];
//			double[] best_sols = new double[seeds.length];
//			
//			for(int i=0; i < seeds.length; i++)
//			{
//				//Object res = 
//				
//				
//				
//				
//			}
//			
//			
//			
//			
//			return null;
//		}
//
//		private BatchRunResults startManyRuns(TargetAlgorithm func,
//				int[] thetaIdxs, String[] instanceFileNames, int[] seeds,
//				int[] censorLimits) {
//			// TODO Auto-generated method stub
//			return null;
//		}
}
