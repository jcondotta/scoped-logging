package com.jcondotta.scopedlogging.domain.logging;

import java.util.Set;

public final class LogKey {

  public static final String OPERATION = "operation";
  public static final String OUTCOME = "outcome";
  public static final String REASON = "reason";
  public static final String DURATION_MS = "duration_ms";

  private static final Set<String> RESERVED = Set.of(OPERATION, OUTCOME, DURATION_MS);

  public static boolean isReserved(String key) {
    return key != null && RESERVED.contains(key);
  }

  private LogKey() {}
}
