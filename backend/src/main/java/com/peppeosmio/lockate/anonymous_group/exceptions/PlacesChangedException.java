package com.peppeosmio.lockate.anonymous_group.exceptions;

import lombok.Getter;

/**
 * A location post carried place events computed on places that changed since: the phone has to
 * fetch them and compute the events again before they go out.
 */
@Getter
public class PlacesChangedException extends Exception {
  private final long placesVersion;

  public PlacesChangedException(long placesVersion) {
    super("places_changed");
    this.placesVersion = placesVersion;
  }
}
