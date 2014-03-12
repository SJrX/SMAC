package ca.ubc.cs.beta.smac.wizard.questions.algo;

import java.util.Map;

import ca.ubc.cs.beta.smac.wizard.questions.AbstractQuestion;
import ca.ubc.cs.beta.smac.wizard.questions.Question;
import ca.ubc.cs.beta.smac.wizard.questions.QuestionKeys;
import ca.ubc.cs.beta.smac.wizard.questions.instances.InstanceTypeSelectorQuestion;

public class AlgorithmExecutableCutoffTimeQuestion extends AbstractQuestion
		implements Question {

	public AlgorithmExecutableCutoffTimeQuestion(
			Map<QuestionKeys, String> answers) {
		super(answers);
	}

	@Override
	public String getPrompt() {
		return "What is the maximum amount of time a single invocation of the target algorithm should be allowed to run for (in seconds)?" + this.getDefaultValuePrompt(QuestionKeys.CUTOFF_TIME) + "?";
	}

	@Override
	public Question setAnswerAndGetNext(String answer) {
		answer = this.getValue(QuestionKeys.CUTOFF_TIME, answer);
		
		try {
			Integer i = Integer.valueOf(answer);
			if (i <= 0)
			{
				throw new NumberFormatException();
			}
			
			this.answers.put(QuestionKeys.CUTOFF_TIME, answer);
			
			return new AlgorithmExecutableDeterministicQuestion(this.answers);
			
		} catch(NumberFormatException e)
		{
			System.out.println("[ERROR] didn't understand " + answer + " enter a value in seconds only, value must be a positive integer.");
			return this;
		}
		
	}

}
