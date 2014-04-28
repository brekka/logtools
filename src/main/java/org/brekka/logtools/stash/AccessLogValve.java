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

import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.brekka.logtools.Host;

/**
 * Extension to the access log valve that will attempt to write each event to a remote logstash server (via TCP input).
 * 
 * @author Andrew Taylor (andrew@brekka.org)
 */
public class AccessLogValve extends org.apache.catalina.valves.AccessLogValve {

    private String host;

    private int port;

    private int connectionTimeoutMillis;

    private int socketTimeoutMillis;

    private int priority = 4;

    private int eventBufferSize = 1000;
    
    private String localHostName;

    private volatile Dispatcher dispatcher;
    private Host localHost;

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.catalina.valves.AccessLogValve#log(org.apache.catalina.connector.Request,
     * org.apache.catalina.connector.Response, long)
     */
    @Override
    public void log(Request request, Response response, long time) {
        // Log as normal
        super.log(request, response, time);
        initDispatcher();
        String eventJson = toJsonString(request, response, time);
        dispatcher.dispatchMessage(eventJson);
    }
    


    /**
     * @param request
     * @param response
     * @param time
     * @return
     */
    protected String toJsonString(Request request, Response response, long time) {
        StringWriter sw = new StringWriter();
        try (PrintWriter out = new PrintWriter(sw);) {
            out.print('{');
            out.print("\"@fields\": {");
            processFields(request, response, out);
            out.print("},");
            out.printf("\"@timestamp\": \"%tFT%<tT.%<tLZ\",", System.currentTimeMillis());
            out.printf("\"@source_host\": \"%s\",", localHost.getFqdn());
            out.printf("\"@source_path\": \"%s\",", request.getRequestURI());
            // Make sure last (no trailing comma)
            out.printf("\"@message\": \"%s\"", request.getRequestURL());
            out.print('}');
        }
        return sw.toString();
    }

    /**
     * @param request
     * @param response
     */
    protected void processFields(Request request, Response response, PrintWriter out) {
        // Request
        out.printf("\"remote_host\": \"%s\",", request.getRemoteHost());
        out.printf("\"remote_user\": \"%s\",", request.getRemoteUser());
        out.printf("\"query\": \"%s\",", request.getQueryString());
        out.printf("\"uri\": \"%s\",", request.getRequestURI());
        out.printf("\"request_length\": %d,", request.getContentLength());
        out.printf("\"protocol\": \"%s\",", request.getProtocol());
        out.printf("\"method\": \"%s\",", request.getMethod());
        out.printf("\"request_content_type\": \"%s\",", request.getContentType());

        // Response
        out.printf("\"response_length\": %d,", response.getContentLength());
        out.printf("\"response_content_type\": \"%s\",", response.getContentType());
        // Last (no trailing comma)
        out.printf("\"status_code\": %d", response.getStatus());
    }

    /**
     * 
     */
    private void initDispatcher() {
        if (dispatcher == null) {
            synchronized (this) {
                if (dispatcher == null) {
                    TCPClient client = new TCPClient(host, port);
                    client.setConnectionTimeout(connectionTimeoutMillis);
                    client.setSocketTimeout(socketTimeoutMillis);
                    dispatcher = new Dispatcher(client, eventBufferSize, priority);
                    if (localHostName != null) {
                        localHost = new Host(localHostName);
                    } else {
                        localHost = new Host();
                    }
                }
            }
        }
    }

    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * @param host
     *            the host to set
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * @param port
     *            the port to set
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * @return the connectionTimeoutMillis
     */
    public int getConnectionTimeoutMillis() {
        return connectionTimeoutMillis;
    }

    /**
     * @param connectionTimeoutMillis
     *            the connectionTimeoutMillis to set
     */
    public void setConnectionTimeoutMillis(int connectionTimeoutMillis) {
        this.connectionTimeoutMillis = connectionTimeoutMillis;
    }

    /**
     * @return the socketTimeoutMillis
     */
    public int getSocketTimeoutMillis() {
        return socketTimeoutMillis;
    }

    /**
     * @param socketTimeoutMillis
     *            the socketTimeoutMillis to set
     */
    public void setSocketTimeoutMillis(int socketTimeoutMillis) {
        this.socketTimeoutMillis = socketTimeoutMillis;
    }

    /**
     * @return the eventBufferSize
     */
    public int getEventBufferSize() {
        return eventBufferSize;
    }

    /**
     * @param eventBufferSize
     *            the eventBufferSize to set
     */
    public void setEventBufferSize(int eventBufferSize) {
        this.eventBufferSize = eventBufferSize;
    }

    /**
     * @return the priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * @param priority
     *            the priority to set
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * @return the localHostName
     */
    public String getLocalHostName() {
        return localHostName;
    }

    /**
     * @param localHostName the localHostName to set
     */
    public void setLocalHostName(String localHostName) {
        this.localHostName = localHostName;
    }
}
