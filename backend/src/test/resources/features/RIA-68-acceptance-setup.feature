@RIA-68
Feature: Acceptance test setup
  Checks that Cucumber reaches the running application through REST Assured.
  It is the only scenario without @wip until the I2 stories implement their steps.

  Scenario: The application reports that it is healthy
    When a client sends GET "/actuator/health"
    Then the response status is 200
    And the response field "status" is "UP"
