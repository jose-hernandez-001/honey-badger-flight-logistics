package com.honeybadger.flightlogistics.persistence;

import com.honeybadger.flightlogistics.domain.RouteEntity;
import com.honeybadger.flightlogistics.domain.WaypointEntity;
import io.cucumber.spring.ScenarioScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@ScenarioScope
public class ScenarioContext {

    public UUID currentRouteId;
    public UUID currentWaypointId;
    public List<RouteEntity> routeResults = new ArrayList<>();
    public List<WaypointEntity> waypointResults = new ArrayList<>();
}
