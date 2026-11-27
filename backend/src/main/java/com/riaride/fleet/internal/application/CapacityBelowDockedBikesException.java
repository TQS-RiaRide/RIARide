package com.riaride.fleet.internal.application;

// The bikes already docked at the hub would not fit. The count is given, since it is the hub's own
// state, not a value the client sent.
public class CapacityBelowDockedBikesException extends RuntimeException {

  private final long dockedBikes;

  public CapacityBelowDockedBikesException(long dockedBikes) {
    super(
        "The hub has "
            + dockedBikes
            + " bikes docked, so its capacity cannot be lower. Move bikes elsewhere first.");
    this.dockedBikes = dockedBikes;
  }

  public long getDockedBikes() {
    return dockedBikes;
  }
}
