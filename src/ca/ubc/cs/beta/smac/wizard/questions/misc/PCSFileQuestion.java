package ca.ubc.cs.beta.smac.wizard.questions.misc;

import java.io.File;
import java.util.Map;

import ca.ubc.cs.beta.aclib.configspace.ParamFileHelper;
import ca.ubc.cs.beta.smac.wizard.questions.AbstractQuestion;
import ca.ubc.cs.beta.smac.wizard.questions.Question;
import ca.ubc.cs.beta.smac.wizard.questions.QuestionKeys;

public class PCSFileQuestion extends AbstractQuestion {

	public PCSFileQuestion(Map<QuestionKeys, String> answers) {
		super(answers);
	}

	@Override
	public String getPrompt() {
		return "SMAC requires a description of the configuration space of your algorithm\n"+
			   "This is outlined in the SMAC Manual section \"Algorithm Parameter File\"\n"+
			   "Which file contains the PCS file for the scenario " +this.getDefaultValuePrompt(QuestionKeys.PCS_FILE) + "?";
			
	}

	@Override
	public Question setAnswerAndGetNext(String answer) {
		
		answer = this.getValue(QuestionKeys.PCS_FILE, answer);
		
		File f = new File(answer);
		
		if(!f.canRead())
		{
			if(!f.exists())
			{
				System.out.println("[ERROR] Could not find file: " + answer);
			} else
			{
				System.out.println("[ERROR] Could not read file (check permissions?): " + answer);
			} 
			return this;
		}
		
		try {
			ParamFileHelper.getParamFileParser(f);
		} catch(RuntimeException e)
		{
			System.out.println("[ERROR] There was a problem parsing the PCS file: " + e.getMessage());
			return this;
		}
		
		this.answers.put(QuestionKeys.PCS_FILE, answer);
		return new RunObjectiveQuestion(this.answers);
		
	}
	

}
