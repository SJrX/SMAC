package ca.ubc.cs.beta.smac.wizard.questions;

public enum QuestionKeys {

	SCENARIO_NAME,
	EXEC_DIR ("execdir"),
	EXEC("algo"),
	RUN_OBJ("run_obj"),
	PCS_FILE("paramfile"),
	CUTOFF_TIME("target_run_cputime_limit"),
	INSTANCE_FILE("instance_file"),
	SINGLE_INSTANCE,
	NO_INSTANCES,
	VALIDATION_INSTANCE_FILE("test_instance_file"),
	DETERMINISTIC("deterministic"),
	SCENARIO_CPULIMIT("cputime_limit"),
	SCENARIO_WALLLIMIT("wallclock_limit"),
	FEATURE_FILE("feature_file"),
	OVERALL_OBJ("overall_obj"), 
	SCENARIO_CALLLIMIT("runcount-limit"),
	INSTANCE_TYPE_SELECTION,
	DETERMINISTIC_ANSWER;
	
	private final String scenario_key;
	
	QuestionKeys()
	{
		this.scenario_key = "";
	}
	
	QuestionKeys(String key)
	{
		this.scenario_key = key;
	}
	
	public String getScenarioFileKeyName()
	{
		return scenario_key;
	}
}
