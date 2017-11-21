package com.k_int.gokb.refine;

import java.io.InputStream;


public class RefineAPICallback {

  public class GOKbAuthRequiredException extends Exception {
    private static final long serialVersionUID = -6051072967329719175L;

    public GOKbAuthRequiredException(String message, Throwable cause) {
      super(message, cause);
    }
  }
  
  public class GOKbPermissionDeniedException extends Exception {
    private static final long serialVersionUID = -8654196677186315352L;

    public GOKbPermissionDeniedException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  protected void onSuccess(InputStream result, int respCode) throws Exception {
    /* Do Nothing */
  }

  protected void onError(InputStream result, int respCode, Exception e) throws Exception {

    // If we have a 401 response then we need to redirect to login page.
    switch (respCode) {
      case  401 :
        throw new GOKbAuthRequiredException (
          "Authorisation failed. Please supply your username and password.", e
          );
        
      case 403 :
        throw new GOKbPermissionDeniedException (
          "You don't have permission to use the refine extension with this service. Please contact the administrator if you believe you should be able to use this service.", e
          );
      default:

        // Throw the exception.
        throw e;
    }
  }

  protected void complete(InputStream result) throws Exception {
    if (result != null) result.close();
  }
}