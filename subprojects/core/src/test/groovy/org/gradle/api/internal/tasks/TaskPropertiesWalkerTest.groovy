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

package org.gradle.api.internal.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class TaskPropertiesWalkerTest extends AbstractProjectBuilderSpec {

    def visitor = Mock(InputsVisitor)

    def "visits inputs"() {
        def task = project.tasks.create("myTask", MyTask)

        when:
        new TaskPropertiesWalker([]).visitInputs(task, visitor)

        then:
        1 * visitor.visitProperty({ it.name == 'myProperty' && it.value == 'myValue' })
        1 * visitor.visitFileProperty({ it.propertyName == 'inputFile' })
        1 * visitor.visitFileProperty({ it.propertyName == 'inputFiles' })
        1 * visitor.visitProperty({ it.name == 'bean.class' && it.value == NestedBean.name })
        1 * visitor.visitProperty({ it.name == 'bean.nestedInput' && it.value == 'nested' })
        1 * visitor.visitFileProperty({ it.propertyName == 'bean.inputDir' })

        0 * _
    }

    def "nested bean with null value is detected"() {
        def task = project.tasks.create("myTask", MyTask)
        task.bean = null

        when:
        new TaskPropertiesWalker([]).visitInputs(task, visitor)

        then:
        1 * visitor.visitProperty({ it.name == 'bean.class' && it.value == null })
    }

    static class MyTask extends DefaultTask {

        @Input
        String myProperty = "myValue"

        @InputFile
        File inputFile = new File("some-location")

        @InputFiles
        FileCollection inputFiles = new SimpleFileCollection([new File("files")])

        @Nested
        Object bean = new NestedBean()

    }

    static class NestedBean {
        @Input
        String nestedInput = 'nested'

        @InputDirectory
        File inputDir
    }

}
