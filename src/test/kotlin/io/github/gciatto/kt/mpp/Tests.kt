package io.github.gciatto.kt.mpp

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.source.yaml
import io.github.classgraph.ClassGraph
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.file.shouldBeAFile
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.slf4j.LoggerFactory
import java.io.File

class Tests : StringSpec({
    val scan = ClassGraph()
        .enableAllInfo()
        .acceptPackages(Tests::class.java.`package`.name)
        .scan()
    scan.getResourcesWithLeafName("test.yaml")
        .flatMap {
            log.debug("Found test list in $it")
            val yamlFile = File(it.classpathElementFile.absolutePath + "/" + it.path)
            val testConfiguration = Config {
                addSpec(Root)
            }.from.yaml.inputStream(it.open())
            testConfiguration[Root.tests].map { it to yamlFile.parentFile }
        }.forEach { (test, location) ->
            log.debug("Test to be executed: $test from $location")
            val testFolder = folder {
                location.copyRecursively(this.root)
            }
            log.debug("Test has been copied into $testFolder and is ready to get executed")
            test.description {
                val runner = GradleRunner.create()
                    .withProjectDir(testFolder.root)
                    .withPluginClasspath() // .also { println("Plugin Classpath:\n\t" + it.pluginClasspath.joinToString("\n\t")) }
//                    .withPluginClasspath(classpath)
                    .withDebug(true)
                    .withArguments(test.configuration.tasks + test.configuration.options)
                val result = if (test.expectation.failure.isEmpty()) {
                    runner.build()
                } else {
                    runner.buildAndFail()
                }
                println(result.tasks)
                println(result.output)
                test.expectation.output_contains.forEach {
                    result.output shouldContain it
                }
                test.expectation.output_matches.forEach { regexString ->
                    val regex = Regex(regexString)
                    result.output.lineSequence().anyShouldMatch(regex)
                }
                test.expectation.success.forEach {
                    result.outcomeOf(it) shouldBe TaskOutcome.SUCCESS
                }
                test.expectation.failure.forEach {
                    result.outcomeOf(it) shouldBe TaskOutcome.FAILED
                }
                test.expectation.file_exists.forEach {
                    val file = it.actualFile(testFolder.root)
                    file.shouldExist()
                    file.shouldBeAFile()
                    it.shouldBeValidWrt(testFolder.root)
                }
            }
        }
}) {
    companion object {
        val log = LoggerFactory.getLogger(Tests::class.java)

        private fun BuildResult.outcomeOf(name: String) = task(":$name")
            ?.outcome
            ?: throw IllegalStateException("Task $name was not present among the executed tasks")

        private fun folder(closure: TemporaryFolder.() -> Unit) = TemporaryFolder().apply {
            create()
            closure()
        }

        private fun Sequence<String>.anyShouldMatch(regex: Regex) {
            if (!any { regex.matches(it) }) {
                throw AssertionError("No line matches: ${regex.pattern}")
            }
        }

        private fun ExistingFile.shouldBeValidWrt(root: File) {
            if (!this.isValid(root)) {
                throw AssertionError(
                    "File ${file.resolve(root)} should exist and contain " +
                        (if (all) "all of" else "any of") +
                        " the following lines: " +
                        contents.joinToString { "`$it`" }
                )
            }
        }
    }
}
