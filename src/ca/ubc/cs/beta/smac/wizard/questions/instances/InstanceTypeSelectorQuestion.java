package ca.ubc.cs.beta.smac.wizard.questions.instances;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

import ca.ubc.cs.beta.aclib.misc.returnvalues.ACLibReturnValues;
import ca.ubc.cs.beta.smac.wizard.questions.AbstractQuestion;
import ca.ubc.cs.beta.smac.wizard.questions.Question;
import ca.ubc.cs.beta.smac.wizard.questions.QuestionKeys;
import ca.ubc.cs.beta.smac.wizard.questions.limits.ScenarioCPUTimeLimit;



public class InstanceTypeSelectorQuestion extends AbstractQuestion {

	public InstanceTypeSelectorQuestion(Map<QuestionKeys, String> answers) {
		super(answers);
	}

	@Override
	public String getPrompt() {
		
		return "SMAC can optimize an algorithm that works on particular instances. This scenario " +this.getDefaultValuePrompt(QuestionKeys.INSTANCE_TYPE_SELECTION)+ ":\n"+
			   "1) Does not use or care about instances\n"+
			   "2) Uses a single instance\n" + 
			   "3) Uses many instances";
			   
	}

	@Override
	public Question setAnswerAndGetNext(String answer) {
		
		answer = this.getValue(QuestionKeys.INSTANCE_TYPE_SELECTION, answer);
		
		if(answer.equals("1"))
		{
			String instanceName = "no-instance-" + this.answers.get(QuestionKeys.SCENARIO_NAME) + ".txt";
			
			this.answers.put(QuestionKeys.INSTANCE_FILE, instanceName);
			this.answers.put(QuestionKeys.VALIDATION_INSTANCE_FILE, instanceName);
			
			this.answers.put(QuestionKeys.NO_INSTANCES, "true");
			this.answers.put(QuestionKeys.INSTANCE_TYPE_SELECTION, "1");
			
			InstanceFileCreater.createInstanceFile(instanceName, "no_instance");
			
			return new ScenarioCPUTimeLimit(this.answers);
			
			
		} else if(answer.equals("2"))
		{
			this.answers.put(QuestionKeys.INSTANCE_TYPE_SELECTION, "2");
			return new AskNameOfSingleInstanceQuestion(this.answers);
		} else if(answer.equals("3"))
		{	
			this.answers.put(QuestionKeys.INSTANCE_TYPE_SELECTION, "3");
			return new AskNameOfInstanceFileQuestion(this.answers);
		} else
		{
			System.out.println("[ERROR] Don't know what " + answer + " is, please enter 1, 2 or 3?");
			return this;
		}
	}

}
