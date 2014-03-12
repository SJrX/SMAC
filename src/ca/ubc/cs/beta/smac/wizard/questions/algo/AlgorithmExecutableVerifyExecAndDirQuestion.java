package ca.ubc.cs.beta.smac.wizard.questions.algo;

import java.util.Map;

import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.base.cli.CommandLineAlgorithmRun;
import ca.ubc.cs.beta.smac.wizard.questions.AbstractQuestion;
import ca.ubc.cs.beta.smac.wizard.questions.Question;
import ca.ubc.cs.beta.smac.wizard.questions.QuestionKeys;
import ca.ubc.cs.beta.smac.wizard.questions.misc.PCSFileQuestion;

public class AlgorithmExecutableVerifyExecAndDirQuestion extends
		AbstractQuestion {

	public AlgorithmExecutableVerifyExecAndDirQuestion(
			Map<QuestionKeys, String> answers) {
		super(answers);
	}

	@Override
	public String getPrompt() {
		return "To execute the algorithm SMAC will execute the following commands:\ncd " + this.answers.get(QuestionKeys.EXEC_DIR) + CommandLineAlgorithmRun.COMMAND_SEPERATOR + "\n" + this.answers.get(QuestionKeys.EXEC) + "\n Does this look correct (can this be executed in a shell)? (Y/N)";
	}

	@Override
	public Question setAnswerAndGetNext(String answer)
	{
		if(answer.length() == 0)
		{
			answer = "Y";
		}
		String firstChar = answer.substring(0, 1).toUpperCase();
		
		if(firstChar.equals("Y"))
		{
			return new PCSFileQuestion(this.answers);
			
		} else if (firstChar.equals("N"))
		{
			System.out.println("Please re-enter the directory and executable");
			return new AlgorithmExecutableDirectoryQuestion(this.answers);
			
		} else
		{
			System.out.println("[ERROR] Didn't understand answer please answer only Y or N");
			return this;
		}
		
		
		
	}

}
