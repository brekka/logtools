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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.SocketFactory;

import org.apache.log4j.net.SocketServer;

/**
 * TODO Description of MockLogStashServer
 *
 * @author Andrew Taylor (andrew@brekka.org)
 */
public class MockLogStashServer implements Runnable {
    
    private ServerSocket serverSocket;
    
    private Socket currentSocket;
    
    private ExecutorService executorService = Executors.newFixedThreadPool(2);
    
    private LinkedList<String> messages = new LinkedList<>();
    
    private boolean shutdown = false;
    /**
     * 
     */
    public MockLogStashServer() throws Exception {
        serverSocket = new ServerSocket(9033);
        executorService.submit(this);
    }
    
    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {
        try {
            final Socket socket = serverSocket.accept();
            currentSocket = socket;
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    handleSocket(socket);
                }
            });
        } catch (IOException e) {
            // Socket closed
        }
        executorService.submit(this);
    }
    
    protected void handleSocket(Socket socket) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            while (!shutdown && null != (line = br.readLine())) {
                messages.add(line);
            }
            socket.close();
        } catch (IOException e) {
            
        }
    }
    
    /**
     * @return the messages
     */
    public LinkedList<String> getMessages() {
        return messages;
    }
    
    public void close() {
        shutdown = true;
        try {
            serverSocket.close();
        } catch (IOException e) {
            throw new RuntimeException("IO", e);
        }
        try {
            currentSocket.shutdownInput();
            currentSocket.shutdownOutput();
            currentSocket.close();
        } catch (IOException e) {
            throw new RuntimeException("IO", e);
        }
        executorService.shutdown();
    }
}
