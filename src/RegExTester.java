import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class RegExTester {

	public static void main(String[] args)
	{
		
		String regex = ".*run for config \\d+ \\(0x[0-9A-Z]+\\) on instance \\d+ with seed (-?\\d+) and captime \\d+.*";
		//String regex = "running options \\d+ on instance \\d+ with seed (-?\\d+) and captime \\d+";
		Pattern pat = Pattern.compile(regex);
		
		//String line = "Scheduling run for config 2 (0x0005) on instance 8 with seed -1 and captime 10.437153343456984";
		String line = "Scheduling run for config 2 (0x0005) on instance 8 with seed -1 and captime 10.437153343456984";
		
		Matcher m = pat.matcher(line);
		
		System.out.println(m.find());
		
	}
}
