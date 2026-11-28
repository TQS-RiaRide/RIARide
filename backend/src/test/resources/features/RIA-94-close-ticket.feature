@RIA-94 @wip
Feature: Close a ticket
  As a technician, I want to close a ticket after the repair,
  so that the bike returns to service.

  The scenarios have no step definitions yet (@wip, excluded from CI); RIA-94 adds them. A ticket
  is assigned to a technician by RIA-93. Opening a ticket for an available bike moves it to
  maintenance (RIA-92). Closing records when the ticket was resolved, from which the KPIs compute
  the time to repair (RIA-118).

  Background:
    Given a hub "Marés Centro" with these bikes:
      | code   | status    | battery |
      | EB-901 | available | 80      |
    And the technicians "Bruno Costa" and "Carla Dias" have accounts with the technician role
    And the operator has opened a ticket for bike "EB-901" with the fault "Chain keeps slipping"
    And the ticket for bike "EB-901" is assigned to technician "Bruno Costa"
    And bike "EB-901" is "maintenance"

  @TEST_RIA-185
  Scenario: The assigned technician closes the ticket and the bike is available again
    When technician "Bruno Costa" closes the ticket for bike "EB-901"
    Then the response status is 200
    And the ticket for bike "EB-901" is closed, with the time it was resolved
    And bike "EB-901" is "available"

  Scenario: A technician who is not assigned to the ticket cannot close it
    When technician "Carla Dias" closes the ticket for bike "EB-901"
    Then the response is a problem detail with status 403
    And the ticket for bike "EB-901" is open
    And bike "EB-901" is "maintenance"

  # The first close keeps its resolution time, which the time-to-repair KPI uses.
  Scenario: A closed ticket cannot be closed again
    Given technician "Bruno Costa" has closed the ticket for bike "EB-901"
    When technician "Bruno Costa" closes the ticket for bike "EB-901"
    Then the response is a problem detail with status 409
    And the ticket for bike "EB-901" keeps the time it was first resolved
    And bike "EB-901" is "available"
