package com.honeybadger.flightlogistics.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static us.abstracta.jmeter.javadsl.JmeterDsl.httpSampler;
import static us.abstracta.jmeter.javadsl.JmeterDsl.jtlWriter;
import static us.abstracta.jmeter.javadsl.JmeterDsl.testPlan;
import static us.abstracta.jmeter.javadsl.JmeterDsl.threadGroup;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.time.Duration;
import org.apache.http.entity.ContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

public class JMeterSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private PerformanceScenarioContext context;

        @Given("the flight logistics API is running")
    public void theFlightLogisticsApiIsRunning() {
        context.setBaseUrl("http://localhost:" + port);
    }

    @When("{int} concurrent users each send {int} GET requests to {string}")
    public void concurrentUsersEachSendGetRequests(int threads, int iterations, String path) throws IOException {
        context.setStats(
                testPlan(
                        threadGroup(threads, iterations,
                                httpSampler(context.getBaseUrl() + path)
                        ),
                        jtlWriter("target/jtls/performance")
                ).run()
        );
    }

    @When("{int} concurrent users each POST {int} routes to {string}")
    public void concurrentUsersEachPostRoutes(int threads, int iterations, String path) throws IOException {
        String routeBody = "{\"name\":\"Load Test Route\",\"aircraftId\":\"a1b2c3d4-e5f6-7890-abcd-ef1234567890\"}";
        context.setStats(
                testPlan(
                        threadGroup(threads, iterations,
                                httpSampler(context.getBaseUrl() + path)
                                        .post(routeBody, ContentType.APPLICATION_JSON)
                        ),
                        jtlWriter("target/jtls/performance")
                ).run()
        );
    }

    @Then("all requests succeed")
    public void allRequestsSucceed() {
        assertThat(context.getStats().overall().errorsCount())
                .as("All requests should succeed with no errors")
                .isEqualTo(0);
    }

    @Then("the 99th percentile response time is below {int} milliseconds")
    public void the99thPercentileResponseTimeIsBelowMilliseconds(int millis) {
        assertThat(context.getStats().overall().sampleTimePercentile99())
                .as("99th percentile response time should be below %d ms", millis)
                .isLessThan(Duration.ofMillis(millis));
    }
}
