// Root is a pure Gradle multi-module aggregator: it owns no source and applies no plugins.
// Each feature module (reservation, operation) is an independently runnable Spring Boot
// application with its own bootJar/bootRun; see reservation/build.gradle.kts and
// operation/build.gradle.kts.

group = "yeo.chi.proejct"
version = "0.0.1-SNAPSHOT"
