/*
 * The MIT License
 * Copyright © 2026 José Hernández
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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

    @When("{int} & routes to {string}")
    public void routes_to(Integer threads, String path) throws IOException {
        // For this step, assume each thread sends 10 POST requests (as an example)
        int iterations = 10;
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
