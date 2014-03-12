package ca.ubc.cs.beta.smac.wizard.questions.instances;

import java.io.File;
import java.util.Map;

import ca.ubc.cs.beta.smac.wizard.questions.AbstractQuestion;
import ca.ubc.cs.beta.smac.wizard.questions.Question;
import ca.ubc.cs.beta.smac.wizard.questions.QuestionKeys;
import ca.ubc.cs.beta.smac.wizard.questions.limits.ScenarioCPUTimeLimit;

public class AskNameOfValidationInstanceFileQuestion extends AbstractQuestion {

	public AskNameOfValidationInstanceFileQuestion(Map<QuestionKeys, String> answers) {
		super(answers);
	}

	@Override
	public String getPrompt() {
		return "A second set of instances is required to compute validation performance\n"
				+ "What is the name of the instance file" + this.getDefaultValuePrompt(QuestionKeys.VALIDATION_INSTANCE_FILE) + "?";
				
		
	}

	@Override
	public Question setAnswerAndGetNext(String answer) {
		
		answer = this.getValue(QuestionKeys.VALIDATION_INSTANCE_FILE, answer);
		File f = new File(answer);
		if(!f.exists())
		{
			System.out.println("[ERROR] Could not find validation instance file: " + f.getAbsolutePath());
			return this;
		}
		
		this.answers.put(QuestionKeys.VALIDATION_INSTANCE_FILE, answer);
		return new ScenarioCPUTimeLimit(this.answers);
	}

}
