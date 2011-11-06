/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.testfixtures.internal;

import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.AsmBackedClassGenerator;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.internal.project.IProjectFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ServiceRegistryFactory;
import org.gradle.groovy.scripts.StringScriptSource;
import org.gradle.initialization.DefaultProjectDescriptor;
import org.gradle.initialization.DefaultProjectDescriptorRegistry;
import org.gradle.invocation.DefaultGradle;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.IOException;

/**
 * by Szczepan Faber, created at: 10/1/11
 */
public class ProjectBuilderImpl {

    private static final GlobalTestServices GLOBAL_SERVICES = new GlobalTestServices();
    private static final AsmBackedClassGenerator CLASS_GENERATOR = new AsmBackedClassGenerator();

    public Project createChildProject(String name, Project parent, File projectDir) {
        ProjectInternal parentProject = (ProjectInternal) parent;
        DefaultProject project = CLASS_GENERATOR.newInstance(
                DefaultProject.class,
                name,
                parentProject,
                (projectDir != null) ? projectDir.getAbsoluteFile() : new File(parentProject.getProjectDir(), name),
                new StringScriptSource("test build file", null),
                parentProject.getGradle(),
                parentProject.getGradle().getServices()
        );
        parentProject.addChildProject(project);
        parentProject.getProjectRegistry().addProject(project);
        return project;
    }

    public Project createProject(String name, File inputProjectDir) {
        File projectDir = prepareProjectDir(inputProjectDir);

        final File homeDir = new File(projectDir, "gradleHome");

        StartParameter startParameter = new StartParameter();
        startParameter.setGradleUserHomeDir(new File(projectDir, "userHome"));

        ServiceRegistryFactory topLevelRegistry = new TestTopLevelBuildServiceRegistry(GLOBAL_SERVICES, startParameter, homeDir);
        GradleInternal gradle = new DefaultGradle(null, startParameter, topLevelRegistry);

        DefaultProjectDescriptor projectDescriptor = new DefaultProjectDescriptor(null, name, projectDir, new DefaultProjectDescriptorRegistry());
        ProjectInternal project = topLevelRegistry.get(IProjectFactory.class).createProject(projectDescriptor, null, gradle);

        gradle.setRootProject(project);
        gradle.setDefaultProject(project);

        return project;
    }

    public File prepareProjectDir(File projectDir) {
        if (projectDir == null) {
            try {
                projectDir = GFileUtils.canonicalise(File.createTempFile("gradle", "projectDir"));
                projectDir.delete();
                projectDir.mkdir();
                projectDir.deleteOnExit();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            projectDir = GFileUtils.canonicalise(projectDir);
        }
        return projectDir;
    }
}
