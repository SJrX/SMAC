package ca.ubc.cs.beta.smac.state.nullFactory;

import java.util.List;

import ca.ubc.cs.beta.config.AlgorithmExecutionConfig;
import ca.ubc.cs.beta.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.smac.OverallObjective;
import ca.ubc.cs.beta.smac.RunObjective;
import ca.ubc.cs.beta.smac.exceptions.StateSerializationException;
import ca.ubc.cs.beta.smac.state.StateDeserializer;
import ca.ubc.cs.beta.smac.state.StateFactory;
import ca.ubc.cs.beta.smac.state.StateSerializer;

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

}
