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
package org.gradle.launcher.daemon.client;

import org.gradle.api.internal.project.DefaultServiceRegistry;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.cache.internal.DefaultFileLockManager;
import org.gradle.cache.internal.DefaultProcessMetaDataProvider;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.PersistentDaemonRegistry;
import org.gradle.launcher.daemon.server.DaemonIdleTimeout;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.os.*;
import org.gradle.os.jna.NativeEnvironment;

import java.io.File;

/**
 * Takes care of instantiating and wiring together the services required by the daemon client.
 */
public class DaemonClientServices extends DefaultServiceRegistry {
    private final ServiceRegistry loggingServices;
    private final File userHomeDir;
    private final int idleTimeout;

    public DaemonClientServices(ServiceRegistry loggingServices, File userHomeDir) {
        this(loggingServices, userHomeDir, DaemonIdleTimeout.DEFAULT_IDLE_TIMEOUT);
    }

    public DaemonClientServices(ServiceRegistry loggingServices, File userHomeDir, int idleTimeout) {
        this.loggingServices = loggingServices;
        this.userHomeDir = userHomeDir;
        this.idleTimeout = idleTimeout;
    }

    protected ProcessEnvironment createProcessEnvironment() {
        return NativeEnvironment.current();
    }
    
    protected DaemonDir createDaemonDir() {
        return new DaemonDir(userHomeDir, get(ProcessEnvironment.class));
    }

    protected FileLockManager createFileLockManager() {
        return new DefaultFileLockManager(new DefaultProcessMetaDataProvider(get(ProcessEnvironment.class)));
    }
    
    protected DaemonRegistry createDaemonRegistry() {
        return new PersistentDaemonRegistry(get(DaemonDir.class).getRegistry(), get(FileLockManager.class));
    }

    protected DaemonConnector createDaemonConnector() {
        return new ExternalDaemonConnector(get(DaemonRegistry.class), userHomeDir, idleTimeout);
    }
    
    protected DaemonClient createDaemonClient() {
        return new DaemonClient(get(DaemonConnector.class), new GradleLauncherMetaData(), loggingServices.get(OutputEventListener.class));
    }
}
