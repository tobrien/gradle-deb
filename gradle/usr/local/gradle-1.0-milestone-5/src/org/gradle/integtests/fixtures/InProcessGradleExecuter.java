/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.integtests.fixtures;

import junit.framework.AssertionFailedError;
import org.gradle.BuildResult;
import org.gradle.GradleLauncher;
import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.api.LocationAwareException;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.tasks.TaskState;
import org.gradle.cli.CommandLineParser;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.initialization.DefaultGradleLauncherFactory;
import org.gradle.os.ProcessEnvironment;
import org.gradle.os.jna.NativeEnvironment;
import org.hamcrest.Matcher;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.*;

import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class InProcessGradleExecuter extends AbstractGradleExecuter {
    private final ProcessEnvironment envHacker = NativeEnvironment.current();

    @Override
    protected ExecutionResult doRun() {
        OutputListenerImpl outputListener = new OutputListenerImpl();
        OutputListenerImpl errorListener = new OutputListenerImpl();
        BuildListenerImpl buildListener = new BuildListenerImpl();
        BuildResult result = doRun(outputListener, errorListener, buildListener);
        result.rethrowFailure();
        return new InProcessExecutionResult(buildListener.executedTasks, buildListener.skippedTasks,
                outputListener.toString(), errorListener.toString());
    }

    @Override
    protected ExecutionFailure doRunWithFailure() {
        OutputListenerImpl outputListener = new OutputListenerImpl();
        OutputListenerImpl errorListener = new OutputListenerImpl();
        BuildListenerImpl buildListener = new BuildListenerImpl();
        try {
            doRun(outputListener, errorListener, buildListener).rethrowFailure();
            throw new AssertionFailedError("expected build to fail but it did not.");
        } catch (GradleException e) {
            return new InProcessExecutionFailure(buildListener.executedTasks, buildListener.skippedTasks,
                    outputListener.writer.toString(), errorListener.writer.toString(), e);
        }
    }

    private BuildResult doRun(final OutputListenerImpl outputListener, OutputListenerImpl errorListener,
                              BuildListenerImpl listener) {
        assertCanExecute();

        InputStream originalStdIn = System.in;
        System.setIn(getStdin());
        
        File userDir = new File(System.getProperty("user.dir"));
        StartParameter parameter = new StartParameter();
        parameter.setLogLevel(LogLevel.INFO);
        parameter.setSearchUpwards(true);
        parameter.setCurrentDir(getWorkingDir());

        CommandLineParser parser = new CommandLineParser();
        DefaultCommandLineConverter converter = new DefaultCommandLineConverter();
        converter.configure(parser);
        converter.convert(parser.parse(getAllArgs()), parameter);

        Properties originalSysProperties = new Properties();
        originalSysProperties.putAll(System.getProperties());
        envHacker.setProcessDir(getWorkingDir());
        Map<String, String> previousEnv = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : getEnvironmentVars().entrySet()) {
            previousEnv.put(entry.getKey(), System.getenv(entry.getKey()));
            envHacker.setEnvironmentVariable(entry.getKey(), entry.getValue());
        }

        DefaultGradleLauncherFactory factory = (DefaultGradleLauncherFactory) GradleLauncher.getFactory();
        factory.addListener(listener);
        GradleLauncher gradleLauncher = GradleLauncher.newInstance(parameter);
        gradleLauncher.addStandardOutputListener(outputListener);
        gradleLauncher.addStandardErrorListener(errorListener);
        try {
            return gradleLauncher.run();
        } finally {
            System.setProperties(originalSysProperties);
            envHacker.setProcessDir(userDir);
            for (Map.Entry<String, String> entry : previousEnv.entrySet()) {
                String oldValue = entry.getValue();
                if (oldValue != null) {
                    envHacker.setEnvironmentVariable(entry.getKey(), oldValue);
                } else {
                    envHacker.removeEnvironmentVariable(entry.getKey());
                }
            }
            factory.removeListener(listener);
            System.setIn(originalStdIn);
        }
    }

    public DaemonController getDaemonController() {
        throw new UnsupportedOperationException();
    }

    public void assertCanExecute() {
        assertNull(getExecutable());
    }

    public boolean canExecute() {
        try {
            assertCanExecute();
        } catch (AssertionError e) {
            return false;
        }
        return true;
    }

    private static class BuildListenerImpl implements TaskExecutionGraphListener {
        private final List<String> executedTasks = new ArrayList<String>();
        private final Set<String> skippedTasks = new HashSet<String>();

        public void graphPopulated(TaskExecutionGraph graph) {
            List<Task> planned = new ArrayList<Task>(graph.getAllTasks());
            graph.addTaskExecutionListener(new TaskListenerImpl(planned, executedTasks, skippedTasks));
        }
    }

    private static class OutputListenerImpl implements StandardOutputListener {
        private StringWriter writer = new StringWriter();

        @Override
        public String toString() {
            return writer.toString();
        }

        public void onOutput(CharSequence output) {
            writer.append(output);
        }
    }

    private static class TaskListenerImpl implements TaskExecutionListener {
        private final List<Task> planned;
        private final List<String> executedTasks;
        private final Set<String> skippedTasks;
        private Task current;

        public TaskListenerImpl(List<Task> planned, List<String> executedTasks, Set<String> skippedTasks) {
            this.planned = planned;
            this.executedTasks = executedTasks;
            this.skippedTasks = skippedTasks;
        }

        public void beforeExecute(Task task) {
            assertThat(current, nullValue());
            assertTrue(planned.contains(task));
            current = task;

            String taskPath = path(task);
            if (taskPath.startsWith(":buildSrc:")) {
                return;
            }

            executedTasks.add(taskPath);
        }

        public void afterExecute(Task task, TaskState state) {
            assertThat(task, sameInstance(current));
            current = null;

            String taskPath = path(task);
            if (taskPath.startsWith(":buildSrc:")) {
                return;
            }

            if (state.getSkipped()) {
                skippedTasks.add(taskPath);
            }
        }

        private String path(Task task) {
            return task.getProject().getGradle().getParent() == null ? task.getPath() : ":" + task.getProject().getRootProject().getName() + task.getPath();
        }
    }

    public static class InProcessExecutionResult extends AbstractExecutionResult {
        private final List<String> plannedTasks;
        private final Set<String> skippedTasks;
        private final String output;
        private final String error;

        public InProcessExecutionResult(List<String> plannedTasks, Set<String> skippedTasks, String output,
                                        String error) {
            this.plannedTasks = plannedTasks;
            this.skippedTasks = skippedTasks;
            this.output = output;
            this.error = error;
        }

        public String getOutput() {
            return output;
        }

        public String getError() {
            return error;
        }

        public List<String> getExecutedTasks() {
            return new ArrayList<String>(plannedTasks);
        }

        public ExecutionResult assertTasksExecuted(String... taskPaths) {
            List<String> expected = Arrays.asList(taskPaths);
            assertThat(plannedTasks, equalTo(expected));
            return this;
        }

        public Set<String> getSkippedTasks() {
            return new HashSet<String>(skippedTasks);
        }

        public ExecutionResult assertTasksSkipped(String... taskPaths) {
            Set<String> expected = new HashSet<String>(Arrays.asList(taskPaths));
            assertThat(skippedTasks, equalTo(expected));
            return this;
        }

        public ExecutionResult assertTaskSkipped(String taskPath) {
            assertThat(skippedTasks, hasItem(taskPath));
            return this;
        }

        public ExecutionResult assertTasksNotSkipped(String... taskPaths) {
            Set<String> expected = new HashSet<String>(Arrays.asList(taskPaths));
            Set<String> notSkipped = getNotSkippedTasks();
            assertThat(notSkipped, equalTo(expected));
            return this;
        }

        public ExecutionResult assertTaskNotSkipped(String taskPath) {
            assertThat(getNotSkippedTasks(), hasItem(taskPath));
            return this;
        }

        private Set<String> getNotSkippedTasks() {
            Set<String> notSkipped = new HashSet<String>(plannedTasks);
            notSkipped.removeAll(skippedTasks);
            return notSkipped;
        }
    }

    private static class InProcessExecutionFailure extends InProcessExecutionResult implements ExecutionFailure {
        private final GradleException failure;

        public InProcessExecutionFailure(List<String> tasks, Set<String> skippedTasks, String output, String error,
                                         GradleException failure) {
            super(tasks, skippedTasks, output, error);
            this.failure = failure;
        }

        public ExecutionFailure assertHasLineNumber(int lineNumber) {
            assertThat(failure.getMessage(), containsString(String.format(" line: %d", lineNumber)));
            return this;

        }

        public ExecutionFailure assertHasFileName(String filename) {
            assertThat(failure.getMessage(), startsWith(String.format("%s", filename)));
            return this;
        }

        public ExecutionFailure assertHasCause(String description) {
            assertThatCause(startsWith(description));
            return this;
        }

        public ExecutionFailure assertThatCause(final Matcher<String> matcher) {
            if (failure instanceof LocationAwareException) {
                LocationAwareException exception = (LocationAwareException) failure;
                assertThat(exception.getReportableCauses(), hasItem(hasMessage(matcher)));
            } else {
                assertThat(failure.getCause(), notNullValue());
                assertThat(failure.getCause().getMessage(), matcher);
            }
            return this;
        }

        public ExecutionFailure assertHasNoCause() {
            if (failure instanceof LocationAwareException) {
                LocationAwareException exception = (LocationAwareException) failure;
                assertThat(exception.getReportableCauses(), isEmpty());
            } else {
                assertThat(failure.getCause(), nullValue());
            }
            return this;
        }

        public ExecutionFailure assertHasDescription(String context) {
            assertThatDescription(startsWith(context));
            return this;
        }

        public ExecutionFailure assertThatDescription(Matcher<String> matcher) {
            assertThat(failure.getMessage(), containsLine(matcher));
            return this;
        }
    }
}
