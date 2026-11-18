package com.jcondotta.scopedlogging.domain.logging;

/**
 * Business result recorded under {@link LogKey#OUTCOME}, independently of event severity.
 * Normal completion defaults to success; an escaping throwable forces failure.
 */
public enum LogOutcome {

  /** Successful result, either implicit on normal return or explicitly declared. */
  SUCCESS("success"),
  /** Failed result, either explicitly declared or caused by an escaping throwable. */
  FAILURE("failure");

  private final String value;

  /**
   * Associates an outcome with its structured attribute value.
   *
   * @param value textual representation used in completed events
   */
  LogOutcome(String value) {
    this.value = value;
  }

  /**
   * Returns the lowercase representation stored in the event's outcome attribute.
   *
   * @return {@code success} or {@code failure}
   */
  public String value() {
    return value;
  }
}
