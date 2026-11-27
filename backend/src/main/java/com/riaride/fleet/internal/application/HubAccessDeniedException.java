package com.riaride.fleet.internal.application;

public class HubAccessDeniedException extends RuntimeException {

  public HubAccessDeniedException() {
    super("A hotel manager can change only the hubs of their hotel and the tourist spots.");
  }
}
