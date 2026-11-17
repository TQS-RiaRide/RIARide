@RIA-56
Feature: Register a hub
  As an operator, I want to register a hub with its bike capacity,
  so that bikes can be docked there and guests can start and end trips there.

  Endpoint: POST /api/hubs, called by a user with the operator role.
  Request body fields:
    name        text, required, not blank
    hotel       name of the hotel the hub belongs to, required
    latitude    decimal degrees, required
    longitude   decimal degrees, required
    capacity    whole number of bike slots, required, at least 1
  Success: 201 Created, a Location header "/api/hubs/{id}", and a body with
  id, name, hotel, latitude, longitude, capacity, dockedBikes (0) and freeSlots (= capacity).
  Invalid input: 400 Bad Request with an RFC 9457 problem detail
  (Content-Type application/problem+json) and no hub is created.

  Background:
    Given an operator is logged in

  @BR10 @wip
  Scenario: The operator registers a hub with capacity for 10 bikes
    When the operator registers a hub with:
      | name      | Marés Praia – Beach Front |
      | hotel     | Marés Praia               |
      | latitude  | 40.6405                   |
      | longitude | -8.7538                   |
      | capacity  | 10                        |
    Then the response status is 201
    And the response has a "Location" header pointing to the new hub
    And the response field "name" is "Marés Praia – Beach Front"
    And the response field "hotel" is "Marés Praia"
    And the response field "capacity" is "10"
    And the response field "dockedBikes" is "0"
    And the response field "freeSlots" is "10"
    And the fleet lists hub "Marés Praia – Beach Front" with 10 slots

  @BR10 @wip
  Scenario: The operator cannot register a hub with capacity 0
    When the operator registers a hub with:
      | name      | Marés Praia – Beach Front |
      | hotel     | Marés Praia               |
      | latitude  | 40.6405                   |
      | longitude | -8.7538                   |
      | capacity  | 0                         |
    Then the response is a problem detail with status 400
    And the problem detail reports an invalid field "capacity"
    And the fleet does not list hub "Marés Praia – Beach Front"
