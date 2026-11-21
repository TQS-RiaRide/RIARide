@RIA-58
Feature: Receive telemetry from the simulator
  As an operator, I want the platform to update each bike's state from telemetry,
  so that the fleet shows up-to-date battery, location and lock state.

  @TEST_RIA-155 @wip
  Scenario: A telemetry reading updates the bike's state in less than 5 seconds
    Given a hub "Marés Centro" with an available bike "EB-501" with 80 % battery
    When the simulator publishes a telemetry reading for bike "EB-501" via MQTT with:
      | battery | 64           |
      | hub     | Marés Centro |
      | lock    | locked       |
    Then within 5 seconds bike "EB-501" has 64 % battery
    And bike "EB-501" is at hub "Marés Centro" and "locked"
