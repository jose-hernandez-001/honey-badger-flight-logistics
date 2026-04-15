package com.honeybadger.flightlogistics.persistence;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.neo4j.Neo4jContainer;

@CucumberContextConfiguration
@SpringBootTest
@Import(CucumberSpringConfiguration.ContainerConfig.class)
public class CucumberSpringConfiguration {

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfig {

        @Bean
        @ServiceConnection
        @SuppressWarnings("resource")
        Neo4jContainer neo4jContainer() {
            return new Neo4jContainer("neo4j:5").withoutAuthentication();
        }
    }
}
