/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.project.taskfactory;

import org.gradle.api.internal.TaskInputsInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.CompileClasspathSnapshotter;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter;
import org.gradle.api.internal.tasks.DeclaredTaskInputFileProperty;
import org.gradle.api.internal.tasks.InputsVisitor;
import org.gradle.api.internal.tasks.PropertyInfo;
import org.gradle.api.internal.tasks.TaskPropertyValue;
import org.gradle.api.internal.tasks.ValidationAction;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.CompileClasspathNormalizer;
import org.gradle.api.tasks.InputFiles;

import java.lang.annotation.Annotation;

public class CompileClasspathPropertyAnnotationHandler implements OverridingPropertyAnnotationHandler, FileSnapshottingPropertyAnnotationHandler {
    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return CompileClasspath.class;
    }

    @Override
    public Class<? extends Annotation> getOverriddenAnnotationType() {
        return InputFiles.class;
    }

    @Override
    public Class<? extends FileCollectionSnapshotter> getSnapshotterImplementationType() {
        return CompileClasspathSnapshotter.class;
    }

    @Override
    public void attachActions(final TaskPropertyActionContext context) {
        context.setConfigureAction(new UpdateAction() {
            public void update(TaskInternal task, TaskPropertyValue futureValue) {
                task.getInputs().registerFiles(futureValue)
                    .withPropertyName(context.getName())
                    .withNormalizer(CompileClasspathNormalizer.class)
                    .optional(context.isOptional());
            }
        });
    }

    @Override
    public void accept(PropertyInfo propertyInfo, InputsVisitor visitor, TaskInputsInternal inputs) {
        DeclaredTaskInputFileProperty fileSpec = inputs.createFileSpec(propertyInfo, ValidationAction.NO_OP);
        fileSpec
            .withPropertyName(propertyInfo.getName())
            .withNormalizer(CompileClasspathNormalizer.class)
            .optional(propertyInfo.isOptional());
        visitor.visitFileProperty(fileSpec);
    }
}
