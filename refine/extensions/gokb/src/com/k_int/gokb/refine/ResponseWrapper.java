package com.k_int.gokb.refine;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;


public class ResponseWrapper extends HttpServletResponseWrapper {

    public ResponseWrapper(HttpServletResponse response) {
        super(response);
    }
    
    private String redirectURL = null;

    @Override
    public void sendRedirect(String location)
            throws IOException {
        
        // Perform the default redirection and then grab the URL
        // for later retrieval.
        super.sendRedirect(location);
        redirectURL = location;
    }
    
    public String getRedirectURL() {        
        return redirectURL;
    }
}
