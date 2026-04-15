package com.honeybadger.flightlogistics.persistence;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectDirectories;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectDirectories("src/test/resources/features/persistence")
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = "com.honeybadger.flightlogistics.persistence")
@ConfigurationParameter(key = Constants.PLUGIN_PROPERTY_NAME, value = "pretty")
public class PersistenceSuiteIT {
}
