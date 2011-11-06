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

package org.gradle.api.internal.artifacts.configurations.conflicts;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.IvyNode;
import org.gradle.api.artifacts.ForcedVersion;

import java.util.Set;

/**
 * Selects the dependency based on the strict strategy configuration (e.g. the forced versions) and
 *
 * by Szczepan Faber, created at: 10/5/11
 */
public class DependencySelector {
    private final Set<ForcedVersion> forcedVersions;

    public DependencySelector(Set<ForcedVersion> forcedVersions) {
        this.forcedVersions = forcedVersions;
    }

    public IvyNode maybeSelect(IvyNode lhs, IvyNode rhs) {
        if (forcedVersions == null) {
            return null;
        }
        for (ForcedVersion d : forcedVersions) {
            ModuleRevisionId forcedId = ModuleRevisionId.newInstance(d.getGroup(), d.getName(), d.getVersion());
            if (forcedId.equals(lhs.getId())) {
                return lhs;
            } else if (forcedId.equals(rhs.getId())) {
                return rhs;
            }
        }
        return null;
    }
}
