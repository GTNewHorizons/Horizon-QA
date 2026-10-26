# Horizon-QA client tools

This archive contains the portable Gradle client supervisor and the schema-5 JSON/JUnit verifier used by Horizon-QA CI.
It requires a Gradle consumer project with Horizon-QA installed, JDK 17+ for supervision, and Python 3.9+ for verification.
Minecraft uses the Java version selected by the consumer's RFG task.

Unpack the versioned Maven artifact `com.github.GTNewHorizons:Horizon-QA:<version>:tools@zip` outside the consumer's source tree.
From the consumer directory, run:

```sh
./gradlew -p /path/to/unpacked/scripts/client-tests runClientTests \
    --project-root "$PWD" --client-task :runClient --tests mymod:ScreenTests
```

Use `gradlew.bat` on Windows and pass the consumer's absolute path to `--project-root`.
The supervisor retains isolated game directories, classpath snapshots, bounded process ownership and cancellation.
Each report directory must be new.
`--timeout-seconds` defaults to 600 and `--shutdown-seconds` to 20.

Python defaults to `python` on Windows and `python3` on other platforms.
Select a particular interpreter with `--python /path/to/python` or `-PhorizonQaPython=/path/to/python`.
No interpreter fallback is attempted.
Verifier output is retained in the run's `verification.log`.

To verify an already completed run:

```sh
python /path/to/unpacked/tools/verify-client-reports.py /path/to/reports \
    --tests mymod:ScreenTests --earliest "$launch_started_at"
```

`launch_started_at` must contain the Unix timestamp recorded before launch.
The verifier checks current schema-5 JSON and JUnit, selected IDs, counts, paths and freshness.
Optional failures remain exit 0, required failures exit 1, and invalid or incomplete evidence exits 2.
The supervisor also checks process completion and retains `launch-result.json` with the precise result code.
Gradle itself returns a generic failed task for either nonzero result.

The ZIP does not contain Minecraft, mods, caches, a consumer wrapper or another process launcher.
Install Horizon-QA and test classes through the consumer's ordinary runtime configuration.
