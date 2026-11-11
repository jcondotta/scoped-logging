package com.jcondotta.scopedlogging.domain.logging;

public enum LogOutcome {

  SUCCESS("success"), FAILURE("failure");

  private final String value;

  LogOutcome(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
