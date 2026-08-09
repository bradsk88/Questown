# Questown
https://modrinth.com/mod/questown/

### Environment Variables
- ENABLE_DEV_COMMANDS : Set to "true" to enable developer commands under the `/_qt` namespace.
- INVISIBLE_LOG_LEVEL : Set to "trace" to reduce log noise during development.

### Automated Testing
Run all 12 job tests on a headless server:
```
JAVA_HOME=/home/retro/.gradle/jdks/jdk-17.0.20+8 ./gradlew runServer -Dquestown.autotest=true -Dquestown.autotest.warp=24000
```
- Requires JDK 17 (Gradle auto-provisions it under `~/.gradle/jdks/` on first build — if the
  pinned path is gone, substitute any JDK 17 that exists on your machine).
- Exit code 0 = all pass, 1 = failures.
- Results in `run/logs/latest.log` — grep for `[autotest]`.

### Dev environment pitfalls (learned 2026-08-09)

- **"Invalid patcher dependency: net.minecraftforge:forge:...:userdev"** — this almost
  certainly means ForgeGradle cached a corrupt userdev jar. The known cause: the
  `modmaven.dev` repository (a former JEI mirror) now serves an HTML redirect page
  **with HTTP 200** for artifacts it doesn't have, and ForgeGradle's downloader caches
  that page as the jar. The repo has been removed from `build.gradle`; if you see this
  error, delete the poisoned artifact and rebuild:
  `rm -rf ~/.gradle/caches/forge_gradle/maven_downloader/net/minecraftforge/forge/<version>/`
- **"Unsupported class file major version 65"** — you ran Gradle 7.2 on Java 21. Questown
  needs an older JDK to *run Gradle* (e.g. `JAVA_HOME=~/.jdks/corretto-16.0.2 ./gradlew runClient`);
  RoomRecipes instead pins `org.gradle.java.home` in its `gradle.properties` to a JDK 17.
- **`Could not find ca.bradj:RoomRecipes:1.19.2-<version>`** — RoomRecipes is resolved from
  `mavenLocal()`, not a remote repo. Publish it from a sibling checkout:
  `cd ../RoomRecipes && git checkout 1.19.2-<version> && ./gradlew publishToMavenLocal`
  (the published version is the git branch name). Its `gradle.properties` may pin
  `org.gradle.java.home` to a since-deleted auto-provisioned JDK — point it at any local JDK 17.

### Release Preparation
Before a new version can be considered beta-ready, you must test
- Starting from a brand new save
- Adding one of each villager
- Sleeping through the night
- Completing a quest batch
- Sleeping through the night again
- Closing and reopening the save

# Forge Docs

Source installation information for modders
-------------------------------------------
This code follows the Minecraft Forge installation methodology. It will apply
some small patches to the vanilla MCP source code, giving you and it access 
to some of the data and functions you need to build a successful mod.

Note also that the patches are built against "un-renamed" MCP source code (aka
SRG Names) - this means that you will not be able to read them directly against
normal code.

Setup Process:
==============================

Step 1: Open your command-line and browse to the folder where you extracted the zip file.

Step 2: You're left with a choice.
If you prefer to use Eclipse:
1. Run the following command: `gradlew genEclipseRuns` (`./gradlew genEclipseRuns` if you are on Mac/Linux)
2. Open Eclipse, Import > Existing Gradle Project > Select Folder 
   or run `gradlew eclipse` to generate the project.

If you prefer to use IntelliJ:
1. Open IDEA, and import project.
2. Select your build.gradle file and have it import.
3. Run the following command: `gradlew genIntellijRuns` (`./gradlew genIntellijRuns` if you are on Mac/Linux)
4. Refresh the Gradle Project in IDEA if required.

If at any point you are missing libraries in your IDE, or you've run into problems you can 
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
{this does not affect your code} and then start the process again.

Mapping Names:
=============================
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license, if you do not agree with it you can change your mapping names to other crowdsourced names in your 
build.gradle. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/MinecraftForge/MCPConfig/blob/master/Mojang.md

Additional Resources: 
=========================
Community Documentation: http://mcforge.readthedocs.io/en/latest/gettingstarted/  
LexManos' Install Video: https://www.youtube.com/watch?v=8VEdtQLuLO0  
Forge Forum: https://forums.minecraftforge.net/  
Forge Discord: https://discord.gg/UvedJ9m  