package ca.ubc.cs.beta.smac.wizard.questions.instances;

import java.util.Map;

import ca.ubc.cs.beta.smac.wizard.questions.AbstractQuestion;
import ca.ubc.cs.beta.smac.wizard.questions.Question;
import ca.ubc.cs.beta.smac.wizard.questions.QuestionKeys;
import ca.ubc.cs.beta.smac.wizard.questions.limits.ScenarioCPUTimeLimit;

public class AskNameOfSingleValidationInstanceQuestion extends AbstractQuestion {

	public AskNameOfSingleValidationInstanceQuestion(Map<QuestionKeys, String> answers) {
		super(answers);
	}

	@Override
	public String getPrompt() {
		return "What is the name of the instance we should validate on?";
	}

	@Override
	public Question setAnswerAndGetNext(String answer) {
		this.answers.put(QuestionKeys.SINGLE_INSTANCE, answer);
		String validationInstanceName = "single-validation-instance-" + this.answers.get(QuestionKeys.SCENARIO_NAME) + ".txt";
		this.answers.put(QuestionKeys.VALIDATION_INSTANCE_FILE, validationInstanceName);
		InstanceFileCreater.createInstanceFile(validationInstanceName, answer);
		return new ScenarioCPUTimeLimit(this.answers);
	}

}
