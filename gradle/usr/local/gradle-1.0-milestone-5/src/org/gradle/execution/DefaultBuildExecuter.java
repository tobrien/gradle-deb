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
package org.gradle.execution;

import org.gradle.api.internal.GradleInternal;
import org.gradle.util.GUtil;

import java.util.List;

public class DefaultBuildExecuter implements BuildExecuter {
    private final List<BuildConfigurationAction> configurationActions;
    private final List<BuildExecutionAction> executionActions;
    private GradleInternal gradle;
    private String displayName;

    public DefaultBuildExecuter(Iterable<? extends BuildConfigurationAction> configurationActions, Iterable<? extends BuildExecutionAction> executionActions) {
        this.configurationActions = GUtil.toList(configurationActions);
        this.executionActions = GUtil.toList(executionActions);
    }

    public void select(GradleInternal gradle) {
        this.gradle = gradle;
        configure(0);
    }

    private void configure(final int index) {
        if (index >= configurationActions.size()) {
            return;
        }
        configurationActions.get(index).configure(new BuildExecutionContext() {
            public void setDisplayName(String displayName) {
                DefaultBuildExecuter.this.displayName = displayName;
            }

            public GradleInternal getGradle() {
                return gradle;
            }

            public void proceed() {
                configure(index + 1);
            }
        });
    }

    public String getDisplayName() {
        return displayName;
    }

    public void execute() {
        execute(0);
    }

    private void execute(final int index) {
        if (index >= executionActions.size()) {
            return;
        }
        executionActions.get(index).execute(new BuildExecutionContext() {
            public GradleInternal getGradle() {
                return gradle;
            }

            public void setDisplayName(String displayName) {
                DefaultBuildExecuter.this.displayName = displayName;
            }

            public void proceed() {
                execute(index + 1);
            }
        });
    }
}
