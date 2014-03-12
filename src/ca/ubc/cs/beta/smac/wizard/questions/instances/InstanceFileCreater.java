package ca.ubc.cs.beta.smac.wizard.questions.instances;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import ca.ubc.cs.beta.aclib.misc.returnvalues.ACLibReturnValues;
import ca.ubc.cs.beta.smac.wizard.questions.QuestionKeys;

public class InstanceFileCreater {

	public static void createInstanceFile(String instanceFileName, String instance)
	{
		try {
			File f = new File(instanceFileName);
		
			f.createNewFile();
			
			if(!f.canWrite())
			{
				System.err.println("Cannot write file " + f.getAbsolutePath() + " cannot continue, please check permissions");
				System.exit(ACLibReturnValues.OTHER_EXCEPTION);
			}
			
			FileWriter fWrite = new FileWriter(f);
			
			fWrite.write(instance + "\n");
			fWrite.flush();
			fWrite.close();
		
		} catch(IOException e)
		{
			System.err.println("Cannot write instance file cannot continue: " +e.getClass().getSimpleName() + ":" +  e.getMessage());
			System.exit(ACLibReturnValues.OTHER_EXCEPTION);
			
		}
	
	}
}
