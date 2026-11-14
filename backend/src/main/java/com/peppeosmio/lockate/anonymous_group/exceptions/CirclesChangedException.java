package com.peppeosmio.lockate.anonymous_group.exceptions;

import lombok.Getter;

/**
 * A location post carried circle events computed on circles that changed since: the phone has to
 * fetch them and compute the events again before they go out.
 */
@Getter
public class CirclesChangedException extends Exception {
  private final long circlesVersion;

  public CirclesChangedException(long circlesVersion) {
    super("circles_changed");
    this.circlesVersion = circlesVersion;
  }
}
