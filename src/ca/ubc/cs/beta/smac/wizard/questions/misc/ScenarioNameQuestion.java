package ca.ubc.cs.beta.smac.wizard.questions.misc;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import ca.ubc.cs.beta.smac.wizard.questions.AbstractQuestion;
import ca.ubc.cs.beta.smac.wizard.questions.Question;
import ca.ubc.cs.beta.smac.wizard.questions.QuestionKeys;
import ca.ubc.cs.beta.smac.wizard.questions.algo.AlgorithmExecutableDirectoryQuestion;

public class ScenarioNameQuestion extends AbstractQuestion {


	
	public ScenarioNameQuestion(Map<QuestionKeys, String> defaultAnswers) {
		super(defaultAnswers);
	}

	@Override
	public String getPrompt() {
		return "Name of the scenario you would like to create " + this.getDefaultValuePrompt(QuestionKeys.SCENARIO_NAME) + "?";
	}

	@Override
	public Question setAnswerAndGetNext(String answer) {
		
		
		answer = this.getValue(QuestionKeys.SCENARIO_NAME, answer);
		File f = new File(answer + ".txt");
		
		boolean fileCreated;
		try {
			fileCreated = f.createNewFile();
		} catch (IOException e) {
			System.out.println("[ERROR] Exception occurred, please try again, type:" + e.getClass().getSimpleName() + " message:" + e.getMessage()  );
			return this;
		}
		
		if(!fileCreated)
		{
			
			if(f.exists())
			{
				System.out.println("[ERROR] " + answer + ".txt already exists, please try again");
				return this;
			} else
			{
				System.out.println("[ERROR] " + answer + ".txt could not be created, check permissions on the directory: " + f.getParentFile().getAbsolutePath());
				return this;
			}
		}
		
		f.delete();
		answers.put(QuestionKeys.SCENARIO_NAME, answer);
		return new AlgorithmExecutableDirectoryQuestion(answers);
		
	}

	

}
