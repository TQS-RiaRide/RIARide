@RIA-55
Feature: End a trip at a hub
  As a guest, I want to end my trip at any hub with a free slot,
  so that I can return the bike where it suits me.

  Background:
    Given a guest "Rita Almeida" with an active stay at "Marés Centro" is logged in
    And the guest has a trip in progress with bike "EB-301"

  @BR10 @wip
  Scenario: The guest ends the trip at a hub with a free slot
    Given a hub "Marés Praia" with capacity 10 and 9 bikes docked
    When the guest ends the trip at hub "Marés Praia"
    Then the response status is 200
    And the trip is "ended"
    And bike "EB-301" is docked at hub "Marés Praia"

  @BR10 @wip
  Scenario: The guest cannot end the trip at a full hub and nearby hubs are suggested
    Given a hub "Marés Praia" with capacity 10 and 10 bikes docked
    And a hub "Marés Salinas" with capacity 8 and 3 bikes docked
    And a hub "Marés Centro" with capacity 12 and 6 bikes docked
    When the guest ends the trip at hub "Marés Praia"
    Then the response is a problem detail with status 409
    And the trip is "in progress"
    And the problem detail suggests the nearest hubs with free slots, nearest first
