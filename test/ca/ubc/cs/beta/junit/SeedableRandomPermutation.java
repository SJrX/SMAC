package ca.ubc.cs.beta.junit;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

import ca.ubc.cs.beta.aclib.misc.debug.DebugUtil;
import ca.ubc.cs.beta.aclib.random.RandomUtil;
import ca.ubc.cs.beta.aclib.random.SeedableRandomPool;


public class SeedableRandomPermutation {

	private static final SeedableRandomPool pool = new SeedableRandomPool(System.currentTimeMillis());
	
	@Test
	public void testAllPermutationsGenerated() {
		Random rand = pool.getRandom(DebugUtil.getCurrentMethodName());
		
		
		
		Set<List<Integer>> permutations = new HashSet<List<Integer>>();
		
		int N = 10;
		long nFactorial = 1;
		for(int i =1; i <= N; i++)
		{
			nFactorial *= i;
			if(nFactorial * nFactorial  < 0)
			{
				fail("nFactorial too big");
			}

		}
		
		/**
		 * Actual bound is about 
		 * 
		 * O(n!lg(n!))
		 */
		for(int i=0; i < (nFactorial*nFactorial); i++)
		{
			int[] perm = RandomUtil.getPermutation(N, 0, rand);
			List<Integer> x = new ArrayList<Integer>(perm.length);
			for(int j = 0; j < perm.length; j++)
			{
				x.add(perm[j]);
			}
			permutations.add(x);
			
			
			//System.out.println(Arrays.toString(perm));
			
			if(permutations.size() == nFactorial)
			{
				break;
			}
			if((permutations.size() % 10000) == 0)
			{
				System.out.print(".");
			}
			if((permutations.size() % 500000) == 0)
			{
				System.out.print("\n");
			}
			
		}
		
		if(!(permutations.size() == nFactorial))
		{
			fail("Didn't generate every permutation " + permutations.size() + " versus " + nFactorial );
		} else
		{
			System.out.println("\nYay all " + nFactorial + " combinations generated " + permutations.size());
		}
	}

}
