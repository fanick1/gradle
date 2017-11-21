/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.buildinit.plugins.fixtures

import org.gradle.api.JavaVersion
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitBuildScriptDsl
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion
import org.hamcrest.Matcher

import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitBuildScriptDsl.GROOVY
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitBuildScriptDsl.KOTLIN
import static org.hamcrest.Matchers.containsString

class ScriptDslFixture {

    static final List<BuildInitBuildScriptDsl> SCRIPT_DSLS = JavaVersion.current().isJava8Compatible() ? [GROOVY, KOTLIN] : [GROOVY]

    static final List<List<BuildInitBuildScriptDsl>> scriptDslCombinationsFor(int count) {
        return ([SCRIPT_DSLS] * count).combinations()
    }

    static ScriptDslFixture of(BuildInitBuildScriptDsl scriptDsl, TestFile rootDir) {
        new ScriptDslFixture(scriptDsl, rootDir)
    }

    final BuildInitBuildScriptDsl scriptDsl
    final TestFile rootDir

    ScriptDslFixture(BuildInitBuildScriptDsl scriptDsl, TestFile rootDir) {
        this.scriptDsl = scriptDsl
        this.rootDir = rootDir
    }

    String fileNameFor(String fileNameWithoutExtension) {
        scriptDsl.fileNameFor(fileNameWithoutExtension)
    }

    String getBuildFileName() {
        fileNameFor("build")
    }

    String getSettingsFileName() {
        fileNameFor("settings")
    }

    TestFile getBuildFile(TestFile parentFolder = rootDir) {
        parentFolder.file(buildFileName)
    }

    TestFile getSettingsFile(TestFile parentFolder = rootDir) {
        parentFolder.file(settingsFileName)
    }

    TestFile scriptFile(String filePathWithoutExtension, TestFile parentFolder = rootDir) {
        def fileWithoutExtension = parentFolder.file(filePathWithoutExtension)
        new TestFile(fileWithoutExtension.parentFile, fileNameFor(fileWithoutExtension.name))
    }

    void assertGradleFilesGenerated(TestFile parentFolder = rootDir) {
        assert getBuildFile(parentFolder).exists()
        assert getSettingsFile(parentFolder).exists()
        def gradleVersion = GradleVersion.current().version
        def distributionType = scriptDsl.wrapperDistributionType.name().toLowerCase()
        new WrapperTestFixture(parentFolder).generated(gradleVersion, distributionType)
    }

    void assertWrapperNotGenerated(TestFile parentFolder = rootDir) {
        new WrapperTestFixture(parentFolder).notGenerated()
    }

    Matcher<String> containsStringAssignment(target, string) {
        switch (scriptDsl) {
            case KOTLIN:
                return containsString("$target = \"$string\"")
            case GROOVY:
            default:
                return containsString("$target = '$string'")
        }
    }

    Matcher<String> containsConfigurationDependency(String configuration, String group, String name, String version, boolean useKotlinAccessors = true) {
        switch (scriptDsl) {
            case KOTLIN:
                if (useKotlinAccessors) {
                    return containsString("$configuration(group = \"$group\", name = \"$name\", version = \"$version\")")
                } else {
                    return containsString("\"$configuration\"(group = \"$group\", name = \"$name\", version = \"$version\")")
                }
            case GROOVY:
            default:
                return containsString("$configuration group: '$group', name: '$name', version: '$version'")
        }
    }

    Matcher<String> containsConfigurationDependencyNotation(String configuration, String notation, boolean useKotlinAccessors = true) {
        switch (scriptDsl) {
            case KOTLIN:
                if (useKotlinAccessors) {
                    return containsString("$configuration(\"$notation")
                } else {
                    return containsString("\"$configuration\"(\"$notation")
                }
            case GROOVY:
            default:
                return containsString("$configuration '$notation")
        }
    }
}