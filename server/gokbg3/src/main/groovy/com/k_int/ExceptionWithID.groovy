package com.k_int

/**
 * @author Steve Osguthorpe <steve.osguthorpe@k-int.com>
 * Exception that adds an ID to the message so that it can be found easily when logged.
 */
class ExceptionWithID extends Exception {
  
  private long id = System.currentTimeMillis()

  /**
   * @param message
   */
  public ExceptionWithID (String message, String id) {
    super("[-${id}-] {message}")
  }

  /**
   * @param message
   * @param cause
   */
  public ExceptionWithID (String message, Throwable cause, String id) {
    super("[-${id}-] {message}", cause)
  }

}
