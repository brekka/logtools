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
                close();
            }
        });
    }
    
    public void close() {
        try {
            executorService.submit(new Runnable() {
                
                @Override
                public void run() {
                    client.close();
                }
            });
        } catch (RejectedExecutionException e){
            //Leave the socket open and hope the finalizer gets it
        }
        if (!executorService.isShutdown()) {
            executorService.shutdown();
            try {
                executorService.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // Ignore
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

}
