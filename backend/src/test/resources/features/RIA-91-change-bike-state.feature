@RIA-91
Feature: Change a bike's state
  As an operator, I want to activate, deactivate or retire bikes,
  so that I control which ones can be used.

  Background:
    Given an operator is logged in
    And a hub "Marés Centro" with these bikes:
      | code   | status    | battery |
      | EB-701 | available | 80      |
      | EB-702 | available | 80      |
      | EB-703 | in use    | 60      |

  Scenario: The operator retires an available bike and guests no longer see it
    When the operator retires bike "EB-701"
    Then the response status is 200
    And bike "EB-701" is "retired"
    Given a guest "Rita Almeida" with an active stay at "Marés Centro" is logged in
    When the guest lists the bikes at hub "Marés Centro"
    Then bike "EB-701" is not listed
    And bike "EB-702" is listed

  Scenario: The operator cannot retire a bike in use
    When the operator retires bike "EB-703"
    Then the response is a problem detail with status 409
    And bike "EB-703" is "in use"
