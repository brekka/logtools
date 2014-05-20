/*
 * Copyright 2013 the original author or authors.
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

package org.brekka.logtools.stash;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Dispatches event messages to logstash using the specified client. Messages will be queued for sending in case
 * the server is temporarily unavailable. A sensible limit is set on the number of messages that can be buffered. If
 * that limit is exceeded, new messages will start being dropped.
 *
 * @author Andrew Taylor (andrew@brekka.org)
 */
class Dispatcher {
    
    private static final boolean DEBUG_ENABLED = "true".equals(System.getProperty("logtools.dispatcher.debug"));
    
    private final ExecutorService executorService;
    
    private final Client client;
    
    /**
     * Maximum time to block the JVM shutdown to clear events.
     */
    private int shutdownDelaySeconds = 10;
    
    public Dispatcher(Client client) {
        this(client, 1000, 4);
    }
    
    public Dispatcher(Client client, int eventBufferSize, final int priority) {
        this.client = client;
        ThreadFactory tf = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "LogStashDispatcher");
                t.setDaemon(true);
                // Slightly below normal
                t.setPriority(priority);
                return t;
            }
        };
        
        // Just one daemon thread.
        executorService = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(eventBufferSize), tf);
        
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // JVM is going down, try to flush as many messages as possible.
                close(true);
            }
        });
    }
    
    /**
     * Close this dispatcher without blocking. Any subsequent call to dispatchMessage will be rejected. 
     */
    public void close() {
        close(false);
    }
    
    protected void close(boolean wait) {
        if (!executorService.isShutdown()) {
            try {
                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        client.close();
                    }
                });
            } catch (RejectedExecutionException e){
                // Leave the socket open and hope the finalizer gets it
                if (DEBUG_ENABLED) {
                    System.err.printf("Dispatcher to '%s' close rejected, must already have been called.%n", client);
                }
            }
            executorService.shutdown();
        }
        if (wait && !executorService.isTerminated()) {
            // The JVM is shutting down. We want to flush as many events as possible before giving up.
            try {
                if (executorService.awaitTermination(shutdownDelaySeconds, TimeUnit.SECONDS)) {
                    if (DEBUG_ENABLED) {
                        System.err.printf("Shutdown of dispatcher to '%s' failed to process all events%n", client);
                    }
                }
            } catch (InterruptedException e) {
                if (DEBUG_ENABLED) {
                    System.err.printf("Dispatcher to '%s' failed to process all events due to interruption%n", client);
                }
            }
        }
    }

    public void dispatchMessage(final String message) {
        try {
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            client.writeEvent(message);
                            // Written successfully
                            return;
                        } catch (Exception e) {
                            client.close();
                            // Make sure to have some kind of delay between attempts. There are situations
                            // where connection failures will be very quick so this avoids thrashing
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e1) {
                                return;
                            }
                        }
                        if (Thread.currentThread().isInterrupted()) {
                            // Thread is interrupted. Exit now
                            return;
                        }
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            if (DEBUG_ENABLED) {
                System.err.printf("Dispatch to '%s' failed for event %s%n", client, message);
                e.printStackTrace();
            }
        }
    }
    
    /**
     * @param shutdownDelaySeconds the shutdownDelaySeconds to set
     */
    public void setShutdownDelaySeconds(int shutdownDelaySeconds) {
        if (shutdownDelaySeconds > 0) {
            this.shutdownDelaySeconds = shutdownDelaySeconds;
        }
    }
}
