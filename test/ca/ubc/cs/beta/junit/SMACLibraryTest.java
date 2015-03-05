package ca.ubc.cs.beta.junit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.Test;

import ca.ubc.cs.beta.aeatk.misc.associatedvalue.Pair;
import ca.ubc.cs.beta.aeatk.objectives.RunObjective;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.NormalizedRange;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParameterConfiguration;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParameterConfigurationSpace;
import ca.ubc.cs.beta.aeatk.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aeatk.runhistory.RunHistory;
import ca.ubc.cs.beta.aeatk.smac.SMACOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.base.analytic.AnalyticFunctions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.base.java.IObjectiveFunction;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.base.java.ObjectiveFunctionResult;
import ca.ubc.cs.beta.smac.executors.SMACJavaLib;

public class SMACLibraryTest {

	@Test
	public void testMissingDefaultValue() {
		Map<String, String> defaultValues = new HashMap<String, String>();
		List<List<Pair<String, String>>> forbiddenConfigurations = new ArrayList<List<Pair<String, String>>>();
		Map<String, Map<String, List<String>>> dependentValues = new HashMap<String, Map<String, List<String>>>();
		Map<String, NormalizedRange> continuousVariables = new HashMap<String, NormalizedRange>();
		Map<String, List<String>> categoricalVariables = new HashMap<String, List<String>>();
		List<String> catVals = new ArrayList<String>();
		catVals.add("good");
		catVals.add("bad");

		// test for categorical variables
		categoricalVariables.put("param", catVals);
		@SuppressWarnings("unused")
		ParameterConfigurationSpace space;
		try {
			space = new ParameterConfigurationSpace(categoricalVariables,
					continuousVariables, dependentValues, defaultValues,
					forbiddenConfigurations);
			fail("Giving no default value should result in an IllegalArgumentException.");
		} catch (IllegalArgumentException e) {
			defaultValues.put("param", "non-existent");
			try {
				space = new ParameterConfigurationSpace(categoricalVariables,
						continuousVariables, dependentValues, defaultValues,
						forbiddenConfigurations);
				fail("Giving non existent default value should result in an IllegalArgumentException.");
			} catch (IllegalArgumentException e2) {
				// Good, this is what we want
			} catch (Throwable t1) {
				t1.printStackTrace();
				fail("Giving non existent default value should result in an IllegalArgumentException.");
			}
		} catch (Throwable t) {
			t.printStackTrace();
			fail("Giving no default value should result in an IllegalArgumentException.");
		}

		// settle the problem with the categorical variable
		defaultValues.put("param", "good");

		// now we check continuous variables
		continuousVariables.put("param1", new NormalizedRange(-5, 5, false,
				false));
		try {
			space = new ParameterConfigurationSpace(categoricalVariables,
					continuousVariables, dependentValues, defaultValues,
					forbiddenConfigurations);
			fail("Giving no default value should result in an IllegalArgumentException.");
		} catch (IllegalArgumentException e) {
			defaultValues.put("param1", "-10.0");
			try {
				space = new ParameterConfigurationSpace(categoricalVariables,
						continuousVariables, dependentValues, defaultValues,
						forbiddenConfigurations);
				fail("Giving out-of-interval default value should result in an IllegalArgumentException.");
			} catch (IllegalArgumentException e2) {
				// Good, this is what we want
			} catch (Throwable t1) {
				t1.printStackTrace();
				fail("Giving out-of-interval default value should result in an IllegalArgumentException.");
			}
		} catch (Throwable t) {
			t.printStackTrace();
			fail("Giving no default value should result in an IllegalArgumentException.");
		}

		// settle the problem with the continuous variables
		defaultValues.put("param1", "0.0");
		try {
			space = new ParameterConfigurationSpace(categoricalVariables,
					continuousVariables, dependentValues, defaultValues,
					forbiddenConfigurations);
		} catch (Throwable t) {
			t.printStackTrace();
			fail("No exception expected!");
		}
	}

	@Test
	public void testObjectiveFunctionIsNull() {
		// fail("not implemented");
	}

	@Test
	public void testIntegerVariablesWithNonIntegerDefaultValue() {
		// fail("not implemented");
	}

}
