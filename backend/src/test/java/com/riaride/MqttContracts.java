package com.riaride;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.util.List;

// The MQTT message contracts shared with the simulator (contracts/mqtt, RIA-60), copied onto the
// test classpath by the pom. Formats such as uuid and date-time are asserted, not only annotated.
public final class MqttContracts {

  private static final String COMMAND_SCHEMA = "/contracts/mqtt/command.schema.json";

  private MqttContracts() {}

  // The reasons a command payload breaks command.schema.json; empty when it follows it.
  public static List<String> commandViolations(String payload) {
    return load(COMMAND_SCHEMA).validate(payload, InputFormat.JSON).stream()
        .map(ValidationMessage::getMessage)
        .toList();
  }

  private static JsonSchema load(String resource) {
    try (var schema = MqttContracts.class.getResourceAsStream(resource)) {
      if (schema == null) {
        throw new IllegalStateException(
            resource
                + " is not on the test classpath: it comes from "
                + "contracts/mqtt in the repository (RIA-60)");
      }
      var config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
      return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
          .getSchema(schema, config);
    } catch (IOException unreadable) {
      throw new IllegalStateException("Could not read " + resource, unreadable);
    }
  }
}
