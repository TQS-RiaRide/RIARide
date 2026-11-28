package com.riaride;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): the Flyway migrations follow the naming rule of the architecture document
// (section 6, Migration rules), V<n>__<module>_<change>.sql, and no version is used twice. Flyway
// would refuse a duplicate only when the backend starts; this fails the build at once, before the
// integration tests. That every migration applies on PostgreSQL is checked by the integration
// tests, which all start from them.
class MigrationFilesTest {

  private static final Pattern NAME = Pattern.compile("V(\\d+)__([a-z][a-z0-9_]*)\\.sql");

  // The application modules (package-info.java in each), and the tables of libraries.
  private static final Set<String> OWNERS =
      Set.of(
          "accounts",
          "backoffice",
          "fleet",
          "maintenance",
          "passes",
          "payments",
          "telemetry",
          "trips",
          "event_publication");

  private static List<Path> migrations;

  @BeforeAll
  static void readMigrations() throws IOException, URISyntaxException {
    var folder =
        Path.of(
            Objects.requireNonNull(
                    MigrationFilesTest.class.getClassLoader().getResource("db/migration"),
                    "db/migration is not on the classpath")
                .toURI());
    try (Stream<Path> files = Files.list(folder)) {
      migrations = files.filter(Files::isRegularFile).sorted().toList();
    }
  }

  private static String name(Path migration) {
    return migration.getFileName().toString();
  }

  @Requirement("RIA-101")
  @Test
  void shouldNameEveryMigrationByItsVersionModuleAndChange() {
    assertThat(migrations).isNotEmpty();
    assertThat(migrations)
        .allSatisfy(migration -> {
          var matcher = NAME.matcher(name(migration));
          assertThat(matcher.matches())
              .as("%s is named V<n>__<module>_<change>.sql", name(migration))
              .isTrue();
          var description = matcher.group(2);
          assertThat(OWNERS)
              .as("%s starts with the module that owns the table", name(migration))
              .anySatisfy(owner ->
                  assertThat(description.equals(owner) || description.startsWith(owner + "_"))
                      .isTrue());
        });
  }

  // Two pull requests that pick the same number would otherwise meet only at startup.
  @Requirement("RIA-101")
  @Test
  void shouldUseEachVersionOnce() {
    var byVersion =
        migrations.stream()
            .map(MigrationFilesTest::name)
            .filter(name -> NAME.matcher(name).matches())
            .collect(
                Collectors.groupingBy(
                    name -> {
                      var matcher = NAME.matcher(name);
                      matcher.matches();
                      return Integer.parseInt(matcher.group(1));
                    },
                    Collectors.mapping(Function.identity(), Collectors.toList())));

    assertThat(byVersion.values())
        .as("migrations sharing a version")
        .allSatisfy(names -> assertThat(names).hasSize(1));
  }

  @Requirement("RIA-101")
  @Test
  void shouldHaveNoEmptyMigration() throws IOException {
    for (var migration : migrations) {
      assertThat(Files.readString(migration).strip())
          .as("%s has statements", name(migration))
          .isNotEmpty();
    }
  }
}
