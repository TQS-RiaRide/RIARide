@RIA-53
Feature: Find available bikes
  As a guest, I want to see the bikes I can book at a hub,
  so that I can choose one for my trip.

  @BR12 @wip
  Scenario: Only available bikes with enough battery are shown as bookable
    Given a hub "Marés Praia" with these bikes:
      | code   | status    | battery |
      | EB-101 | available | 90      |
      | EB-102 | available | 55      |
      | EB-103 | available | 20      |
      | EB-104 | available | 15      |
    And a guest "Rita Almeida" with an active stay at "Marés Praia" is logged in
    When the guest lists the bikes at hub "Marés Praia"
    Then the response status is 200
    And the bookable bikes are "EB-101, EB-102, EB-103"
