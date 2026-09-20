import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    alias(libs.plugins.errorprone)
    alias(libs.plugins.extra.java.module.info)
    alias(libs.plugins.spotless)
}

group = "io.github.xoifaii"
version = "2.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

repositories {
    mavenCentral()
}

// re2j 1.8 ships neither a module descriptor nor an Automatic-Module-Name, so Gradle would leave it on
// the classpath where a module cannot see it. This names it as the automatic module "re2j" instead.
extraJavaModuleInfo {
    failOnMissingModuleInfo = false
    automaticModule("com.google.re2j:re2j", "re2j")
}

dependencies {
    api(libs.jspecify)
    implementation(libs.re2j)
    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 26
    // Requiring an automatic module is a lint warning, and re2j is one. It stays internal, never in a
    // public signature, so nothing else is relaxed.
    options.compilerArgs.addAll(listOf("-Xlint:all,-requires-automatic", "-Werror"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
        error(
            "NullAway",
            "CatchAndPrintStackTrace",
            "EmptyCatch",
            "JavaTimeDefaultTimeZone",
            "JavaUtilDate",
            "MissingCasesInEnumSwitch",
            "NonFinalStaticField",
            "NullableOptional",
            "ReferenceEquality",
            "SystemOut",
            "ThreadLocalUsage",
            "UnnecessaryDefaultInEnumSwitch",
            "UnusedVariable",
            "WildcardImport",
        )
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

spotless {
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    java {
        palantirJavaFormat(libs.versions.palantir.get())
        formatAnnotations()
        removeUnusedImports()
    }
}
