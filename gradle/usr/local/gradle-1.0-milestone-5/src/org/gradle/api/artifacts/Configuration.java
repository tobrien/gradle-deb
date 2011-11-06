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
package org.gradle.api.artifacts;

import groovy.lang.Closure;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.util.Map;
import java.util.Set;

/**
 * <p>A {@code Configuration} represents a group of artifacts and their dependencies.</p>
 */
public interface Configuration extends FileCollection {

    /**
     * Returns the resolution strategy used by this configuration
     *
     * @return strategy
     */
    ResolutionStrategy getResolutionStrategy();

    /**
     * The states a configuration can be into. A configuration is only mutable as long as it is
     * in the unresolved state.
     */
    enum State { UNRESOLVED, RESOLVED, RESOLVED_WITH_FAILURES }

    /**
     * Returns the state of the configuration.
     *
     * @see org.gradle.api.artifacts.Configuration.State
     */
    State getState();

    /**
     * Returns the name of this configuration.
     *
     * @return The configuration name, never null.
     */
    String getName();

    /**
     * A {@link org.gradle.api.Namer} namer for configurations that returns {@link #getName()}.
     */
    static class Namer implements org.gradle.api.Namer<Configuration> {
        public String determineName(Configuration c) {
            return c.getName();
        }
    }
    
    /**
     * Returns true if this is a visible configuration. A visible configuration is usable outside the project it belongs
     * to. The default value is true.
     *
     * @return true if this is a visible configuration.
     */
    boolean isVisible();

    /**
     * Sets the visibility of this configuration. When visible is set to true, this configuration is visibile outside
     * the project it belongs to. The default value is true.
     *
     * @param visible true if this is a visible configuration
     * @return this configuration
     */
    Configuration setVisible(boolean visible);

    /**
     * Returns the names of the configurations which this configuration extends from. The artifacts of the super
     * configurations are also available in this configuration.
     *
     * @return The super configurations. Returns an empty set when this configuration does not extend any others.
     */
    Set<Configuration> getExtendsFrom();

    /**
     * Sets the configurations which this configuration extends from.
     *
     * @param superConfigs The super configuration. Should not be null.
     * @return this configuration
     */
    Configuration setExtendsFrom(Set<Configuration> superConfigs);

    /**
     * Adds the given configurations to the set of configuration which this configuration extends from.
     *
     * @param superConfigs The super configurations.
     * @return this configuration
     */
    Configuration extendsFrom(Configuration... superConfigs);

    /**
     * Returns the transitivity of this configuration. A transitive configuration contains the transitive closure of its
     * direct dependencies, and all their dependencies. An intransitive configuration contains only the direct
     * dependencies. The default value is true.
     *
     * @return true if this is a transitive configuration, false otherwise.
     */
    boolean isTransitive();

    /**
     * Sets the transitivity of this configuration. When set to true, this configuration will contain the transitive
     * closure of its dependencies and their dependencies. The default value is true.
     *
     * @param t true if this is a transitive configuration.
     * @return this configuration
     */
    Configuration setTransitive(boolean t);

    /**
     * Returns the description for this configuration.
     *
     * @return the description. May be null.
     */
    String getDescription();

    /**
     * Sets the description for this configuration.
     *
     * @param description the description. May be null
     * @return this configuration
     */
    Configuration setDescription(String description);

    /**
     * Gets a ordered set including this configuration and all superconfigurations
     * recursively.
     * @return the list of all configurations
     */
    Set<Configuration> getHierarchy();

    /**
     * Resolves this configuration. This locates and downloads the files which make up this configuration, and returns
     * the resulting set of files.
     *
     * @return The files of this configuration.
     */
    Set<File> resolve();

    /**
     * Takes a closure which gets coerced into a Spec. Behaves otherwise in the same way as
     * {@link #files(org.gradle.api.specs.Spec)}.
     *
     * @param dependencySpecClosure The closure describing a filter applied to the all the dependencies of this configuration (including dependencies from extended configurations).
     * @return The files of a subset of dependencies of this configuration.
     */
    Set<File> files(Closure dependencySpecClosure);

    /**
     * Resolves this configuration. This locates and downloads the files which make up this configuration.
     * But only the resulting set of files belonging to the subset of dependencies specified by the dependencySpec
     * is returned.
     *
     * @param dependencySpec The spec describing a filter applied to the all the dependencies of this configuration (including dependencies from extended configurations).
     * @return The files of a subset of dependencies of this configuration.
     */
    Set<File> files(Spec<? super Dependency> dependencySpec);

    /**
     * Resolves this configuration. This locates and downloads the files which make up this configuration.
     * But only the resulting set of files belonging to the specified dependencies
     * is returned.
     *
     * @param dependencies The dependences to be resolved
     * @return The files of a subset of dependencies of this configuration.
     */
    Set<File> files(Dependency... dependencies);

    /**
     * Resolves this configuration lazily. The resolve happens when the elements of the returned FileCollection get accessed the first time.
     * This locates and downloads the files which make up this configuration. Only the resulting set of files belonging to the subset
     * of dependencies specified by the dependencySpec is contained in the FileCollection.
     *
     * @param dependencySpec The spec describing a filter applied to the all the dependencies of this configuration (including dependencies from extended configurations).
     * @return The FileCollection with a subset of dependencies of this configuration.
     */
    FileCollection fileCollection(Spec<? super Dependency> dependencySpec);

    /**
     * Takes a closure which gets coerced into a Spec. Behaves otherwise in the same way as
     * {@link #fileCollection(org.gradle.api.specs.Spec)}.
     *
     * @param dependencySpecClosure The closure describing a filter applied to the all the dependencies of this configuration (including dependencies from extended configurations).
     * @return The FileCollection with a subset of dependencies of this configuration.
     */
    FileCollection fileCollection(Closure dependencySpecClosure);

    /**
     * Resolves this configuration lazily. The resolve happens when the elements of the returned FileCollection get accessed the first time.
     * This locates and downloads the files which make up this configuration. Only the resulting set of files belonging to specified
     * dependencies is contained in the FileCollection.
     *
     * @param dependencies The dependencies for which the FileCollection should contain the files.
     * @return The FileCollection with a subset of dependencies of this configuration.
     */
    FileCollection fileCollection(Dependency... dependencies);


    /**
     * Resolves this configuration. This locates and downloads the files which make up this configuration, and returns
     * a ResolvedConfiguration that may be used to determine information about the resolve (including errors).
     *
     * @return The ResolvedConfiguration object
     */
    ResolvedConfiguration getResolvedConfiguration();

    /**
     * Returns the name of the task that upload the artifacts of this configuration to repositories
     * declared by the user.
     *
     * @see org.gradle.api.tasks.Upload
     */
    String getUploadTaskName();

    /**
     * Returns a {@code TaskDependency} object containing all required dependencies to build the internal dependencies
     * (e.g. project dependencies) belonging to this configuration or to one of its super configurations.
     *
     * @return a TaskDependency object
     */
    TaskDependency getBuildDependencies();

    /**
     * Returns a TaskDependency object containing dependencies on all tasks with the specified name from project
     * dependencies related to this configuration or one of its super configurations.  These other projects may be
     * projects this configuration depends on or projects with a similarly named configuration that depend on this one
     * based on the useDependOn argument.
     *
     * @param useDependedOn if true, add tasks from project dependencies in this configuration, otherwise use projects
     *                      from configurations with the same name that depend on this one.
     * @param taskName name of task to depend on
     * @return the populated TaskDependency object
     */
    TaskDependency getTaskDependencyFromProjectDependency(boolean useDependedOn, final String taskName);

    /**
     * Returns a {@code TaskDependency} object containing all required tasks to build the artifacts
     * belonging to this configuration or to one of its super configurations.
     *
     * @return a task dependency object
     * @deprecated Use {@link PublishArtifactSet#getBuildDependencies()} on {@link #getAllArtifacts()} instead.
     */
    @Deprecated
    TaskDependency getBuildArtifacts();

    /**
     * Gets the set of dependencies directly contained in this configuration
     * (ignoring superconfigurations).
     *
     * @return the set of dependencies
     */
    DependencySet getDependencies();

    /**
     * <p>Gets the complete set of dependencies including those contributed by
     * superconfigurations.</p>
     *
     * @return the (read-only) set of dependencies
     */
    DependencySet getAllDependencies();

    /**
     * <p>Gets the set of dependencies of type T directly contained in this configuration (ignoring superconfigurations).</p>
     * 
     * <p>The returned set is live, in that any future dependencies added to this configuration that match the type will appear in the returned set.</p>
     *
     * @param type the dependency type
     * @param <T> the dependency type
     * @return The (read-only) set.
     * @deprecated Use {@link DependencySet#withType(Class)} on {@link #getDependencies()} instead.
     */
    @Deprecated
    <T extends Dependency> DomainObjectSet<T> getDependencies(Class<T> type);

    /**
     * Gets the set of dependencies of type T for this configuration including those contributed by superconfigurations.
     *
     * <p>The returned set is live, in that any future dependencies added to this configuration that match the type will appear in the returned set.</p>
     * 
     * @param type the dependency type
     * @param <T> the dependency type
     * @return The (read-only) set.
     * @deprecated Use {@link DependencySet#withType(Class)} on {@link #getAllDependencies()} instead.
     */
    @Deprecated
    <T extends Dependency> DomainObjectSet<T> getAllDependencies(Class<T> type);

    /**
     * Adds a dependency to this configuration.
     *
     * @param dependency The dependency to be added.
     * @deprecated Use {@link DependencySet#add(Object)} on {@link #getDependencies()} instead.
     */
    @Deprecated
    void addDependency(Dependency dependency);

    /**
     * Returns the artifacts of this configuration excluding the artifacts of extended configurations.
     * 
     * @return The set.
     */
    PublishArtifactSet getArtifacts();

    /**
     * Returns the artifacts of this configuration including the artifacts of extended configurations.
     * 
     * @return The (read-only) set.
     */
    PublishArtifactSet getAllArtifacts();

    /**
     * Returns the artifacts of this configuration as a {@link FileCollection}, including artifacts of extended
     * configurations.
     *
     * @return the artifact files.
     * @deprecated Use {@link PublishArtifactSet#getFiles()} on {@link #getAllArtifacts()} instead.
     */
    @Deprecated
    FileCollection getAllArtifactFiles();

    /**
     * Returns the exclude rules applied for resolving any dependency of this configuration.
     *
     * @see #exclude(java.util.Map)
     */
    Set<ExcludeRule> getExcludeRules();

    /**
     * Adds an exclude rule to exclude transitive dependencies for all dependencies of this configuration.
     * You can also add exclude rules per-dependency. See {@link ModuleDependency#exclude(java.util.Map)}.
     *
     * @param excludeProperties the properties to define the exclude rule.
     * @return this
     */
    Configuration exclude(Map<String, String> excludeProperties);

    /**
     * Returns all the configurations belonging to the same configuration container as this
     * configuration (including this configuration).
     */
    Set<Configuration> getAll();

    /**
     * Adds an artifact to be published to this configuration.
     *
     * @param artifact The artifact.
     * @return this
     * @deprecated Use {@link PublishArtifactSet#add(Object)} on {@link #getArtifacts()} instead.
     */
    @Deprecated
    Configuration addArtifact(PublishArtifact artifact);

    /**
     * Removes an artifact from the artifacts to be published to this configuration.
     *
     * @param artifact The artifact.
     * @return this
     * @deprecated Use {@link PublishArtifactSet#remove(Object)} on {@link #getArtifacts()} instead.
     */
    @Deprecated
    Configuration removeArtifact(PublishArtifact artifact);

    /**
     * Returns the incoming dependencies of this configuration.
     *
     * @return The incoming dependencies of this configuration. Never null.
     */
    ResolvableDependencies getIncoming();

    /**
     * Creates a copy of this configuration that only contains the dependencies directly in this configuration
     * (without contributions from superconfigurations).  The new configuation will be in the
     * UNRESOLVED state, but will retain all other attributes of this configuration except superconfigurations.
     * {@link #getHierarchy()} for the copy will not include any superconfigurations.
     * @return copy of this configuration
     */
    Configuration copy();

    /**
     * Creates a copy of this configuration that contains the dependencies directly in this configuration
     * and those derived from superconfigurations.  The new configuration will be in the
     * UNRESOLVED state, but will retain all other attributes of this configuration except superconfigurations.
     * {@link #getHierarchy()} for the copy will not include any superconfigurations.
     * @return copy of this configuration
     */
    Configuration copyRecursive();

    /**
     * Creates a copy of this configuration ignoring superconfigurations (see {@link #copy()} but filtering
     * the dependencies using the specified dependency spec.
     *
     * @param dependencySpec filtering requirements
     * @return copy of this configuration
     */
    Configuration copy(Spec<? super Dependency> dependencySpec);

    /**
     * Creates a copy of this configuration with dependencies from superconfigurations (see {@link #copyRecursive()})
     * but filtering the dependencies using the dependencySpec.
     *
     * @param dependencySpec filtering requirements
     * @return copy of this configuration
     */
    Configuration copyRecursive(Spec<? super Dependency> dependencySpec);

    /**
     * Takes a closure which gets coerced into a Spec. Behaves otherwise in the same way as {@link #copy(org.gradle.api.specs.Spec)}
     *
     * @param dependencySpec filtering requirements
     * @return copy of this configuration
     */
    Configuration copy(Closure dependencySpec);

    /**
     * Takes a closure which gets coerced into a Spec. Behaves otherwise in the same way as {@link #copyRecursive(org.gradle.api.specs.Spec)}
     *
     * @param dependencySpec filtering requirements
     * @return copy of this configuration
     */
    Configuration copyRecursive(Closure dependencySpec);
}
