Feature: API Performance
  As an operator of Flight Logistics
  I want the REST API to respond quickly under concurrent load
  So that multiple clients can be served efficiently

  Background:
    Given the flight logistics API is running

  Scenario: List routes endpoint handles concurrent load
    When 5 concurrent users each send 10 GET requests to "/api/v1/routes"
    Then the 99th percentile response time is below 2000 milliseconds

  Scenario: Actuator health endpoint responds quickly under load
    When 10 concurrent users each send 20 GET requests to "/actuator/health"
    Then all requests succeed
    And the 99th percentile response time is below 2000 milliseconds

  Scenario: Create route endpoint handles concurrent writes
    When 3 & routes to "/api/v1/routes"
    Then the 99th percentile response time is below 3000 milliseconds
