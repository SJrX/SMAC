package ca.ubc.cs.beta.smac.wizard.questions.instances;

import java.util.Map;

import ca.ubc.cs.beta.smac.wizard.questions.AbstractQuestion;
import ca.ubc.cs.beta.smac.wizard.questions.Question;
import ca.ubc.cs.beta.smac.wizard.questions.QuestionKeys;

public class AskNameOfSingleInstanceQuestion extends AbstractQuestion {

	public AskNameOfSingleInstanceQuestion(Map<QuestionKeys, String> answers) {
		super(answers);
	}

	@Override
	public String getPrompt() {
		return "What is the name of the instance we should optimize?";
	}

	@Override
	public Question setAnswerAndGetNext(String answer) {
		this.answers.put(QuestionKeys.SINGLE_INSTANCE, answer);
		String trainingInstanceName = "single-training-instance-" + this.answers.get(QuestionKeys.SCENARIO_NAME) + ".txt";
		this.answers.put(QuestionKeys.INSTANCE_FILE, trainingInstanceName);
		InstanceFileCreater.createInstanceFile(trainingInstanceName, "answer");
		return new AskNameOfSingleValidationInstanceQuestion(this.answers);
	}

}
