package ca.ubc.cs.beta.smac.wizard.questions.instances;

import java.io.File;
import java.util.Map;

import ca.ubc.cs.beta.smac.wizard.questions.AbstractQuestion;
import ca.ubc.cs.beta.smac.wizard.questions.Question;
import ca.ubc.cs.beta.smac.wizard.questions.QuestionKeys;

public class AskNameOfFeatureFileQuestion extends AbstractQuestion {

	public AskNameOfFeatureFileQuestion(Map<QuestionKeys, String> answers) {
		super(answers);
	}

	@Override
	public String getPrompt() {
		return "An optional file can be specified containing features for every instance (see section \"Feature File Format\")\n" + 
				"Features for instances are recommended and can improve SMACs performance\n" +  
				"What is the name of the feature file? (Hit enter if none)";
				
		
	}

	@Override
	public Question setAnswerAndGetNext(String answer) {
		
		
		if(answer.trim().length() > 0)
		{
			File f = new File(answer);
			if(!f.exists())
			{
				System.out.println("[ERROR] Could not find feature file: " + f.getAbsolutePath());
				return this;
			}
			
			this.answers.put(QuestionKeys.FEATURE_FILE, answer);
		}
		return new AskNameOfValidationInstanceFileQuestion(this.answers);
	}

}
