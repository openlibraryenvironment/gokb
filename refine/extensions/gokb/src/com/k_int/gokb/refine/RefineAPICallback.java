package com.k_int.gokb.refine;

import java.io.InputStream;


public class RefineAPICallback {
    protected void onSuccess(InputStream result) throws Exception {
        /* Do Nothing */
    }
    
    protected void onError(InputStream result, Exception e) throws Exception {
        
        // Throw the exception.
        throw e;
    }
    
    protected void complete(InputStream result) throws Exception {
        if (result != null) result.close();
    }
}