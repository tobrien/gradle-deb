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

package org.gradle.launcher.daemon.registry;

import org.gradle.cache.DefaultSerializer;
import org.gradle.cache.PersistentStateCache;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.cache.internal.OnDemandFileLock;
import org.gradle.cache.internal.SimpleStateCache;
import org.gradle.messaging.remote.Address;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * Access to daemon registry files. Useful also for testing.
 *
 * @author: Szczepan Faber, created at: 8/18/11
 */
public class PersistentDaemonRegistry implements DaemonRegistry {
    private final SimpleStateCache<DaemonRegistryContent> cache;
    private final File registryFile;

    public PersistentDaemonRegistry(File registryFile, FileLockManager fileLockManager) {
        this.registryFile = registryFile;
        cache = new SimpleStateCache<DaemonRegistryContent>(
                registryFile,
                new OnDemandFileLock(
                        registryFile,
                        "daemon addresses registry",
                        fileLockManager),
                new DefaultSerializer<DaemonRegistryContent>());
    }

    public String toString() {
        return String.format("PersistentDaemonRegistry[file=%s]", registryFile);
    }

    public synchronized List<DaemonStatus> getAll() {
        DaemonRegistryContent content = cache.get();
        if (content == null) {
            //when no daemon process has started yet
            return new LinkedList<DaemonStatus>();
        }
        return content.getStatuses();
    }

    public synchronized List<DaemonStatus> getIdle() {
        List<DaemonStatus> out = new LinkedList<DaemonStatus>();
        List<DaemonStatus> all = getAll();
        for (DaemonStatus d : all) {
            if (d.isIdle()) {
                out.add(d);
            }
        }
        return out;
    }

    public synchronized List<DaemonStatus> getBusy() {
        List<DaemonStatus> out = new LinkedList<DaemonStatus>();
        List<DaemonStatus> all = getAll();
        for (DaemonStatus d : all) {
            if (!d.isIdle()) {
                out.add(d);
            }
        }
        return out;
    }

    public synchronized void remove(final Address address) {
        cache.update(new PersistentStateCache.UpdateAction<DaemonRegistryContent>() {
            public DaemonRegistryContent update(DaemonRegistryContent oldValue) {
                assertCacheNotEmpty(oldValue);
                oldValue.removeStatus(address);
                return oldValue;
            }
        });
    }

    public synchronized void markBusy(final Address address) {
        cache.update(new PersistentStateCache.UpdateAction<DaemonRegistryContent>() {
            public DaemonRegistryContent update(DaemonRegistryContent oldValue) {
                assertCacheNotEmpty(oldValue);
                DaemonStatus status = oldValue.getStatus(address);
                status.setIdle(false);
                return oldValue;
            }
        });
    }

    public synchronized void markIdle(final Address address) {
        cache.update(new PersistentStateCache.UpdateAction<DaemonRegistryContent>() {
            public DaemonRegistryContent update(DaemonRegistryContent oldValue) {
                assertCacheNotEmpty(oldValue);
                oldValue.getStatus(address).setIdle(true);
                return oldValue;
            }
        });
    }

    private void assertCacheNotEmpty(Object value) {
        if (value == null) {
            throw new EmptyRegistryException("Registry is empty!");
        }
    }

    public synchronized void store(final Address address) {
        cache.update(new PersistentStateCache.UpdateAction<DaemonRegistryContent>() {
            public DaemonRegistryContent update(DaemonRegistryContent oldValue) {
                if (oldValue == null) {
                    //it means the registry didn't exist yet
                    oldValue = new DaemonRegistryContent();
                }
                DaemonStatus status = new DaemonStatus(address).setIdle(true);
                oldValue.setStatus(address, status);
                return oldValue;
            }
        });
    }
}
