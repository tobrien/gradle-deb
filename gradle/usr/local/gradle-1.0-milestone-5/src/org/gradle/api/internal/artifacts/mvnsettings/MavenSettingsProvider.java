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

package org.gradle.api.internal.artifacts.mvnsettings;

import org.gradle.util.SystemProperties;

import java.io.File;

/**
* @author Szczepan Faber, created at: 3/30/11
*/
public class MavenSettingsProvider {
    public File getUserSettingsFile() {
        File m2Dir = getUserMavenDir();
        return new File(m2Dir, "settings.xml");
    }

    public File getUserMavenDir() {
        File userHome = new File(SystemProperties.getUserHome());
        return new File(userHome, ".m2");
    }

    public File getLocalMavenRepository() {
        File m2Dir = getUserMavenDir();
        return new File(m2Dir, "repository");
    }
}