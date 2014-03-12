package ca.ubc.cs.beta.smac.wizard.questions.misc;

import java.util.Map;

import ca.ubc.cs.beta.smac.wizard.questions.AbstractQuestion;
import ca.ubc.cs.beta.smac.wizard.questions.Question;
import ca.ubc.cs.beta.smac.wizard.questions.QuestionKeys;
import ca.ubc.cs.beta.smac.wizard.questions.algo.AlgorithmExecutableCutoffTimeQuestion;

public class RunObjectiveQuestion extends AbstractQuestion {

	public RunObjectiveQuestion(Map<QuestionKeys, String> answers) {
		super(answers);
	}

	@Override
	public String getPrompt() {
		return "SMAC can try to minimize either the reported runtime, or the quality of the target algorithm. Which should it optimize "+this.getDefaultValuePrompt(QuestionKeys.RUN_OBJ)+"?\n1) RUNTIME\n2) QUALITY";
				
				
	}

	@Override
	public Question setAnswerAndGetNext(String answer) {
		
		answer = this.getValue(QuestionKeys.RUN_OBJ, answer);
		
		if(answer.toUpperCase().equals("RUNTIME") || answer.equals("1"))
		{
			this.answers.put(QuestionKeys.RUN_OBJ, "RUNTIME");
		} else if(answer.toUpperCase().equals("QUALITY") || answer.equals("2"))
		{
			this.answers.put(QuestionKeys.RUN_OBJ, "QUALITY");
		} else
		{
			System.out.println("[ERROR] Not sure what objective " + answer + " is, please select only \"1\" or \"2\"");
			return this;
		}
		
		return new OverallObjectiveQuestion(this.answers);
	}

}
