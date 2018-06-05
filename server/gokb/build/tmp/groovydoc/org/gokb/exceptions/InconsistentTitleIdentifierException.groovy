package org.gokb.exceptions;

public class InconsistentTitleIdentifierException extends java.lang.Exception {

  public String proposed_title
  public List identifiers
  public Long matched_title_id
  public String matched_title

  public InconsistentTitleIdentifierException(String message) {
    super(message)
  }

  public InconsistentTitleIdentifierException(String message, Throwable cause) {
    super(message,cause);
  }

  public InconsistentTitleIdentifierException(String message,
                                              String proposed_title,
                                              List identifiers,
                                              Long matched_title_id,
                                              String matched_title) {
    super(message)
    this.proposed_title=proposed_title
    this.identifiers=identifiers
    this.matched_title_id=matched_title_id
    this.matched_title=matched_title
  }

}
