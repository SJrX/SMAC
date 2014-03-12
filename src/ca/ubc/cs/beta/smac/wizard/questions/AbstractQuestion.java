package ca.ubc.cs.beta.smac.wizard.questions;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

public abstract class AbstractQuestion implements Question {

	protected final Map<QuestionKeys, String> answers;
	
	public AbstractQuestion(Map<QuestionKeys, String> answers)
	{
		this.answers = new EnumMap<QuestionKeys,String>(QuestionKeys.class);
		this.answers.putAll(answers);
	}
	
	public Map<QuestionKeys, String> getAllAnswers()
	{
		return Collections.unmodifiableMap(answers);
	}
	
	protected String getDefaultValue(QuestionKeys k)
	{
		return this.answers.get(k);
	}
	
	protected String getDefaultValuePrompt(QuestionKeys k)
	{
		String malValue = this.answers.get(k);
		if( malValue != null  && !malValue.trim().equals(""))
		{
			 return "[" + malValue + "]"; 
		}
		return "";
	}
	
	protected String getValue(QuestionKeys k, String userPrompt)
	{
		if((userPrompt != null) && userPrompt.trim().length() > 0)
		{
			return userPrompt;
		}
		
		String defaultValue = getDefaultValue(k);
		
		if(defaultValue != null && defaultValue.trim().length() > 0)
		{
			return defaultValue;
		}
		
		return userPrompt;
	}
	
}
