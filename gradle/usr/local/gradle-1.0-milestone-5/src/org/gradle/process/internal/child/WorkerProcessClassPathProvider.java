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

package org.gradle.process.internal.child;

import org.gradle.api.Action;
import org.gradle.api.internal.ClassPathProvider;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.process.internal.launcher.BootstrapClassLoaderWorker;
import org.gradle.process.internal.launcher.GradleWorkerMain;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class WorkerProcessClassPathProvider implements ClassPathProvider {
    private final CacheRepository cacheRepository;
    private final ModuleRegistry moduleRegistry;
    private final Object lock = new Object();
    private Set<File> workerClassPath;

    public WorkerProcessClassPathProvider(CacheRepository cacheRepository, ModuleRegistry moduleRegistry) {
        this.cacheRepository = cacheRepository;
        this.moduleRegistry = moduleRegistry;
    }

    public Set<File> findClassPath(String name) {
        if (name.equals("WORKER_PROCESS")) {
            // TODO - split out a logging project and use its classpath, instead of hardcoding logging dependencies here
            Set<File> classpath = new LinkedHashSet<File>();
            classpath.addAll(moduleRegistry.getModule("gradle-core").getImplementationClasspath());
            classpath.addAll(moduleRegistry.getModule("gradle-cli").getImplementationClasspath());
            classpath.addAll(moduleRegistry.getExternalModule("slf4j-api").getClasspath());
            classpath.addAll(moduleRegistry.getExternalModule("logback-classic").getClasspath());
            classpath.addAll(moduleRegistry.getExternalModule("logback-core").getClasspath());
            classpath.addAll(moduleRegistry.getExternalModule("jul-to-slf4j").getClasspath());
            return classpath;
        }
        if (name.equals("WORKER_MAIN")) {
            synchronized (lock) {
                if (workerClassPath == null) {
                    PersistentCache cache = cacheRepository.cache("workerMain").withInitializer(new CacheInitializer()).open();
                    workerClassPath = Collections.singleton(classesDir(cache));
                }
                return workerClassPath;
            }
        }

        return null;
    }

    private static File classesDir(PersistentCache cache) {
        return new File(cache.getBaseDir(), "classes");
    }

    private static class CacheInitializer implements Action<PersistentCache> {
        public void execute(PersistentCache cache) {
            File classesDir = classesDir(cache);
            for (Class<?> aClass : Arrays.asList(GradleWorkerMain.class, BootstrapClassLoaderWorker.class)) {
                String fileName = aClass.getName().replace('.', '/') + ".class";
                GFileUtils.copyURLToFile(WorkerProcessClassPathProvider.class.getClassLoader().getResource(fileName),
                        new File(classesDir, fileName));
            }
        }
    }

}
