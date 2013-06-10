package com.k_int.gokb.refine.functions;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONWriter;

import com.google.refine.grel.Function;

public class ExtractHost implements Function {
	
	private static final String REGEX = "^(.*\\:\\/\\/)?([^\\/]*).*";

    @Override
    public String call(Properties bindings, Object[] args) {
    	
    	String ret = null;
    	
    	if (args.length >= 1) {
    		Object url = args[0];
    		if (url != null && url instanceof String) {
    			
    			// Default the return value to the input
    			ret = (String)url;
    			
    			// Try and extract the organisation.
    			Pattern exp = Pattern.compile(REGEX);
    			Matcher m = exp.matcher((String) url);
    			if (m.matches()) {
    				// Try and get the list of matches.
    				String server = m.group(2);
    				String[] parts = server.split("\\.");
    				
    				// Check for second parameter.
    				Long number;
    				if (args.length > 1 && ((number = (Long) args[1]) != null )) {
    					
    					if (number < parts.length) {
    						// Only return "number" of parts from the right.
    						String[] newParts = new String[number.intValue()];
    						System.arraycopy(parts, (int)(parts.length - number), newParts, 0, number.intValue());
    						
    						// Set to parts.
    						parts = newParts;
    					}
    					
    					ret = "";
        				for (int i=0; i<parts.length; i++) {
        					ret += (i>0 ? "." : "") + parts[i];
        				}
    				} else {
    					ret = server;
    				}
    			}
    		}
    	}
    	
    	return ret;
    }

    
    @Override
    public void write(JSONWriter writer, Properties options)
        throws JSONException {
    
        writer.object();
        writer.key("description"); writer.value("Tries to parse a host from the data.");
        writer.key("params"); writer.value("value, Number of segments to return working from right to left of the matching host.");
        writer.key("returns"); writer.value("Either the matched host or the original value passed in.");
        writer.endObject();
    }
}
