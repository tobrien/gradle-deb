/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.tasks.scala

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.project.IsolatedAntBuilder
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.scala.ScalaCompileOptions
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class AntScalaCompiler implements ScalaCompiler {
    private static Logger logger = LoggerFactory.getLogger(AntScalaCompiler)

    private final IsolatedAntBuilder antBuilder
    private final Iterable<File> bootclasspathFiles
    private final Iterable<File> extensionDirs
    FileCollection source
    File destinationDir
    Iterable<File> classpath
    Iterable<File> scalaClasspath
    ScalaCompileOptions scalaCompileOptions = new ScalaCompileOptions()
    
    def AntScalaCompiler(IsolatedAntBuilder antBuilder) {
        this.antBuilder = antBuilder
        this.bootclasspathFiles = []
        this.extensionDirs = []
    }

    def AntScalaCompiler(IsolatedAntBuilder antBuilder, Iterable<File> bootclasspathFiles, Iterable<File> extensionDirs) {
        this.antBuilder = antBuilder
        this.bootclasspathFiles = bootclasspathFiles
        this.extensionDirs = extensionDirs
    }

    WorkResult execute() {
        Map options = ['destDir': destinationDir] + scalaCompileOptions.optionMap()
        String taskName = scalaCompileOptions.useCompileDaemon ? 'fsc' : 'scalac'

        antBuilder.withClasspath(scalaClasspath).execute { ant ->
            taskdef(resource: 'scala/tools/ant/antlib.xml')

            "${taskName}"(options) {
                source.addToAntBuilder(ant, 'src', FileCollection.AntType.MatchingTask)
                bootclasspathFiles.each {file ->
                    bootclasspath(location: file)
                }
                extensionDirs.each {dir ->
                    extdirs(location: dir)
                }
                classpath(location: destinationDir)
                classpath.each {file ->
                    classpath(location: file)
                }
            }
        }

        return { true } as WorkResult
    }

}
