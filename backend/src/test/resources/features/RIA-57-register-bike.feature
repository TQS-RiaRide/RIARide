@RIA-57
Feature: Register a bike
  As an operator, I want to register a bike at a hub,
  so that guests can book it.

  Background:
    Given an operator is logged in

  @BR10 @wip
  Scenario: The operator registers a bike at a hub with free slots
    Given a hub "Marés Salinas" with capacity 8 and 3 bikes docked
    When the operator registers bike "EB-401" at hub "Marés Salinas"
    Then the response status is 201
    And bike "EB-401" is "available"
    And bike "EB-401" is docked at hub "Marés Salinas"

  @BR10 @wip
  Scenario: The operator cannot register a bike at a full hub
    Given a hub "Marés Salinas" with capacity 8 and 8 bikes docked
    When the operator registers bike "EB-401" at hub "Marés Salinas"
    Then the response is a problem detail with status 409
    And bike "EB-401" does not exist
