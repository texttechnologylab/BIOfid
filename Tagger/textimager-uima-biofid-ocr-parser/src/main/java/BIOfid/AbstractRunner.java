package BIOfid;

import com.google.common.collect.ImmutableList;
import org.apache.commons.cli.MissingArgumentException;

public abstract class AbstractRunner {
	
	protected static ImmutableList<String> params;
	
	protected static void getParams(String[] args) throws MissingArgumentException {
		params = ImmutableList.copyOf(args);
		
		if (params.isEmpty()) {
			throw new MissingArgumentException("No arguments given!\n");
		}
		
		if (Integer.max(params.indexOf("-h"), params.indexOf("--help")) > -1) {
			throw new MissingArgumentException("");
		}
	}
}
