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

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * TCPClient.
 * 
 * This implementation does a lot of flushing to ensure that an exception is thrown when a message could not be
 * written. As such it is not going to work particularly well in high throughput scenarios.
 * 
 * The goal is to replace this with an alternative solution in the near future. At this time we just want to ensure 
 * messages get written in our low-throughput environment.
 *
 * @author Andrew Taylor (andrew@brekka.org)
 */
class TCPClient implements Client {

    private final SocketAddress socketAddress;
    private int connectionTimeout = 5000;
    private int socketTimeout = 10000;
    
    private Socket socket;
    
    private Writer out;
    
    /**
     * 
     */
    public TCPClient(String address, int port) {
        this.socketAddress = new InetSocketAddress(address, port);
    }
    
    /* (non-Javadoc)
     * @see org.brekka.logtools.stash.Client#writeEvent(java.lang.String)
     */
    @Override
    public synchronized void writeEvent(String line) throws IOException {
        if (out == null) {
            close();
            establish();
        }
        out.write(line);
        out.flush();
        out.write("\n");
        // Might seem inefficient but this ensures we always get an exception if the message could not be written.
        out.flush();
    }

    @Override
    public void close() {
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                // Ignore
            }
            out = null;
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
            socket = null;
        }
    }
    
    /**
     * @param connectionTimeout the connectionTimeout to set
     */
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }
    
    /**
     * @param socketTimeout the socketTimeout to set
     */
    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }
    
    /**
     * @return the socketAddress
     */
    public SocketAddress getSocketAddress() {
        return socketAddress;
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return getSocketAddress().toString();
    }
    
    private void establish() throws IOException {
        socket = new Socket();
        socket.setKeepAlive(true);
        socket.setSoTimeout(socketTimeout);
        socket.setTcpNoDelay(true);
        socket.connect(socketAddress, connectionTimeout);
        socket.shutdownInput();
        out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
    }
}
