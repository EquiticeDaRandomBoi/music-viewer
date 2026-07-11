## Task 1 Report: Gradle Multi-Project Scaffold

**Status:** DONE_WITH_CONCERNS

---

### What was created

| File | Notes |
|---|---|
| `settings.gradle` | Root multi-project settings; includes `mc-1.21.11` and `mc-26.1.2` |
| `mc-1.21.11/build.gradle` | Fabric Loom build; yarn mappings; archivesName=musicplayer-1.21.11 |
| `mc-1.21.11/gradle.properties` | Verified versions from maven.fabricmc.net (July 2026) |
| `mc-1.21.11/gradle/wrapper/gradle-wrapper.properties` | Gradle 8.10 bin distribution |
| `mc-1.21.11/gradlew` | Wrapper stub (delegates to system `gradle`) |
| `mc-1.21.11/gradlew.bat` | Windows wrapper stub |
| `mc-26.1.2/build.gradle` | Fabric Loom build; **official Mojang mappings** (see concern 1) |
| `mc-26.1.2/gradle.properties` | Verified versions; no yarn_mappings property |
| `mc-26.1.2/gradle/wrapper/gradle-wrapper.properties` | Gradle 8.10 bin distribution |
| `mc-26.1.2/gradlew` | Wrapper stub |
| `mc-26.1.2/gradlew.bat` | Windows wrapper stub |
| `common-src/.gitkeep` | Scaffold for shared Java sources |
| `common-resources/scripts/.gitkeep` | Scaffold for shared resources |

---

### Version values used (verified via maven.fabricmc.net)

**mc-1.21.11:**
```
minecraft_version=1.21.11
yarn_mappings=1.21.11+build.6   # latest build available
loader_version=0.19.3            # current latest loader
fabric_version=0.141.4+1.21.11  # latest fabric-api for 1.21.11
```

**mc-26.1.2:**
```
minecraft_version=26.1.2
loader_version=0.19.3           # current latest loader
fabric_version=0.154.2+26.1.2  # latest fabric-api for 26.1.2
# No yarn_mappings — uses loom.officialMojangMappings()
```

---

### Concerns

**1. mc-26.1.2 uses official Mojang mappings, not yarn (DEVIATION FROM BRIEF)**
Yarn mappings do not exist for `26.1.2` in the Fabric maven repository. The latest yarn version available is `1.21.11+build.6`. The `mc-26.1.2/build.gradle` therefore uses `loom.officialMojangMappings()` instead of `mappings "net.fabricmc:yarn:..."`, and `gradle.properties` does not contain a `yarn_mappings` property. This matches what the official `FabricMC/fabric-example-mod` `26.1.2` branch uses.

**2. fabric-loom version may be stale**
Both `build.gradle` files use `id 'fabric-loom' version '1.9-SNAPSHOT'` as specified in the brief. However, the official `FabricMC/fabric-example-mod` repository (both `1.21.1` and `26.1.2` branches, updated July 2026) uses `loom_version=1.17-SNAPSHOT`. If dependency resolution fails with `1.9-SNAPSHOT`, update the plugin version in both `build.gradle` files to `1.9-SNAPSHOT` → `1.17-SNAPSHOT`.

**3. Wrapper stubs are not full Gradle wrappers**
The `gradlew` / `gradlew.bat` scripts in each subproject are minimal stubs that call the system `gradle` binary. The brief notes that the user should run `gradle wrapper` (or `gradlew wrapper`) in each subproject directory to generate the full Gradle wrapper artifacts (`gradle-wrapper.jar`, complete wrapper shell script). The `gradle-wrapper.properties` files are already in place with the Gradle 8.10 distribution URL.

**4. Dependency resolution not tested**
Cannot run `gradlew dependencies` without internet access and a Gradle installation. All version values were verified against live maven.fabricmc.net metadata rather than via an actual Gradle build.

---

### srcDirs wiring

Both subprojects wire `common-src/` and `common-resources/` via:
```groovy
sourceSets {
    main {
        java      { srcDir "${rootDir}/common-src" }
        resources { srcDir "${rootDir}/common-resources" }
    }
}
```
`rootDir` resolves to `F:\musicplayer\` in both cases because Gradle discovers the root `settings.gradle` when invoked from any subproject directory (no local `settings.gradle` exists inside the subprojects). This is correct.

## Fix Pass
- Updated fabric-loom to 1.17-SNAPSHOT in both build.gradle files
- Replaced stub gradlew/gradlew.bat with standard Gradle 8.x wrapper bootstrap scripts (gradle-wrapper.jar still needs `gradle wrapper` to be run — documented in SETUP.md)
- Replaced common-resources/scripts/.gitkeep with common-resources/.gitkeep
