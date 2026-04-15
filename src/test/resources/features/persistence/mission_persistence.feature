Feature: Route persistence

  Background:
    Given all repositories are empty

  Scenario: Save and retrieve a route
    When I save a route named "Operation Nighthawk" with status "PLANNED"
    Then the route can be retrieved by its ID
    And the route name is "Operation Nighthawk"
    And the route status is "PLANNED"

  Scenario: Find routes by status
    Given a saved route named "Alpha" with status "PLANNED"
    And a saved route named "Bravo" with status "ACTIVE"
    When I list routes with status "PLANNED"
    Then 1 route is returned in the results
    And the results contain a route named "Alpha"

  Scenario: Update a route status
    Given a saved route named "Charlie" with status "PLANNED"
    When I update the route status to "ACTIVE"
    Then the route status is "ACTIVE"

  Scenario: Delete a route
    Given a saved route named "Delta" with status "PLANNED"
    When I delete the route
    Then the route no longer exists
