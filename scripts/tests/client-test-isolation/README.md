# Client classpath isolation regression

Run from the repository root with a dedicated fixture directory. Its report path must be exactly `<fixtureDirectory>/report`:

```text
./gradlew -p scripts/tests/client-test-isolation --init-script <absolute-repository-path>/scripts/client-test-isolation.init.gradle verifyIsolation -PfixtureDirectory=<absolute-temp-path> -PhorizonQaClientTask=verifyIsolation -PhorizonQaClientReportDir=<absolute-temp-path>/report --configuration-cache
```

This standalone build overrides the JavaExec action and does not start a JVM or Minecraft. It verifies dependency execution before copying, classpath order, duplicate JAR names, configured subproject output directories, absent optional outputs, unchanged external dependencies and snapshot survival after the original outputs are overwritten or removed.

Run the exact same command twice. The first invocation must finish successfully and print `Configuration cache entry stored`. The second must print `Reusing configuration cache` and pass the assertions again. Task actions use captured values, task properties and injected services rather than accessing `Project` during execution.

Before each invocation the fixture resets only its own `report` directory and the optional output it created in the previous test. This allows identical command arguments on cache reuse. Use a dedicated fixture directory, never an actual client report directory. Production isolation still refuses to overwrite an existing classpath snapshot.

For a regression baseline, substitute the previous init script while keeping this fixture and `--configuration-cache`. The previous implementation fails with `invocation of 'gradle' references a Gradle script object from a Groovy closure at execution time`. Passing assertions without a successfully stored and reused cache is insufficient.
