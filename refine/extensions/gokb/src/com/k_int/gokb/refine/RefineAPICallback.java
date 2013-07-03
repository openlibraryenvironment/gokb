package com.k_int.gokb.refine;

import java.io.InputStream;


public class RefineAPICallback {
    protected void onSuccess(InputStream result, int respCode) throws Exception {
        /* Do Nothing */
    }

    protected void onError(InputStream result, int respCode, Exception e) throws Exception {

        // Throw the exception.
        throw e;
    }

    protected void complete(InputStream result) throws Exception {
        if (result != null) result.close();
    }
}