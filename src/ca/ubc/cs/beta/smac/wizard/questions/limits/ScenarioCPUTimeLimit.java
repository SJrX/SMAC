package ca.ubc.cs.beta.smac.wizard.questions.limits;

import java.util.Map;

import ca.ubc.cs.beta.smac.wizard.questions.AbstractQuestion;
import ca.ubc.cs.beta.smac.wizard.questions.Question;
import ca.ubc.cs.beta.smac.wizard.questions.QuestionKeys;

public class ScenarioCPUTimeLimit extends AbstractQuestion {

	public ScenarioCPUTimeLimit(Map<QuestionKeys, String> answers) {
		super(answers);
	}

	@Override
	public String getPrompt() {
		
		return "How much CPU time is the scenario allowed (in seconds), default is infinite " + this.getDefaultValuePrompt(QuestionKeys.SCENARIO_CPULIMIT) + "?";
	}

	@Override
	public Question setAnswerAndGetNext(String answer) {
		answer = this.getValue(QuestionKeys.SCENARIO_CPULIMIT, answer);
		
		if(answer.length() > 0)
		{
			try {
				Integer i = Integer.valueOf(answer);
				this.answers.put(QuestionKeys.SCENARIO_CPULIMIT, answer);
				if( i <= 0 )
				{
					throw new NumberFormatException();
				} 
			} catch(NumberFormatException e)
			{
				System.err.println("[ERROR] CPU time must be > 0 and an integer");
				return this;
			}
		} 
		return new ScenarioWallTimeLimit(this.answers);
		
	}

}
