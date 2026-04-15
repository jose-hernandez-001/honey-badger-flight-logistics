package com.honeybadger.flightlogistics.performance;

import io.cucumber.spring.ScenarioScope;
import org.springframework.stereotype.Component;
import us.abstracta.jmeter.javadsl.core.TestPlanStats;

@Component
@ScenarioScope
public class PerformanceScenarioContext {

    private String baseUrl;
    private TestPlanStats stats;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public TestPlanStats getStats() {
        return stats;
    }

    public void setStats(TestPlanStats stats) {
        this.stats = stats;
    }
}
