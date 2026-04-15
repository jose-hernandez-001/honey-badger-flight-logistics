package com.honeybadger.flightlogistics.persistence;

import com.honeybadger.flightlogistics.domain.WaypointEntity;
import com.honeybadger.flightlogistics.repository.WaypointRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class WaypointPersistenceSteps {

    @Autowired
    private WaypointRepository waypointRepository;

    @Autowired
    private ScenarioContext ctx;

    @Given("a saved waypoint named {string} with sequence {int} at latitude {double} longitude {double} altitude {double}")
    public void aSavedWaypoint(String name, int sequence, double latitude, double longitude, double altitude) {
        WaypointEntity waypoint = buildWaypoint(name, sequence, latitude, longitude, altitude);
        waypointRepository.save(waypoint);
        ctx.currentWaypointId = waypoint.getId();
    }

    @When("I save a waypoint named {string} with sequence {int} at latitude {double} longitude {double} altitude {double}")
    public void iSaveAWaypoint(String name, int sequence, double latitude, double longitude, double altitude) {
        WaypointEntity waypoint = buildWaypoint(name, sequence, latitude, longitude, altitude);
        waypointRepository.save(waypoint);
        ctx.currentWaypointId = waypoint.getId();
    }

    @Then("the waypoint can be retrieved by its ID")
    public void waypointCanBeRetrieved() {
        assertThat(waypointRepository.findById(ctx.currentWaypointId)).isPresent();
    }

    @Then("the waypoint name is {string}")
    public void waypointNameIs(String expectedName) {
        WaypointEntity waypoint = waypointRepository.findById(ctx.currentWaypointId).orElseThrow();
        assertThat(waypoint.getName()).isEqualTo(expectedName);
    }

    @When("I retrieve the route waypoints ordered by sequence")
    public void iRetrieveWaypointsOrderedBySequence() {
        ctx.waypointResults = waypointRepository.findByRouteIdOrderBySequenceAsc(ctx.currentRouteId);
    }

    @Then("{int} waypoints are returned")
    public void nWaypointsReturned(int expectedCount) {
        assertThat(ctx.waypointResults).hasSize(expectedCount);
    }

    @Then("the waypoint at position {int} is named {string}")
    public void waypointAtPositionNamed(int position, String expectedName) {
        assertThat(ctx.waypointResults.get(position - 1).getName()).isEqualTo(expectedName);
    }

    @When("I delete the waypoint")
    public void iDeleteTheWaypoint() {
        waypointRepository.deleteById(ctx.currentWaypointId);
    }

    @Then("the waypoint no longer exists")
    public void waypointNoLongerExists() {
        assertThat(waypointRepository.findById(ctx.currentWaypointId)).isEmpty();
    }

    private WaypointEntity buildWaypoint(String name, int sequence, double latitude, double longitude, double altitude) {
        OffsetDateTime now = OffsetDateTime.now();
        return WaypointEntity.builder()
                .id(UUID.randomUUID())
                .routeId(ctx.currentRouteId)
                .name(name)
                .sequence(sequence)
                .latitude(latitude)
                .longitude(longitude)
                .altitude(altitude)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
