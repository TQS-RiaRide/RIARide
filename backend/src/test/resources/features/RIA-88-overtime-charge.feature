@RIA-88
Feature: Overtime charge
  As a hotel manager, I want long trips to be charged as overtime,
  so that bikes return to the hubs.

  Background:
    Given a hub "Marés Centro" with an available bike "EB-301" with 80 % battery
    And a guest "Rita Almeida" with an active stay at "Marés Centro" is logged in
    And the guest has a valid "stay" pass
    And the guest's pass was bought with a card saved for off-session charges
    And the guest has a trip in progress with bike "EB-301"
    And a hub "Marés Praia" with capacity 10 and 9 bikes docked

  @BR7
  Scenario: A trip of 3 h 20 min is charged 2 blocks of overtime
    Given the payment sandbox accepts off-session charges
    And the trip has lasted 3 h 20 min
    When the guest ends the trip at hub "Marés Praia"
    Then the response status is 200
    And the guest is charged 2 blocks of 15 minutes of overtime, 4.00 EUR, off-session

  # Charging happens after the trip ends, so a payment outage never blocks the guest (architecture, section 7).
  @BR7
  Scenario: The trip ends while the payment sandbox is down, and the overtime is charged later
    Given the payment sandbox is down
    And the trip has lasted 3 h 20 min
    When the guest ends the trip at hub "Marés Praia"
    Then the response status is 200
    And the trip is "ended"
    And no overtime has been charged yet
    When the payment sandbox is back and the overtime charge is retried
    Then the guest is charged 2 blocks of 15 minutes of overtime, 4.00 EUR, off-session
