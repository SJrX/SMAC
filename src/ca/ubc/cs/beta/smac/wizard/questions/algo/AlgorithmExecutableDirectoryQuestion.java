package ca.ubc.cs.beta.smac.wizard.questions.algo;

import java.util.Map;

import ca.ubc.cs.beta.smac.wizard.questions.AbstractQuestion;
import ca.ubc.cs.beta.smac.wizard.questions.Question;
import ca.ubc.cs.beta.smac.wizard.questions.QuestionKeys;

public class AlgorithmExecutableDirectoryQuestion extends AbstractQuestion {

	public AlgorithmExecutableDirectoryQuestion(
			Map<QuestionKeys, String> answers) {
		super(answers);
	}

	@Override
	public String getPrompt() {
		return "SMAC requires an algorithm conform to a specific command line interface when invoked\n" + 
				"The section of the manual entitled \"Wrappers\" will give you instructions on the specifics of the interface\n" + 
				"What directory will the algorithm be invoked in "+ this.getDefaultValuePrompt(QuestionKeys.EXEC_DIR) + "?"; 
	}

	@Override
	public Question setAnswerAndGetNext(String answer) {
		
		answer = this.getValue(QuestionKeys.EXEC_DIR, answer);
		this.answers.put(QuestionKeys.EXEC_DIR, answer);
		return new AlgorithmExecutableQuestion(this.answers);
	}

}
