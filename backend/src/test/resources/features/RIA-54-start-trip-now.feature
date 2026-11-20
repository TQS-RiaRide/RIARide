@RIA-54
Feature: Start a trip now
  As a guest, I want to start a trip with an available bike right away,
  so that I can ride without booking in advance.

  Background:
    Given a hub "Marés Centro" with an available bike "EB-201" with 80 % battery
    And a guest "Rita Almeida" with an active stay at "Marés Centro" is logged in
    And the guest has a valid "stay" pass

  @TEST_RIA-151 @BR6 @wip
  Scenario: Starting a trip reserves the bike and sends the unlock command
    Given the guest has no trip in progress
    When the guest starts a trip with bike "EB-201"
    Then the response status is 202
    And the trip is "starting"
    And bike "EB-201" is "reserved"
    And an unlock command for bike "EB-201" is sent to the simulator

  @BR4 @wip
  Scenario: The trip starts when telemetry confirms the unlock
    Given the guest has a "starting" trip with bike "EB-201"
    When telemetry reports that bike "EB-201" is unlocked
    Then the trip is "in progress"
    And bike "EB-201" is "in use"
    And the guest's pass is active

  @BR4 @BR11 @wip
  Scenario: The trip fails when the unlock is not confirmed within 30 seconds
    Given the guest has a "starting" trip with bike "EB-201"
    When 30 seconds pass without telemetry confirming the unlock of bike "EB-201"
    Then the trip is "failed"
    And the guest's pass is not active
    And bike "EB-201" is "reserved" until the booking expires

  @BR6 @wip
  Scenario: A guest with a trip in progress cannot start another
    Given a hub "Marés Centro" with an available bike "EB-202" with 80 % battery
    And the guest has a trip in progress with bike "EB-201"
    When the guest starts a trip with bike "EB-202"
    Then the response is a problem detail with status 409
    And bike "EB-202" is "available"
