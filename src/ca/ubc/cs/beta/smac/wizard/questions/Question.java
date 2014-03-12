package ca.ubc.cs.beta.smac.wizard.questions;

import java.util.Map;

public interface Question {

	public String getPrompt();
	
	public Question setAnswerAndGetNext(String answer);
	
	public Map<QuestionKeys, String> getAllAnswers();
	
	
}
