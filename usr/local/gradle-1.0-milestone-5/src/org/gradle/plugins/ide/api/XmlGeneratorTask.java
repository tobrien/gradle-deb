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
package org.gradle.plugins.ide.api;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.XmlProvider;
import org.gradle.api.internal.XmlTransformer;
import org.gradle.plugins.ide.internal.generator.generator.PersistableConfigurationObject;
import org.gradle.plugins.ide.internal.generator.generator.PersistableConfigurationObjectGenerator;
import org.gradle.util.DeprecationLogger;

/**
 * A convenience superclass for those tasks which generate XML configuration files from a domain object of type T.
 *
 * @param <T> The domain object type.
 */
public abstract class XmlGeneratorTask<T extends PersistableConfigurationObject> extends GeneratorTask<T> {
    private final XmlTransformer xmlTransformer = new XmlTransformer();

    public XmlGeneratorTask() {
        generator = new PersistableConfigurationObjectGenerator<T>() {
            public T create() {
                return XmlGeneratorTask.this.create();
            }

            public void configure(T object) {
                XmlGeneratorTask.this.configure(object);
            }
        };
    }

    protected XmlTransformer getXmlTransformer() {
        return xmlTransformer;
    }

    protected abstract void configure(T object);

    protected abstract T create();

    /**
     * Deprecated. Moved to the relevant type. Where? For starters, see examples in {@link org.gradle.plugins.ide.idea.model.IdeaProject} or
     * {@link org.gradle.plugins.ide.eclipse.model.EclipseProject}.
     * <p>
     * Adds a closure to be called when the XML document has been created. The XML is passed to the closure as a
     * parameter in form of a {@link org.gradle.api.XmlProvider}. The closure can modify the XML before
     * it is written to the output file.
     *
     * @param closure The closure to execute when the XML has been created.
     */
    @Deprecated
    public void withXml(Closure closure) {
        DeprecationLogger.nagUserWith("<someIdeTask>.withXml is deprecated! Moved to the relevant model object of eclipse/idea.\n"
                + "As a starting point, refer to the dsl guide for IdeaProject or EclipseProject.\n"
                + "For example, ideaProject.withXml was changed to idea.project.ipr.withXml");
        xmlTransformer.addAction(closure);
    }

    /**
     * Deprecated. Moved to the relevant type. Where? For starters, see examples in {@link org.gradle.plugins.ide.idea.model.IdeaProject} or
     * {@link org.gradle.plugins.ide.eclipse.model.EclipseProject}.
     * <p>
     * Adds an action to be called when the XML document has been created. The XML is passed to the action as a
     * parameter in form of a {@link org.gradle.api.XmlProvider}. The action can modify the XML before
     * it is written to the output file.
     *
     * @param action The action to execute when the IPR XML has been created.
     */
    @Deprecated
    public void withXml(Action<? super XmlProvider> action) {
        DeprecationLogger.nagUserWith("<someIdeTask>.withXml is deprecated! Moved to the relevant model object of eclipse/idea.\n"
                + "As a starting point, refer to the dsl guide for IdeaProject or EclipseProject.\n"
                + "For example, ideaProject.withXml was changed to idea.project.ipr.withXml");
        xmlTransformer.addAction(action);
    }
}
