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
package org.gradle.launcher.daemon.server.exec;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.launcher.daemon.protocol.CloseInput;
import org.gradle.launcher.daemon.protocol.ForwardInput;
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.messaging.concurrent.StoppableExecutor;
import org.gradle.messaging.dispatch.AsyncReceive;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.util.StdinSwapper;
import org.gradle.util.UncheckedException;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.Callable;

/**
 * Listens for ForwardInput commands during the execution and sends that to a piped input stream
 * that we install.
 */
public class ForwardClientInput implements DaemonCommandAction {
    private static final Logger LOGGER = Logging.getLogger(ForwardClientInput.class);
    private final ExecutorFactory executorFactory;

    public ForwardClientInput(ExecutorFactory executorFactory) {
        this.executorFactory = executorFactory;
    }

    public void execute(final DaemonCommandExecution execution) {
        final PipedOutputStream inputSource = new PipedOutputStream();
        final PipedInputStream replacementStdin;
        try {
            replacementStdin = new PipedInputStream(inputSource);
        } catch (IOException e) {
            throw new GradleException("unable to wire client stdin to daemon stdin", e);
        }

        Dispatch<Object> dispatcher = new Dispatch<Object>() {
            public void dispatch(Object command) {
                if (command instanceof ForwardInput) {
                    try {
                        ForwardInput forwardedInput = (ForwardInput)command;
                        inputSource.write(forwardedInput.getBytes());
                    } catch (IOException e) {
                        LOGGER.warn("received IO exception trying to forward client input", e);
                    }
                } else if (command instanceof CloseInput) {
                    try {
                        inputSource.close();
                    } catch (IOException e) {
                        LOGGER.warn("IO exception closing output stream connected to replacement stdin", e);
                    }
                } else {
                    LOGGER.warn("while listening for IOCommands, received unexpected command: {}", command);
                }
            }
        };

        StoppableExecutor inputReceiverExecuter = executorFactory.create("daemon client input forwarder");
        AsyncReceive<Object> inputReceiver = new AsyncReceive<Object>(inputReceiverExecuter, dispatcher);
        inputReceiver.receiveFrom(execution.getConnection());

        try {
            new StdinSwapper().swap(replacementStdin, new Callable<Void>() {
                public Void call() {
                    execution.proceed();
                    return null;
                }
            });
            replacementStdin.close();
        } catch (Exception e) {
            throw UncheckedException.asUncheckedException(e);
        } finally {
            inputReceiver.requestStop();
        }
    }

}