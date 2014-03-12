package ca.ubc.cs.beta.smac.wizard.questions.misc;

import java.util.Map;

import ca.ubc.cs.beta.smac.wizard.questions.AbstractQuestion;
import ca.ubc.cs.beta.smac.wizard.questions.Question;
import ca.ubc.cs.beta.smac.wizard.questions.QuestionKeys;
import ca.ubc.cs.beta.smac.wizard.questions.algo.AlgorithmExecutableCutoffTimeQuestion;

public class OverallObjectiveQuestion extends AbstractQuestion {

	public OverallObjectiveQuestion(Map<QuestionKeys, String> answers) {
		super(answers);
	}

	@Override
	public String getPrompt() {
		return "How should SMAC penalize runs that cannot complete on time?\nThe higher the penalty the more SMAC will try and find configurations that solve more configurations"+this.getDefaultValuePrompt(QuestionKeys.OVERALL_OBJ)+":\n1) Don't Penalize (MEAN) \n2) Penalize them by a factor of 10 (MEAN10)\n3) Penalize them by a factor of 1000 (MEAN1000)";
	}

	@Override
	public Question setAnswerAndGetNext(String answer) {
		answer = this.getValue(QuestionKeys.OVERALL_OBJ, answer);
		
		
		if(answer.toUpperCase().equals("MEAN") || answer.equals("1"))
		{
			this.answers.put(QuestionKeys.OVERALL_OBJ, "MEAN");
		} else if(answer.toUpperCase().equals("MEAN10") || answer.equals("2") || answer.toUpperCase().equals("PAR10"))
		{
			this.answers.put(QuestionKeys.OVERALL_OBJ, "MEAN10");
			
		} else if(answer.toUpperCase().equals("MEAN1000") || answer.equals("3") || answer.toUpperCase().equals("PAR1000"))
		{
			this.answers.put(QuestionKeys.OVERALL_OBJ, "MEAN1000");
		} else
		{
			System.out.println("[ERROR] Not sure what objective " + answer + " is, please select only 1,2 or 3");
			return this;
		}
		
		return new AlgorithmExecutableCutoffTimeQuestion(this.answers);
	}

}
