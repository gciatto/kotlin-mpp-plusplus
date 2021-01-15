import java.time.Duration
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    jacoco
    `java-gradle-plugin`
    kotlin("jvm")
    `maven-publish`
    signing
    id("com.gradle.plugin-publish")
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.dokka")
    id("org.jlleitschuh.gradle.ktlint")
    id("org.danilopianini.git-sensitive-semantic-versioning")
    id("org.danilopianini.publish-on-central")
    id("de.marcphilipp.nexus-publish")
}

/*
 * Project information
 */
group = "io.github.gciatto"
description = "A Gradle plugin easing the configuration Kotlin Multiplaftorm projects"
inner class PluginInfo {
    val longName = "Set up Kotlin MPP projects via Gradle"
    val website = "https://github.com/gciatto/kt-mpp-pp"
    val scm = "git@github.com:gciatto/kt-mpp-pp.git"
    val pluginImplementationClass = "$group.kt.mpp.KtMppPlusPlusPlugin"
    val tags = listOf("kotlin", "multi plaftorm", "mpp", "gradle")
    val license = "Apache License, Version 2.0"
    val licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0"
}
val info = PluginInfo()

gitSemVer {
    version = computeGitSemVer()
}

println("$group:$name v$version")

repositories {
    mavenCentral()
    jcenter()
    maven("https://plugins.gradle.org/m2/")
    mapOf(
        "kotlin/dokka" to setOf("org.jetbrains.dokka"),
        "kotlin/kotlinx.html" to setOf("org.jetbrains.kotlinx"),
        "arturbosch/code-analysis" to setOf("io.gitlab.arturbosch.detekt")
    ).forEach { (uriPart, groups) ->
        maven {
            url = uri("https://dl.bintray.com/$uriPart")
            content { groups.forEach { includeGroup(it) } }
        }
    }
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:_")
    api(gradleApi())
    api(gradleKotlinDsl())
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:_")
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jlleitschuh.gradle:ktlint-gradle:_")
    implementation("com.jfrog.bintray.gradle:gradle-bintray-plugin:_")
    implementation("com.github.breadmoirai:github-release:_")
    implementation("io.github.gciatto:kt-npm-publish:_")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:_")
    testImplementation(gradleTestKit())
    testImplementation("com.uchuhimo:konf-yaml:_")
    testImplementation("io.github.classgraph:classgraph:_")
    testImplementation("io.kotest:kotest-runner-junit5:_")
    testImplementation("io.kotest:kotest-assertions-core-jvm:_")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:_")
    testImplementation("org.mockito:mockito-core:_")
//    testRuntimeOnly(files(createClasspathManifest))
}

kotlin {
    target {
        compilations.all {
            kotlinOptions {
                allWarningsAsErrors = true
//                freeCompilerArgs = listOf("-XXLanguage:+InlineClasses", "-Xopt-in=kotlin.RequiresOptIn")
                jvmTarget = JavaVersion.VERSION_1_8.toString()
            }
        }
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
    sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
        showCauses = true
        showStackTraces = true
        events(*TestLogEvent.values())
        exceptionFormat = TestExceptionFormat.FULL
    }
}

tasks.jacocoTestReport {
    reports {
        // Used by Codecov.io
        xml.isEnabled = true
    }
}

detekt {
    failFast = true
    buildUponDefaultConfig = true
    config = files("$projectDir/config/detekt.yml")
    reports {
        html.enabled = true
        xml.enabled = true
        txt.enabled = true
    }
}

pluginBundle {
    website = info.website
    vcsUrl = info.website
    tags = info.tags
}

gradlePlugin {
    plugins {
        create("KtMppPlusPlus") {
            id = "${rootProject.group}.${rootProject.name}"
            displayName = info.longName
            description = project.description
            implementationClass = info.pluginImplementationClass
        }
    }
}

val signingKey: String? by project
val signingPassword: String? by project

println(
    """
        Signing Key: ${mask(signingKey)}
        Signing Passowrd: ${mask(signingPassword)}
    """.trimIndent()
)

signing {
    useInMemoryPgpKeys(signingKey, signingPassword)
}

val mavenRepo: String by project
val mavenUsername: String by project
val mavenPassword: String by project

println(
    """
        Maven Repository: ${show(mavenRepo)}
        Maven Username: ${show(mavenUsername)}
        Maven Password: ${mask(mavenPassword)}
    """.trimIndent()
)

publishing {
    repositories {
        maven(mavenRepo) {
            credentials {
                username = mavenUsername
                password = mavenPassword
            }
        }
    }
    publications {
        withType<MavenPublication> {
            val pubName = name
            pom {
                name.set(info.longName)
                description.set(project.description)
                packaging = "jar"
                url.set(info.website)
                if (pubName.contains("plugin", ignoreCase = true)) {
                    licenses {
                        license {
                            name.set(info.license)
                            url.set(info.licenseUrl)
                        }
                    }
                }
                scm {
                    url.set(info.website)
                    connection.set(info.scm)
                    developerConnection.set(info.scm)
                }
                developers {
                    developer {
                        name.set("Giovanni Ciatto")
                        email.set("giovanni.ciatto@gmail.com")
                        url.set("https://about.me/gciatto")
                    }
                }
            }
        }
    }
}

publishOnCentral {
    projectLongName = info.longName
    projectDescription = project.description ?: "No description provided"
    projectUrl = info.website
    scmConnection = info.scm
    licenseName = info.license
    licenseUrl = info.licenseUrl
    repository(mavenRepo) {
        user = mavenUsername
        password = mavenPassword
    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri(mavenRepo))
            username.set(mavenUsername)
            password.set(mavenPassword)
        }
    }
    clientTimeout.set(Duration.ofMinutes(10))
}

fun mask(string: String?): String =
    if (string.isNullOrBlank()) "<missing>" else "<provided>"

fun show(string: String?): String =
    if (string.isNullOrBlank()) "<missing>" else string
