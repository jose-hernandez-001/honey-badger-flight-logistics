package com.honeybadger.flightlogistics.persistence;

import com.honeybadger.flightlogistics.domain.RouteEntity;
import com.honeybadger.flightlogistics.domain.RouteStatus;
import com.honeybadger.flightlogistics.repository.RouteRepository;
import com.honeybadger.flightlogistics.repository.WaypointRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class RoutePersistenceSteps {

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private WaypointRepository waypointRepository;

    @Autowired
    private ScenarioContext ctx;

    @Given("all repositories are empty")
    public void allRepositoriesAreEmpty() {
        waypointRepository.deleteAll();
        routeRepository.deleteAll();
    }

    @Given("a saved route named {string} with status {string}")
    public void aSavedRoute(String name, String status) {
        RouteEntity route = buildRoute(name, RouteStatus.valueOf(status));
        routeRepository.save(route);
        ctx.currentRouteId = route.getId();
    }

    @When("I save a route named {string} with status {string}")
    public void iSaveARoute(String name, String status) {
        RouteEntity route = buildRoute(name, RouteStatus.valueOf(status));
        routeRepository.save(route);
        ctx.currentRouteId = route.getId();
    }

    @Then("the route can be retrieved by its ID")
    public void routeCanBeRetrieved() {
        assertThat(routeRepository.findById(ctx.currentRouteId)).isPresent();
    }

    @Then("the route name is {string}")
    public void routeNameIs(String expectedName) {
        RouteEntity route = routeRepository.findById(ctx.currentRouteId).orElseThrow();
        assertThat(route.getName()).isEqualTo(expectedName);
    }

    @Then("the route status is {string}")
    public void routeStatusIs(String expectedStatus) {
        RouteEntity route = routeRepository.findById(ctx.currentRouteId).orElseThrow();
        assertThat(route.getStatus()).isEqualTo(RouteStatus.valueOf(expectedStatus));
    }

    @When("I list routes with status {string}")
    public void iListRoutesWithStatus(String status) {
        ctx.routeResults = routeRepository
                .findByStatus(RouteStatus.valueOf(status), PageRequest.of(0, 20))
                .getContent();
    }

    @Then("{int} route is returned in the results")
    public void nRoutesReturnedInResults(int expectedCount) {
        assertThat(ctx.routeResults).hasSize(expectedCount);
    }

    @Then("the results contain a route named {string}")
    public void resultContainsRouteNamed(String name) {
        assertThat(ctx.routeResults)
                .extracting(RouteEntity::getName)
                .contains(name);
    }

    @When("I update the route status to {string}")
    public void iUpdateRouteStatus(String newStatus) {
        RouteEntity route = routeRepository.findById(ctx.currentRouteId).orElseThrow();
        route.setStatus(RouteStatus.valueOf(newStatus));
        route.setUpdatedAt(OffsetDateTime.now());
        routeRepository.save(route);
    }

    @When("I delete the route")
    public void iDeleteTheRoute() {
        routeRepository.deleteById(ctx.currentRouteId);
    }

    @Then("the route no longer exists")
    public void routeNoLongerExists() {
        assertThat(routeRepository.findById(ctx.currentRouteId)).isEmpty();
    }

    private RouteEntity buildRoute(String name, RouteStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        return RouteEntity.builder()
                .id(UUID.randomUUID())
                .name(name)
                .status(status)
                .aircraftId(UUID.randomUUID())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
