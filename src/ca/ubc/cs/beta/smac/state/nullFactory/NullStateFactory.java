package ca.ubc.cs.beta.smac.state.nullFactory;

import java.io.File;
import java.util.List;

import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.aclib.exceptions.StateSerializationException;
import ca.ubc.cs.beta.aclib.execconfig.AlgorithmExecutionConfig;
import ca.ubc.cs.beta.aclib.objectives.OverallObjective;
import ca.ubc.cs.beta.aclib.objectives.RunObjective;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aclib.state.StateDeserializer;
import ca.ubc.cs.beta.aclib.state.StateFactory;
import ca.ubc.cs.beta.aclib.state.StateSerializer;

public class NullStateFactory implements StateFactory {


	@Override
	public StateSerializer getStateSerializer(String id, int iteration)
			throws StateSerializationException {
		return new NullStateSerializer();
	}

	@Override
	public StateDeserializer getStateDeserializer(String id,
			int restoreIteration, ParamConfigurationSpace configSpace,
			OverallObjective overallObj,OverallObjective o2,  RunObjective runObj,
			List<ProblemInstance> instances, AlgorithmExecutionConfig execConfig)
			throws StateSerializationException {
		throw new UnsupportedOperationException("Can not deseialize a null state");
	}

	@Override
	public void purgePreviousStates() {
	}

	@Override
	public void copyFileToStateDir(String name, File f) {
	}

}
