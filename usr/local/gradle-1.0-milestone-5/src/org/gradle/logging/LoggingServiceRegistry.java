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

package org.gradle.logging;

import org.gradle.StartParameter;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.project.DefaultServiceRegistry;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.cli.CommandLineConverter;
import org.gradle.logging.internal.*;
import org.gradle.util.TimeProvider;
import org.gradle.util.TrueTimeProvider;

import java.io.FileDescriptor;

/**
 * A {@link org.gradle.api.internal.project.ServiceRegistry} implementation which provides the logging services.
 */
public class LoggingServiceRegistry extends DefaultServiceRegistry {
    private TextStreamOutputEventListener stdoutListener;
    private final boolean detectConsole;

    LoggingServiceRegistry() {
        this(true);
    }

    LoggingServiceRegistry(boolean detectConsole) {
        this.detectConsole = detectConsole;
        stdoutListener = new TextStreamOutputEventListener(get(OutputEventListener.class));
    }

    /**
     * Creates a set of logging services which are suitable to use in a command-line process.
     */
    public static LoggingServiceRegistry newCommandLineProcessLogging() {
        return new LoggingServiceRegistry(true);
    }

    /**
     * Creates a set of logging services which are suitable to use in a child process. Does not attempt to use any terminal trickery.
     */
    public static LoggingServiceRegistry newChildProcessLogging() {
        return new LoggingServiceRegistry(false);
    }

    /**
     * Creates a set of logging services which are suitable to use embedded in another application. Does not attempt to use any terminal trickery.
     */
    public static LoggingServiceRegistry newEmbeddableLogging() {
        return new LoggingServiceRegistry(false);
    }

    protected CommandLineConverter<LoggingConfiguration> createCommandLineConverter() {
        return new LoggingCommandLineConverter();
    }

    protected TimeProvider createTimeProvider() {
        return new TrueTimeProvider();
    }

    protected StdOutLoggingSystem createStdOutLoggingSystem() {
        return new StdOutLoggingSystem(stdoutListener, get(TimeProvider.class));
    }

    protected StyledTextOutputFactory createStyledTextOutputFactory() {
        return new DefaultStyledTextOutputFactory(stdoutListener, get(TimeProvider.class));
    }

    protected StdErrLoggingSystem createStdErrLoggingSystem() {
        return new StdErrLoggingSystem(new TextStreamOutputEventListener(get(OutputEventListener.class)), get(TimeProvider.class));
    }

    protected ProgressLoggerFactory createProgressLoggerFactory() {
        return new DefaultProgressLoggerFactory(new ProgressLoggingBridge(get(OutputEventListener.class)), get(TimeProvider.class));
    }

    protected Factory<LoggingManagerInternal> createLoggingManagerFactory() {
        OutputEventRenderer renderer = get(OutputEventRenderer.class);
        Slf4jLoggingConfigurer slf4jConfigurer = new Slf4jLoggingConfigurer(renderer);
        LoggingConfigurer compositeConfigurer = new DefaultLoggingConfigurer(renderer, slf4jConfigurer, new JavaUtilLoggingConfigurer());
        return new DefaultLoggingManagerFactory(compositeConfigurer, renderer, getStdOutLoggingSystem(), getStdErrLoggingSystem());
    }

    private LoggingSystem getStdErrLoggingSystem() {
        return get(StdErrLoggingSystem.class);
    }

    private LoggingSystem getStdOutLoggingSystem() {
        return get(StdOutLoggingSystem.class);
    }

    protected OutputEventRenderer createOutputEventRenderer() {
        Spec<FileDescriptor> terminalDetector;
        if (detectConsole) {
            StartParameter startParameter = new StartParameter();
            JnaBootPathConfigurer jnaConfigurer = new JnaBootPathConfigurer(startParameter.getGradleUserHomeDir());
            terminalDetector = new TerminalDetectorFactory().create(jnaConfigurer);
        } else {
            terminalDetector = Specs.satisfyNone();
        }
        return new OutputEventRenderer(terminalDetector).addStandardOutputAndError();
    }
}
