package com.riaride.acceptance;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

// Acceptance tests (BDD, QA Manual 5.3): runs the Gherkin features in
// src/test/resources/features against the REST API. The *IT name makes
// Maven Failsafe run it, separately from the Surefire unit tests.
// Scenarios tagged @wip have no step definitions yet and are excluded;
// each story removes the tag when it implements its steps.
// The JSON report is imported into Xray (QA Manual 2.3).
@Suite(failIfNoTests = false)
@IncludeEngines("cucumber")
@SelectPackages("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.riaride.acceptance")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @wip")
@ConfigurationParameter(
	key = PLUGIN_PROPERTY_NAME,
	value = "pretty,"
		+ "json:target/cucumber-reports/cucumber.json,"
		+ "html:target/cucumber-reports/cucumber.html")
class CucumberIT {
}
