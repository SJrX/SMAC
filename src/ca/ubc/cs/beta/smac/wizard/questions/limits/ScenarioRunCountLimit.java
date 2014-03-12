package ca.ubc.cs.beta.smac.wizard.questions.limits;

import java.util.Map;

import ca.ubc.cs.beta.smac.wizard.questions.AbstractQuestion;
import ca.ubc.cs.beta.smac.wizard.questions.Question;
import ca.ubc.cs.beta.smac.wizard.questions.QuestionKeys;

public class ScenarioRunCountLimit extends AbstractQuestion {

	public ScenarioRunCountLimit(Map<QuestionKeys, String> answers) {
		super(answers);
	}

	@Override
	public String getPrompt() {
		
		return "How many invokations of the target algorithm is the scenario allowed (in seconds), default is infinite "+this.getDefaultValuePrompt(QuestionKeys.SCENARIO_CALLLIMIT)+"?";
	}

	@Override
	public Question setAnswerAndGetNext(String answer) {
		answer = this.getValue(QuestionKeys.SCENARIO_CALLLIMIT, answer);
		if(answer.length() > 0)
		{
			try {
				Integer i = Integer.valueOf(answer);
				this.answers.put(QuestionKeys.SCENARIO_CALLLIMIT, answer);
				
				if( i <= 0 )
				{
					throw new NumberFormatException();
				} 
			} catch(NumberFormatException e)
			{
				System.err.println("[ERROR] Number of runs must be >0 and an integer");
				return this;
			}
		} 
		return null;
		
	}

}
