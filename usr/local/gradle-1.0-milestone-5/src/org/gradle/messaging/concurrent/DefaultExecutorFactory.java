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

package org.gradle.messaging.concurrent;

import org.gradle.api.logging.Logging;
import org.gradle.messaging.dispatch.DispatchException;
import org.gradle.messaging.dispatch.ExceptionTrackingFailureHandler;
import org.gradle.util.UncheckedException;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultExecutorFactory implements ExecutorFactory, Stoppable {
    private final Set<StoppableExecutorImpl> executors = new CopyOnWriteArraySet<StoppableExecutorImpl>();

    public void stop() {
        try {
            new CompositeStoppable(executors).stop();
        } finally {
            executors.clear();
        }
    }

    public StoppableExecutor create(String displayName) {
        StoppableExecutorImpl executor = new StoppableExecutorImpl(createExecutor(displayName));
        executors.add(executor);
        return executor;
    }

    protected ExecutorService createExecutor(String displayName) {
        return Executors.newCachedThreadPool(new ThreadFactoryImpl(displayName));
    }

    private class StoppableExecutorImpl implements StoppableExecutor {
        private final ExecutorService executor;
        private final ExceptionTrackingFailureHandler failureHandler;
        private final ThreadLocal<Runnable> executing = new ThreadLocal<Runnable>();

        public StoppableExecutorImpl(ExecutorService executor) {
            this.executor = executor;
            failureHandler = new ExceptionTrackingFailureHandler(Logging.getLogger(StoppableExecutorImpl.class));
        }

        public void execute(final Runnable command) {
            executor.execute(new Runnable() {
                public void run() {
                    executing.set(command);
                    try {
                        command.run();
                    } catch (Throwable throwable) {
                        failureHandler.dispatchFailed(command, throwable);
                    } finally {
                        executing.set(null);
                    }
                }
            });
        }

        public void requestStop() {
            executor.shutdown();
        }

        public void stop() {
            stop(Integer.MAX_VALUE, TimeUnit.SECONDS);
        }

        public void stop(int timeoutValue, TimeUnit timeoutUnits) throws IllegalStateException {
            requestStop();
            if (executing.get() != null) {
                throw new IllegalStateException("Cannot stop this executor from an executor thread.");
            }
            try {
                try {
                    if (!executor.awaitTermination(timeoutValue, timeoutUnits)) {
                        executor.shutdownNow();
                        throw new IllegalStateException("Timeout waiting for concurrent jobs to complete.");
                    }
                } catch (InterruptedException e) {
                    throw new UncheckedException(e);
                }
                try {
                    failureHandler.stop();
                } catch (DispatchException e) {
                    throw UncheckedException.asUncheckedException(e.getCause());
                }
            } finally {
                executors.remove(this);
            }
        }
    }

    private static class ThreadFactoryImpl implements ThreadFactory {
        private final AtomicLong counter = new AtomicLong();
        private final String displayName;

        public ThreadFactoryImpl(String displayName) {
            this.displayName = displayName;
        }

        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            long count = counter.incrementAndGet();
            if (count == 1) {
                thread.setName(displayName);
            } else {
                thread.setName(String.format("%s Thread %s", displayName, count));
            }
            return thread;
        }
    }
}
