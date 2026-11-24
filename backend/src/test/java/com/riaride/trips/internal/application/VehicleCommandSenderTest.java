package com.riaride.trips.internal.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.telemetry.VehicleCommands;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): the trips module's commands to the bike (RIA-54). A requested start sends
// UNLOCK; a failed start sends LOCK at the hub the bike was reserved at. That they are only sent
// after the transaction commits is checked in StartTripApiIT.
class VehicleCommandSenderTest {

  private final VehicleCommands commands = mock(VehicleCommands.class);
  private final VehicleCommandSender sender = new VehicleCommandSender(commands);

  @Requirement("RIA-54")
  @Test
  void shouldUnlockTheBikeWhenATripStartIsRequested() {
    sender.on(new TripStartRequested("EB-201"));

    verify(commands).unlock("EB-201");
  }

  @Requirement("RIA-54")
  @Test
  void shouldLockTheBikeAtItsHubWhenAStartFails() {
    sender.on(new TripFailed("EB-201", UUID.fromString("ded64655-0ca3-5c5c-9dd8-c641407a63c5")));

    verify(commands).lock("EB-201", "ded64655-0ca3-5c5c-9dd8-c641407a63c5");
  }

  @Requirement("RIA-54")
  @Test
  void shouldLockTheBikeWhereItIsWhenItsHubIsUnknown() {
    sender.on(new TripFailed("EB-201", null));

    verify(commands).lock("EB-201", null);
  }

  // The command is sent after the trip is stored: a failure there must not turn the guest's 202
  // into a 500 for a trip that exists. The trip then times out (BR13).
  @Requirement("RIA-54")
  @Test
  void shouldNotFailTheCallerWhenTheUnlockCannotBeSent() {
    doThrow(new IllegalStateException("broker gone")).when(commands).unlock("EB-201");

    assertThatCode(() -> sender.on(new TripStartRequested("EB-201"))).doesNotThrowAnyException();
  }

  @Requirement("RIA-54")
  @Test
  void shouldNotFailTheCallerWhenTheLockCannotBeSent() {
    doThrow(new IllegalStateException("broker gone")).when(commands).lock("EB-201", null);

    assertThatCode(() -> sender.on(new TripFailed("EB-201", null))).doesNotThrowAnyException();
  }
}
