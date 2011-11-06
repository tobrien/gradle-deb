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

import org.gradle.api.Action;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.launcher.daemon.protocol.IoCommand;
import org.gradle.launcher.daemon.protocol.ForwardInput;
import org.gradle.launcher.daemon.protocol.CloseInput;
import org.gradle.messaging.remote.internal.InputForwarder;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;

import java.io.InputStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Eagerly consumes from an input stream, sending line by line ForwardInput
 * commands over the connection and finishing with a CloseInput command.»
 */
public class DaemonClientInputForwarder implements Stoppable {

    public static final int DEFAULT_BUFFER_SIZE = 1024;

    private final Lock lifecycleLock = new ReentrantLock();
    private boolean started;
    private boolean stopped;

    private final InputStream inputStream;
    private final BuildClientMetaData clientMetadata;
    private final Dispatch<? super IoCommand> dispatch;
    private final ExecutorFactory executorFactory;
    private final int bufferSize;

    private InputForwarder forwarder;

    public DaemonClientInputForwarder(InputStream inputStream, BuildClientMetaData clientMetadata, Dispatch<? super IoCommand> dispatch) {
        this(inputStream, clientMetadata, dispatch, new DefaultExecutorFactory(), DEFAULT_BUFFER_SIZE);
    }

    public DaemonClientInputForwarder(InputStream inputStream, BuildClientMetaData clientMetadata, Dispatch<? super IoCommand> dispatch, ExecutorFactory executorFactory, int bufferSize) {
        this.inputStream = inputStream;
        this.clientMetadata = clientMetadata;
        this.dispatch = dispatch;
        this.executorFactory = executorFactory;
        this.bufferSize = bufferSize;
    }

    public DaemonClientInputForwarder start() {
        lifecycleLock.lock();
        try {
            if (started) {
                throw new IllegalStateException("DaemonClientInputForwarder already started");
            }

            Action<String> dispatcher = new Action<String>() {
                public void execute(String input) {
                    dispatch.dispatch(new ForwardInput(clientMetadata, input.getBytes()));
                }
            };

            Runnable onFinish = new Runnable() {
                public void run() {
                    dispatch.dispatch(new CloseInput(clientMetadata));
                }
            };

            forwarder = new InputForwarder(inputStream, dispatcher, onFinish, executorFactory, bufferSize).start();
            started = true;
            return this;
        } finally {
            lifecycleLock.unlock();
        }
    }

    public void stop() {
        lifecycleLock.lock();
        try {
            if (!stopped) {
                forwarder.stop();
                stopped = true;
            }
        } finally {
            lifecycleLock.unlock();
        }
    }
}