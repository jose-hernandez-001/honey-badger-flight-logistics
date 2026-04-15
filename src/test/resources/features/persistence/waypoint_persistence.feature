Feature: Waypoint persistence

  Background:
    Given all repositories are empty
    And a saved route named "Test Route" with status "PLANNED"

  Scenario: Save and retrieve a waypoint
    When I save a waypoint named "Alpha-1" with sequence 1 at latitude 51.5074 longitude -0.1278 altitude 3000.0
    Then the waypoint can be retrieved by its ID
    And the waypoint name is "Alpha-1"

  Scenario: Retrieve waypoints ordered by sequence
    When I save a waypoint named "Alpha-3" with sequence 3 at latitude 51.5074 longitude -0.1278 altitude 3000.0
    And I save a waypoint named "Alpha-1" with sequence 1 at latitude 51.5074 longitude -0.1278 altitude 3000.0
    And I save a waypoint named "Alpha-2" with sequence 2 at latitude 51.5074 longitude -0.1278 altitude 3000.0
    And I retrieve the route waypoints ordered by sequence
    Then 3 waypoints are returned
    And the waypoint at position 1 is named "Alpha-1"
    And the waypoint at position 2 is named "Alpha-2"
    And the waypoint at position 3 is named "Alpha-3"

  Scenario: Delete a waypoint
    Given a saved waypoint named "Bravo-1" with sequence 1 at latitude 48.8566 longitude 2.3522 altitude 1500.0
    When I delete the waypoint
    Then the waypoint no longer exists
