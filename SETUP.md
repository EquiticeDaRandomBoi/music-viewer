# Setup

## Gradle wrapper bootstrap

The `gradlew` / `gradlew.bat` scripts in each subproject require
`gradle/wrapper/gradle-wrapper.jar` to run. This binary is not committed
to the repository and must be generated once after cloning.

**Prerequisites:** Gradle 8.x installed and available on your PATH.

Run the following commands from the project root:

```sh
cd mc-1.21.11
gradle wrapper
cd ../mc-26.1.2
gradle wrapper
```

After this step, each subproject will have a fully self-contained Gradle
wrapper and you can use `./gradlew build` (Linux/Mac) or `gradlew.bat build`
(Windows) without needing a system Gradle installation.

The `gradle-wrapper.properties` files already reference Gradle 8.10 — the
`gradle wrapper` command will download that specific version automatically.
