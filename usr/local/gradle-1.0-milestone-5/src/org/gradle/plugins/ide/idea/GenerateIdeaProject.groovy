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
package org.gradle.plugins.ide.idea

import org.gradle.plugins.ide.api.XmlGeneratorTask
import org.gradle.plugins.ide.idea.model.IdeaProject
import org.gradle.plugins.ide.idea.model.Project
import org.gradle.util.DeprecationLogger

/**
 * Generates an IDEA project file for root project *only*. If you want to fine tune the idea configuration
 * <p>
 * At this moment nearly all configuration is done via {@link IdeaProject}.
 *
 * @author Hans Dockter
 */
public class GenerateIdeaProject extends XmlGeneratorTask<Project> {

    /**
     * idea project model
     */
    IdeaProject ideaProject;

    @Override protected void configure(Project xmlModule) {
        getIdeaProject().mergeXmlProject(xmlModule)
    }

    @Override Project create() {
        def project = new Project(xmlTransformer, ideaProject.pathFactory)
        return project
    }

    /**
     * Deprecated. Please use #idea.project.modules. See examples in {@link IdeaProject}.
     * <p>
     * The subprojects that should be mapped to modules in the ipr file. The subprojects will only be mapped if the Idea plugin has been
     * applied to them.
     */
    @Deprecated
    Set<org.gradle.api.Project> getSubprojects() {
        DeprecationLogger.nagUserWith("ideaProject.subprojects doesn't do anything at this moment. Please use idea.project.modules instead.")
    }

    @Deprecated
    void setSubprojects(Set<org.gradle.api.Project> subprojects) {
        DeprecationLogger.nagUserWith("ideaProject.subprojects doesn't do anything at this moment. Please use idea.project.modules instead.")
    }

    /**
     * Deprecated. Please use #idea.project.jdkName. See examples in {@link IdeaProject}.
     * <p>
     * The java version used for defining the project sdk.
     */
    @Deprecated
    String getJavaVersion() {
        DeprecationLogger.nagUserOfReplacedMethod("ideaProject.javaVersion", "idea.project.jdkName")
        ideaProject.jdkName
    }

    @Deprecated
    void setJavaVersion(String jdkName) {
        DeprecationLogger.nagUserOfReplacedMethod("ideaProject.javaVersion", "idea.project.jdkName")
        ideaProject.jdkName = jdkName
    }

    /**
     * Deprecated. Please use #idea.project.wildcards. See examples in {@link IdeaProject}.
     * <p>
     * The wildcard resource patterns.
     */
    @Deprecated
    Set getWildcards() {
        DeprecationLogger.nagUserOfReplacedMethod("ideaProject.wildcards", "idea.project.wildcards")
        ideaProject.wildcards
    }

    @Deprecated
    void setWildcards(Set wildcards) {
        DeprecationLogger.nagUserOfReplacedMethod("ideaProject.wildcards", "idea.project.wildcards")
        ideaProject.wildcards = wildcards
    }

    /**
     * output *.ipr file
     */
    File getOutputFile() {
        return ideaProject.outputFile
    }

    void setOutputFile(File newOutputFile) {
        ideaProject.outputFile = newOutputFile
    }
}
