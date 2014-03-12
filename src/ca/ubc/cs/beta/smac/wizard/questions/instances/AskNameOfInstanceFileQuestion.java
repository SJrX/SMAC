package ca.ubc.cs.beta.smac.wizard.questions.instances;

import java.io.File;
import java.util.Map;

import ca.ubc.cs.beta.smac.wizard.questions.AbstractQuestion;
import ca.ubc.cs.beta.smac.wizard.questions.Question;
import ca.ubc.cs.beta.smac.wizard.questions.QuestionKeys;

public class AskNameOfInstanceFileQuestion extends AbstractQuestion {

	public AskNameOfInstanceFileQuestion(Map<QuestionKeys, String> answers) {
		super(answers);
	}

	@Override
	public String getPrompt() {
		return "An instance file containing a list of instances (see section \"Instance File Format\") is required\n"
				+ "What is the name of the instance file" + this.getDefaultValuePrompt(QuestionKeys.INSTANCE_FILE) + "?";
				
		
	}

	@Override
	public Question setAnswerAndGetNext(String answer) {
		answer = this.getValue(QuestionKeys.INSTANCE_FILE, answer);
		
		File f = new File(answer);
		if(!f.exists())
		{
			System.out.println("[ERROR] Could not find instance file: " + f.getAbsolutePath());
			return this;
		}
		
		this.answers.put(QuestionKeys.INSTANCE_FILE, answer);
		
		
		
		return new AskNameOfFeatureFileQuestion(this.answers);
		
	}

}
