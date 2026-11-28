@RIA-97 @wip
Feature: Automatic ticket for a fault reported by telemetry
  As an operator, I want a fault reported by a bike to open a maintenance ticket immediately,
  so that the repair starts without waiting for a guest to complain.

  The scenarios have no step definitions yet (@wip, excluded from CI); RIA-97 adds them. The
  simulator reports a fault as a fault event on MQTT (contracts/mqtt/fault-event.schema.json), with
  a messageId and a faultCode (BRAKES, FLAT_TYRE, BATTERY, LOCK or LIGHTS). The ticket gets the
  same 24-hour deadline as one an operator opens (RIA-92). One ticket is opened per fault message:
  a message received again opens no second ticket, and a different fault opens its own (Product
  Owner decision).

  @TEST_RIA-188
  Scenario: A fault reported for an available bike opens a ticket and takes the bike out of service
    Given a hub "Marés Centro" with these bikes:
      | code   | status    | battery |
      | EB-921 | available | 80      |
    When the simulator reports a "BRAKES" fault for bike "EB-921"
    Then a ticket for bike "EB-921" is opened from telemetry, due 24 hours after it was opened
    And bike "EB-921" is "maintenance"

  @BR10
  Scenario: A fault reported during a trip opens a ticket, and the bike goes to maintenance when the trip ends
    Given a hub "Marés Centro" with an available bike "EB-922" with 80 % battery
    And a hub "Marés Praia" with capacity 10 and 9 bikes docked
    And a guest "Rita Almeida" with an active stay at "Marés Centro" is logged in
    And the guest has a valid "stay" pass
    And the guest has a trip in progress with bike "EB-922"
    When the simulator reports a "BRAKES" fault for bike "EB-922"
    Then a ticket for bike "EB-922" is opened from telemetry, due 24 hours after it was opened
    And the trip is "in progress"
    And bike "EB-922" is "in use"
    When the guest ends the trip at hub "Marés Praia"
    Then the response status is 200
    And bike "EB-922" is "maintenance"

  Scenario: The same fault message received twice opens only one ticket
    Given a hub "Marés Centro" with these bikes:
      | code   | status    | battery |
      | EB-923 | available | 80      |
    When the simulator reports a "BRAKES" fault for bike "EB-923" with message id "f3c1a7e2-5b8d-4c9e-a1f0-2d6b8e4c7a91"
    And the simulator reports the same fault again with message id "f3c1a7e2-5b8d-4c9e-a1f0-2d6b8e4c7a91"
    Then bike "EB-923" has exactly one open ticket

  Scenario: Two different faults reported for the same bike open two tickets
    Given a hub "Marés Centro" with these bikes:
      | code   | status    | battery |
      | EB-924 | available | 80      |
    When the simulator reports a "BRAKES" fault for bike "EB-924" with message id "0b6d2f1e-7c4a-4e8b-9d3f-5a1c8e2b4d70"
    And the simulator reports a "LIGHTS" fault for bike "EB-924" with message id "9e4a7c2d-1f5b-4a6e-8c3d-2b7f9e1a5c48"
    Then bike "EB-924" has exactly two open tickets
