@RIA-92
Feature: Open a maintenance ticket
  As an operator, I want to open a ticket for a broken bike,
  so that it is repaired within the deadline.

  Scenario: The operator opens a ticket for an available bike with a fault
    Given a hub "Marés Centro" with these bikes:
      | code   | status    | battery |
      | EB-801 | available | 80      |
      | EB-802 | available | 80      |
    When the operator opens a ticket for bike "EB-801" with the fault "Rear brake does not work"
    Then the response status is 201
    And bike "EB-801" is "maintenance"
    And the ticket for bike "EB-801" is open and due 24 hours after it was opened
    Given a guest "Rita Almeida" with an active stay at "Marés Centro" is logged in
    When the guest lists the bikes at hub "Marés Centro"
    Then bike "EB-801" is not bookable

  Scenario: A bike in use moves to maintenance only when its trip ends
    Given a hub "Marés Centro" with an available bike "EB-301" with 80 % battery
    And a guest "Rita Almeida" with an active stay at "Marés Centro" is logged in
    And the guest has a valid "stay" pass
    And the guest has a trip in progress with bike "EB-301"
    When the operator opens a ticket for bike "EB-301" with the fault "Guest reported a strange noise"
    Then the response status is 201
    And bike "EB-301" is "in use"
    And the trip is "in progress"
    Given a hub "Marés Praia" with capacity 10 and 9 bikes docked
    When the guest ends the trip at hub "Marés Praia"
    Then the response status is 200
    And bike "EB-301" is "maintenance"

  Scenario: The operator cannot open a ticket for a retired bike
    Given a hub "Marés Centro" with these bikes:
      | code   | status  | battery |
      | EB-803 | retired | 80      |
    When the operator opens a ticket for bike "EB-803" with the fault "Flat tyre"
    Then the response is a problem detail with status 409
    And bike "EB-803" is "retired"
    And bike "EB-803" has no ticket
