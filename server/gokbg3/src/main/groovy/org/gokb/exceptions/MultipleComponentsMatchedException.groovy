package org.gokb.exceptions;

public class MultipleComponentsMatchedException extends java.lang.Exception {

  public String proposed_title
  public List identifiers
  public List matched_ids
  public String matched_title

  public MultipleComponentsMatchedException(String message) {
    super(message)
  }

  public MultipleComponentsMatchedException(String message, Throwable cause) {
    super(message,cause);
  }

  public MultipleComponentsMatchedException(String message,
                                              String proposed_title,
                                              List identifiers,
                                              List matched_ids) {
    super(message)
    this.proposed_title=proposed_title
    this.identifiers=identifiers
    this.matched_ids=matched_ids
  }

}