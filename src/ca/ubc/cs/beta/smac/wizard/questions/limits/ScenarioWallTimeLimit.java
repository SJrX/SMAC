package ca.ubc.cs.beta.smac.wizard.questions.limits;

import java.util.Map;

import ca.ubc.cs.beta.smac.wizard.questions.AbstractQuestion;
import ca.ubc.cs.beta.smac.wizard.questions.Question;
import ca.ubc.cs.beta.smac.wizard.questions.QuestionKeys;

public class ScenarioWallTimeLimit extends AbstractQuestion {

	public ScenarioWallTimeLimit(Map<QuestionKeys, String> answers) {
		super(answers);
	}

	@Override
	public String getPrompt() {
		
		return "How much wall time is the scenario allowed (in seconds), default is infinite"+this.getDefaultValuePrompt(QuestionKeys.SCENARIO_WALLLIMIT)+"?";
	}

	@Override
	public Question setAnswerAndGetNext(String answer) {
		answer = this.getValue(QuestionKeys.SCENARIO_WALLLIMIT, answer);
		
		if(answer.length() > 0)
		{
			try {
			Integer i = Integer.valueOf(answer);
			this.answers.put(QuestionKeys.SCENARIO_WALLLIMIT, answer);
			if( i <= 0 )
			{
				throw new NumberFormatException();
			} 
			} catch(NumberFormatException e)
			{
				System.err.println("[ERROR] Wall time must be > 0 and an integer");
				return this;
			}
		} 
		return new ScenarioRunCountLimit(this.answers);
		
	}

}
