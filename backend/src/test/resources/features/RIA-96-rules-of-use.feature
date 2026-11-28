@RIA-96
Feature: Set the rules of use
  As a hotel manager, I want to set the maximum trip duration and the hub capacity,
  so that I control the service.

  Background:
    Given a hub "Marés Centro" with an available bike "EB-301" with 80 % battery
    And a guest "Rita Almeida" with an active stay at "Marés Centro" is logged in
    And the guest has a valid "stay" pass
    And the guest's pass was bought with a card saved for off-session charges

  @BR7
  Scenario: A trip over a maximum duration changed to 2 hours is charged overtime
    Given the hotel manager of "Marés Centro" has set the maximum trip duration to 2 hours
    And the payment sandbox accepts off-session charges
    And the guest has a trip in progress with bike "EB-301"
    And a hub "Marés Praia" with capacity 10 and 9 bikes docked
    And the trip has lasted 2 h 20 min
    When the guest ends the trip at hub "Marés Praia"
    Then the response status is 200
    And the guest is charged 2 blocks of 15 minutes of overtime, 4.00 EUR, off-session

  # A change applies to trips that start after it (architecture, section 7).
  @BR7
  Scenario: A trip in progress keeps the maximum duration it started with
    Given the payment sandbox accepts off-session charges
    And the guest has a trip in progress with bike "EB-301"
    And a hub "Marés Praia" with capacity 10 and 9 bikes docked
    And 10 more minutes pass
    And the hotel manager of "Marés Centro" has set the maximum trip duration to 2 hours
    And 130 more minutes pass
    When the guest ends the trip at hub "Marés Praia"
    Then the response status is 200
    And the guest is not charged overtime

  @BR10
  Scenario: A guest cannot end a trip at a hub whose capacity was changed to its docked bikes
    Given a hub "Marés Praia" with capacity 10 and 8 bikes docked
    And the guest has a trip in progress with bike "EB-301"
    And the hotel manager of "Marés Praia" has changed the capacity of hub "Marés Praia" to 8
    When the guest ends the trip at hub "Marés Praia"
    Then the response is a problem detail with status 409
    And the trip is "in progress"
