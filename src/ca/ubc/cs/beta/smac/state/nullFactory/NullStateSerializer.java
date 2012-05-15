package ca.ubc.cs.beta.smac.state.nullFactory;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.cs.beta.configspace.ParamConfiguration;
import ca.ubc.cs.beta.probleminstance.InstanceSeedGenerator;
import ca.ubc.cs.beta.smac.history.RunHistory;
import ca.ubc.cs.beta.smac.state.RandomPoolType;
import ca.ubc.cs.beta.smac.state.StateSerializer;

public class NullStateSerializer implements StateSerializer{
	
	private Logger log = LoggerFactory.getLogger(this.getClass());
	
	@Override
	public void setRunHistory(RunHistory runHistory) {

		
	}

	@Override
	public void setPRNG(RandomPoolType t, Random r) {

		
	}

	@Override
	public void setInstanceSeedGenerator(InstanceSeedGenerator gen) {

		
	}

	@Override
	public void save() {
		log.trace("Null State Serializer Selected, no data saved");
		
	}

	@Override
	public void setIncumbent(ParamConfiguration config) {
		
	}

}
