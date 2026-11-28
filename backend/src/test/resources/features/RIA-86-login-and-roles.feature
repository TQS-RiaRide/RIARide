@RIA-86
Feature: Log in and see only the features of my role
  As a user, I want to log in and see only the features of my role,
  so that I only access what I need.

  Users log in through Keycloak (ADR 0009). After login the web app calls GET /api/v1/me,
  which returns the user and the workspaces their roles may open, in menu order:
    guest          GUEST_DASHBOARD
    operator       OPERATOR_DASHBOARD, MAINTENANCE_TICKETS
    technician     MAINTENANCE_TICKETS
    hotel manager  HOTEL_BACKOFFICE
    admin          OPERATOR_DASHBOARD, MAINTENANCE_TICKETS, HOTEL_BACKOFFICE, ACCOUNT_ADMINISTRATION
  The menu is only what the UI offers: every endpoint still checks the role, and a role that
  may not call an endpoint gets 403 Forbidden as a problem detail. A request without a valid
  token gets 401 Unauthorized.

  @TEST_RIA-178
  Scenario: An operator sees the operator dashboard and not the hotel backoffice
    Given a user "Rui Almeida" with the "OPERATOR" role is logged in
    When the user opens their account
    Then the response status is 200
    And the user's workspaces are "OPERATOR_DASHBOARD, MAINTENANCE_TICKETS"
    And the user does not see the "HOTEL_BACKOFFICE" workspace

  Scenario Outline: Each role sees only the workspaces it needs
    Given a user "<name>" with the "<role>" role is logged in
    When the user opens their account
    Then the response status is 200
    And the user's workspaces are "<workspaces>"

    Examples:
      | name           | role          | workspaces                                                                      |
      | Sophie Martin  | GUEST         | GUEST_DASHBOARD                                                                 |
      | Rui Almeida    | OPERATOR      | OPERATOR_DASHBOARD, MAINTENANCE_TICKETS                                         |
      | Nuno Teixeira  | TECHNICIAN    | MAINTENANCE_TICKETS                                                             |
      | Ana Ribeiro    | HOTEL_MANAGER | HOTEL_BACKOFFICE                                                                |
      | Clara Sousa    | ADMIN         | OPERATOR_DASHBOARD, MAINTENANCE_TICKETS, HOTEL_BACKOFFICE, ACCOUNT_ADMINISTRATION |

  Scenario: An operator cannot use the hotel backoffice through the API
    Given a user "Rui Almeida" with the "OPERATOR" role is logged in
    When the user sends POST "/api/v1/stays" with an empty body
    Then the response is a problem detail with status 403

  Scenario: A user who has not logged in cannot open an account
    When a client sends GET "/api/v1/me"
    Then the response is a problem detail with status 401
