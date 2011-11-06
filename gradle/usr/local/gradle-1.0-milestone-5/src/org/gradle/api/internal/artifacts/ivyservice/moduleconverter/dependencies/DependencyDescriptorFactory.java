/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleDependency;

/**
 * @author Hans Dockter
 */
public interface DependencyDescriptorFactory {
    String PROJECT_PATH_KEY = "org.gradle.projectPath";

    /** Adds a dependency descriptor, using information in the configuration object to work around ivy limitations */
    DefaultDependencyDescriptor addDependencyDescriptor(Configuration configuration, DefaultModuleDescriptor moduleDescriptor, ModuleDependency dependency);

    /** Adds a dependency descriptor where no configuration object is available */
    DefaultDependencyDescriptor addDependencyDescriptor(String configuration, DefaultModuleDescriptor moduleDescriptor, ModuleDependency dependency);

    ModuleRevisionId createModuleRevisionId(ModuleDependency dependency);
}
