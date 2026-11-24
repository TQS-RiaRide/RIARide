@RIA-59
Feature: Block bikes with low battery
  As an operator, I want bikes with low battery to be blocked from booking,
  so that guests do not start trips that cannot be completed.

  @TEST_RIA-156 @BR12
  Scenario: A bike whose battery drops to 18 % can no longer be booked
    Given a hub "Marés Centro" with an available bike "EB-601" with 80 % battery
    And a guest "Rita Almeida" with an active stay at "Marés Centro" is logged in
    And the guest has a valid "stay" pass
    When telemetry reports that bike "EB-601" has 18 % battery
    And the guest lists the bikes at hub "Marés Centro"
    Then bike "EB-601" is not bookable
