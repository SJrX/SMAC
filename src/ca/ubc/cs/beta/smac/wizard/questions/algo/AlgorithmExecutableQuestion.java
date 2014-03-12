package ca.ubc.cs.beta.smac.wizard.questions.algo;

import java.util.Map;

import ca.ubc.cs.beta.smac.wizard.questions.AbstractQuestion;
import ca.ubc.cs.beta.smac.wizard.questions.Question;
import ca.ubc.cs.beta.smac.wizard.questions.QuestionKeys;
import ca.ubc.cs.beta.smac.wizard.questions.misc.PCSFileQuestion;

public class AlgorithmExecutableQuestion extends AbstractQuestion {

	

	public AlgorithmExecutableQuestion(Map<QuestionKeys, String> answers) {
		super(answers);
	}

	@Override
	public String getPrompt() {
		return "What is the command-line call of the algorithm to invoke (specify this exactly you would invoke it with a shell (e.g. ./targetAlgorithm )"+ this.getDefaultValuePrompt(QuestionKeys.EXEC) + "?";
		
	}

	@Override
	public Question setAnswerAndGetNext(String answer) {
		answer = this.getValue(QuestionKeys.EXEC, answer);
		
		this.answers.put(QuestionKeys.EXEC, answer);
		return new AlgorithmExecutableVerifyExecAndDirQuestion(this.answers);
	}

}
