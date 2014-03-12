package ca.ubc.cs.beta.smac.wizard.questions.algo;

import java.util.Map;

import ca.ubc.cs.beta.smac.wizard.questions.AbstractQuestion;
import ca.ubc.cs.beta.smac.wizard.questions.Question;
import ca.ubc.cs.beta.smac.wizard.questions.QuestionKeys;
import ca.ubc.cs.beta.smac.wizard.questions.instances.InstanceTypeSelectorQuestion;

public class AlgorithmExecutableDeterministicQuestion extends AbstractQuestion {

	public AlgorithmExecutableDeterministicQuestion(
			Map<QuestionKeys, String> answers) {
		super(answers);
	}

	@Override
	public String getPrompt() {
		return "Is your algorithm deterministic or randomized (i.e. will changing the seed affect measurements)" + this.getDefaultValuePrompt(QuestionKeys.DETERMINISTIC_ANSWER) + "?\n1) Randomized\n2) Deterministic";
	}

	@Override
	public Question setAnswerAndGetNext(String answer) {
		
		answer = this.getValue(QuestionKeys.DETERMINISTIC_ANSWER, answer);
		if(answer.length() == 0)
		{
			answer = "Y";
		}
		String firstChar = answer.substring(0, 1).toUpperCase();
		
		if(firstChar.equals("1") || firstChar.equals("Y"))
		{
			this.answers.put(QuestionKeys.DETERMINISTIC_ANSWER,"1");
			this.answers.put(QuestionKeys.DETERMINISTIC,"0");
			
		} else if (firstChar.equals("2") || firstChar.equals("N"))
		{
			this.answers.put(QuestionKeys.DETERMINISTIC_ANSWER,"2");
			this.answers.put(QuestionKeys.DETERMINISTIC,"1");
			
		} else
		{
			System.out.println("[ERROR] Didn't understand answer please answer only 1 or 2");
			return this;
		}
		
		return new InstanceTypeSelectorQuestion(this.answers);
		
	}

}
