/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.initialization;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.composite.internal.IncludedBuildControllers;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.execution.BuildExecuter;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.service.scopes.BuildScopeServices;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DefaultGradleLauncher implements GradleLauncher {
    private enum Stage {
        LoadSettings, Configure, TaskGraph, RunTasks() {
            @Override
            String getDisplayName() {
                return "Build";
            }
        }, Finished;

        String getDisplayName() {
            return name();
        }
    }

    private final BuildConfigurer buildConfigurer;
    private final ExceptionAnalyser exceptionAnalyser;
    private final BuildListener buildListener;
    private final BuildCompletionListener buildCompletionListener;
    private final BuildExecuter buildExecuter;
    private final BuildScopeServices buildServices;
    private final List<?> servicesToStop;
    private final IncludedBuildControllers includedBuildControllers;
    private final GradleInternal gradle;
    private Stage stage;

    private final InstantExecution instantExecution;
    private final SettingsPreparer settingsPreparer;
    private final TaskExecutionPreparer taskExecutionPreparer;

    public DefaultGradleLauncher(GradleInternal gradle, BuildConfigurer buildConfigurer, ExceptionAnalyser exceptionAnalyser,
                                 BuildListener buildListener, BuildCompletionListener buildCompletionListener,
                                 BuildExecuter buildExecuter, BuildScopeServices buildServices,
                                 List<?> servicesToStop, IncludedBuildControllers includedBuildControllers,
                                 SettingsPreparer settingsPreparer, TaskExecutionPreparer taskExecutionPreparer,
                                 InstantExecution instantExecution) {
        this.gradle = gradle;
        this.buildConfigurer = buildConfigurer;
        this.exceptionAnalyser = exceptionAnalyser;
        this.buildListener = buildListener;
        this.buildExecuter = buildExecuter;
        this.buildCompletionListener = buildCompletionListener;
        this.buildServices = buildServices;
        this.servicesToStop = servicesToStop;
        this.includedBuildControllers = includedBuildControllers;
        this.instantExecution = instantExecution;
        this.settingsPreparer = settingsPreparer;
        this.taskExecutionPreparer = taskExecutionPreparer;
    }

    @Override
    public GradleInternal getGradle() {
        return gradle;
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        doBuildStages(Stage.LoadSettings);
        return gradle.getSettings();
    }

    @Override
    public GradleInternal getConfiguredBuild() {
        doBuildStages(Stage.Configure);
        return gradle;
    }

    public GradleInternal executeTasks() {
        doBuildStages(Stage.RunTasks);
        return gradle;
    }

    @Override
    public void finishBuild() {
        if (stage != null) {
            finishBuild(stage.getDisplayName(), null);
        }
    }

    private void doBuildStages(Stage upTo) {
        Preconditions.checkArgument(
            upTo != Stage.Finished,
            "Stage.Finished is not supported by doBuildStages."
        );
        try {
            if (upTo == Stage.RunTasks && instantExecution.canExecuteInstantaneously()) {
                doInstantExecution();
            } else {
                doClassicBuildStages(upTo);
            }
        } catch (Throwable t) {
            finishBuild(upTo.getDisplayName(), t);
        }
    }

    private void doClassicBuildStages(Stage upTo) {
        prepareSettings();
        if (upTo == Stage.LoadSettings) {
            return;
        }
        configureBuild();
        if (upTo == Stage.Configure) {
            return;
        }
        prepareTaskExecution();
        if (upTo == Stage.TaskGraph) {
            return;
        }
        instantExecution.saveTaskGraph();
        runTasks();
    }

    private void doInstantExecution() {
        buildListener.buildStarted(gradle);
        instantExecution.loadTaskGraph();
        stage = Stage.TaskGraph;
        runTasks();
    }

    private void finishBuild(String action, @Nullable Throwable stageFailure) {
        if (stage == Stage.Finished) {
            return;
        }

        RuntimeException reportableFailure = stageFailure == null ? null : exceptionAnalyser.transform(stageFailure);
        BuildResult buildResult = new BuildResult(action, gradle, reportableFailure);
        List<Throwable> failures = new ArrayList<Throwable>();
        includedBuildControllers.finishBuild(failures);
        try {
            buildListener.buildFinished(buildResult);
        } catch (Throwable t) {
            failures.add(t);
        }
        stage = Stage.Finished;

        if (failures.isEmpty() && reportableFailure != null) {
            throw reportableFailure;
        }
        if (!failures.isEmpty()) {
            if (stageFailure instanceof MultipleBuildFailures) {
                failures.addAll(0, ((MultipleBuildFailures) stageFailure).getCauses());
            } else if (stageFailure != null) {
                failures.add(0, stageFailure);
            }
            throw exceptionAnalyser.transform(new MultipleBuildFailures(failures));
        }
    }

    private void prepareSettings() {
        if (stage == null) {
            buildListener.buildStarted(gradle);

            settingsPreparer.prepareSettings(gradle);

            stage = Stage.LoadSettings;
        }
    }

    private void configureBuild() {
        if (stage == Stage.LoadSettings) {
            buildConfigurer.configure(gradle);

            stage = Stage.Configure;
        }
    }

    private void prepareTaskExecution() {
        if (stage == Stage.Configure) {
            taskExecutionPreparer.prepareForTaskExecution(gradle);

            stage = Stage.TaskGraph;
        }
    }

    @Override
    public void scheduleTasks(final Iterable<String> taskPaths) {
        GradleInternal gradle = getConfiguredBuild();
        Set<String> allTasks = Sets.newLinkedHashSet(gradle.getStartParameter().getTaskNames());
        boolean added = allTasks.addAll(Lists.newArrayList(taskPaths));

        if (!added) {
            return;
        }

        gradle.getStartParameter().setTaskNames(allTasks);

        // Force back to configure so that task graph will get reevaluated
        stage = Stage.Configure;

        doBuildStages(Stage.TaskGraph);
    }

    private void runTasks() {
        if (stage != Stage.TaskGraph) {
            throw new IllegalStateException("Cannot execute tasks: current stage = " + stage);
        }

        List<Throwable> taskFailures = new ArrayList<Throwable>();
        buildExecuter.execute(gradle, taskFailures);
        if (!taskFailures.isEmpty()) {
            throw new MultipleBuildFailures(taskFailures);
        }

        stage = Stage.RunTasks;
    }

    /**
     * <p>Adds a listener to this build instance. The listener is notified of events which occur during the execution of the build. See {@link org.gradle.api.invocation.Gradle#addListener(Object)} for
     * supported listener types.</p>
     *
     * @param listener The listener to add. Has no effect if the listener has already been added.
     */
    @Override
    public void addListener(Object listener) {
        gradle.addListener(listener);
    }

    public void stop() {
        try {
            CompositeStoppable.stoppable(buildServices).add(servicesToStop).stop();
        } finally {
            buildCompletionListener.completed();
        }
    }
}
