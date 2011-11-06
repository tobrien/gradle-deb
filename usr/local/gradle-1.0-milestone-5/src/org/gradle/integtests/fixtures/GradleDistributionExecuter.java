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

import org.gradle.util.DeprecationLogger;
import org.gradle.util.TestFile;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A Junit rule which provides a {@link GradleExecuter} implementation that executes Gradle using a given {@link
 * GradleDistribution}. If not supplied in the constructor, this rule locates a field on the test object with type
 * {@link GradleDistribution}.
 *
 * By default, this executer will execute Gradle in a forked process. There is a system property which enables executing
 * Gradle in the current process.
 */
public class GradleDistributionExecuter extends AbstractGradleExecuter implements MethodRule {
    private static final String IGNORE_SYS_PROP = "org.gradle.integtest.ignore";
    private static final String EXECUTER_SYS_PROP = "org.gradle.integtest.executer";
    private GradleDistribution dist;
    private boolean workingDirSet;
    private boolean userHomeSet;
    private boolean deprecationChecksOn = true;
    private Executer executerType;

    public enum Executer {
        embedded(false),
        forking(true),
        daemon(true),
        embeddedDaemon(false);

        final public boolean forks;

        Executer(boolean forks) {
            this.forks = forks;
        }
    }

    public static Executer getSystemPropertyExecuter() {
        return Executer.valueOf(System.getProperty(EXECUTER_SYS_PROP, Executer.forking.toString()));
    }

    public GradleDistributionExecuter() {
        this(getSystemPropertyExecuter());
    }

    public GradleDistributionExecuter(Executer executerType) {
        this.executerType = executerType;
    }

    public GradleDistributionExecuter(GradleDistribution dist) {
        this(getSystemPropertyExecuter(), dist);
    }

    public GradleDistributionExecuter(Executer executerType, GradleDistribution dist) {
        this(executerType);
        this.dist = dist;
        reset();
    }

    public Executer getType() {
        return executerType;
    }

    public void setType(Executer executerType) {
        this.executerType = executerType;
    }

    public Statement apply(Statement base, final FrameworkMethod method, Object target) {
        if (System.getProperty(IGNORE_SYS_PROP) != null) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    System.out.println(String.format("Skipping test '%s'", method.getName()));
                }
            };
        }

        if (dist == null) {
            dist = RuleHelper.getField(target, GradleDistribution.class);
        }
        reset();
        return base;
    }

    public DaemonController getDaemonController() {
        return configureExecuter().getDaemonController();
    }

    @Override
    public GradleExecuter reset() {
        super.reset();
        workingDirSet = false;
        userHomeSet = false;
        deprecationChecksOn = true;
        DeprecationLogger.reset();
        return this;
    }

    @Override
    public GradleExecuter inDirectory(File directory) {
        super.inDirectory(directory);
        workingDirSet = true;
        return this;
    }

    @Override
    public GradleExecuter withUserHomeDir(File userHomeDir) {
        super.withUserHomeDir(userHomeDir);
        userHomeSet = true;
        return this;
    }

    @Override
    protected ExecutionResult doRun() {
        return checkResult(configureExecuter().run());
    }

    @Override
    protected ExecutionFailure doRunWithFailure() {
        return checkResult(configureExecuter().runWithFailure());
    }

    public GradleDistributionExecuter withDeprecationChecksDisabled() {
        deprecationChecksOn = false;
        return this;
    }

    private <T extends ExecutionResult> T checkResult(T result) {
        // Assert that nothing unexpected was logged
        result.assertOutputHasNoStackTraces();
        result.assertErrorHasNoStackTraces();
        if (deprecationChecksOn) {
            result.assertOutputHasNoDeprecationWarnings();
        }

        if (getExecutable() == null) {
            // Assert that no temp files are left lying around
            // Note: don't do this if a custom executable is used, as we don't know (and probably don't care) whether the executable cleans up or not
            List<String> unexpectedFiles = new ArrayList<String>();
            for (File file : getTmpDir().listFiles()) {
                if (!file.getName().matches("maven-artifact\\d+.tmp")) {
                    unexpectedFiles.add(file.getName());
                }
            }
//            Assert.assertThat(unexpectedFiles, Matchers.isEmpty());
        }

        /*
        File resolversFile = new File(getUserHomeDir(), "caches/artifacts-2/.wharf/resolvers.kryo");
        Assert.assertTrue(resolversFile.getParentFile().isDirectory());
        if (resolversFile.exists()) {
            Assert.assertThat(resolversFile.length(), Matchers.greaterThan(0L));
        }
        */

        return result;
    }

    public GradleHandle<? extends ForkingGradleExecuter> createHandle() {
        GradleExecuter executer = configureExecuter();
        if (!(executer instanceof ForkingGradleExecuter)) {
            throw new IllegalStateException("can only create handles for forking executers right now");
        }
        ForkingGradleExecuter forkingExecuter = (ForkingGradleExecuter)executer;
        return forkingExecuter.createHandle();
    }

    private GradleExecuter configureExecuter() {
        if (!workingDirSet) {
            inDirectory(dist.getTestDir());
        }
        if (!userHomeSet) {
            withUserHomeDir(dist.getUserHomeDir());
        }

        if (!getClass().desiredAssertionStatus()) {
            throw new RuntimeException("Assertions must be enabled when running integration tests.");
        }

        InProcessGradleExecuter inProcessGradleExecuter = new InProcessGradleExecuter();
        copyTo(inProcessGradleExecuter);

        GradleExecuter returnedExecuter = inProcessGradleExecuter;

        TestFile tmpDir = getTmpDir();
        tmpDir.deleteDir().createDir();

        if (executerType.forks || !inProcessGradleExecuter.canExecute()) {
            boolean useDaemon = executerType == Executer.daemon && getExecutable() == null;
            ForkingGradleExecuter forkingGradleExecuter = useDaemon ? new DaemonGradleExecuter(dist.getGradleHomeDir()) : new ForkingGradleExecuter(dist.getGradleHomeDir());
            copyTo(forkingGradleExecuter);
            forkingGradleExecuter.addGradleOpts(String.format("-Djava.io.tmpdir=%s", tmpDir));
            forkingGradleExecuter.addGradleOpts(String.format("-Dorg.gradle.daemon.idletimeout=%s", 5 * 60 * 1000));
            returnedExecuter = forkingGradleExecuter;
//        } else {
//            System.setProperty("java.io.tmpdir", tmpDir.getAbsolutePath());
        }

        if (executerType == Executer.embeddedDaemon) {
            GradleExecuter embeddedDaemonExecutor = new EmbeddedDaemonGradleExecuter();
            copyTo(embeddedDaemonExecutor);
            returnedExecuter = embeddedDaemonExecutor;
        }

        boolean settingsFound = false;
        for (
                TestFile dir = new TestFile(getWorkingDir()); dir != null && dist.isFileUnderTest(dir) && !settingsFound;
                dir = dir.getParentFile()) {
            if (dir.file("settings.gradle").isFile()) {
                settingsFound = true;
            }
        }
        if (settingsFound) {
            returnedExecuter.withSearchUpwards();
        }

        return returnedExecuter;
    }

    private TestFile getTmpDir() {
        return dist.getTestDir().file("tmp");
    }
}
