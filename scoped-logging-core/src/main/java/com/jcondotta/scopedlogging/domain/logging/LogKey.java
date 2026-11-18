package com.jcondotta.scopedlogging.domain.logging;

import java.util.Set;

/**
 * Standard attribute names used by completed scope events.
 * Operation, outcome and duration are managed by the scope lifecycle. Reason is an
 * ordinary business attribute and can be supplied or replaced by the application.
 */
public final class LogKey {

  /** Reserved key identifying the operation associated with the scope. */
  public static final String OPERATION = "operation";
  /** Reserved key holding the textual {@link LogOutcome#value() outcome}. */
  public static final String OUTCOME = "outcome";
  /** Non-reserved key for a business classification, including an explicit null value. */
  public static final String REASON = "reason";
  /** Reserved key for elapsed whole milliseconds through completion, excluding publication. */
  public static final String DURATION_MS = "duration_ms";

  private static final Set<String> RESERVED = Set.of(OPERATION, OUTCOME, DURATION_MS);

  /**
   * Tests whether a key is managed by the scope lifecycle rather than business enrichment.
   *
   * @param key attribute key to inspect, which may be null
   * @return true for operation, outcome or duration; false for all other keys, including null
   */
  public static boolean isReserved(String key) {
    return key != null && RESERVED.contains(key);
  }

  /** Prevents instantiation of the attribute-name holder. */
  private LogKey() {}
}
