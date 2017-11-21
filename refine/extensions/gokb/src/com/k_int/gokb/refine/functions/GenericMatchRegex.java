/*

Copyright 2010, Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
    * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,           
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY           
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package com.k_int.gokb.refine.functions;

import java.util.Properties;

import org.json.JSONException;
import org.json.JSONWriter;

import com.google.refine.expr.functions.strings.Match;

public class GenericMatchRegex extends Match {
	
	private String regex;
	private int offset = 0;
	
	public GenericMatchRegex(String regex, int offset) {
		this.regex = regex;
		this.offset = offset;
	}

    @Override
    public Object call(Properties bindings, Object[] args) {
    	
    	Object result = null;
    	
    	// Fire the Match call with the regex of this class.
        if (args.length == 1) {
        	
        	// Construct a new args array and append the regex.
        	Object[] newArgs = new Object[args.length + 1];
        	System.arraycopy(args, 0, newArgs, 0, args.length);
        	
        	// Append the regex.
        	newArgs[newArgs.length - 1] = regex;
        	
        	// Call the regex.
        	result = super.call(bindings, newArgs);
        	
        	// Apply an offset if we have one.
        	if (result != null && result instanceof String[] && offset > 0) {
        		String[] resArr = (String[])result;
        		if (offset < resArr.length) {
        			String[] newResult = new String[resArr.length - (offset)];
        			System.arraycopy(resArr, offset, newResult, 0, newResult.length);
        			result = newResult;
        		}
        	}
        }
        
        return result;
    }
    
    @Override
    public void write(JSONWriter writer, Properties options)
        throws JSONException {
    
        writer.object();
        writer.key("description"); writer.value("Returns an array of the groups matched using the regular expression '" + regex + "'" );
        writer.key("params"); writer.value("o");
        writer.key("returns"); writer.value("array of strings");
        writer.endObject();
    }
}
