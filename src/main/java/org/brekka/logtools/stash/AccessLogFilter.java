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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.MDC;
import org.brekka.logtools.Host;

public class AccessLogFilter implements Filter {

    private String host;

    private int port;

    private int connectionTimeoutMillis;

    private int socketTimeoutMillis;

    private int priority = 4;

    private int eventBufferSize = 1000;
    
    private String localHostName;

    private volatile Dispatcher dispatcher;
    private Host localHost;
    
    private String mdcProperties;
    private volatile Map<String,String> mdcProps;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        host = getParamOrDefault(filterConfig,"host",null);
        port = Integer.valueOf(getParamOrDefault(filterConfig,"port","0"));
        connectionTimeoutMillis = Integer.valueOf(getParamOrDefault(filterConfig,"connectionTimeoutMillis","0"));
        localHostName = getParamOrDefault(filterConfig,"localHostName",null);
        mdcProperties = getParamOrDefault(filterConfig, "mdcProperties", null);
    }
    
    protected String getParamOrDefault(FilterConfig filterConfig, String paramName,String defaultValue){
        String initParameter = filterConfig.getInitParameter(paramName);
        if (initParameter==null){
            return defaultValue;
        }
        return initParameter;
    }



    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse resp = (HttpServletResponse) response;
            log(req, resp, System.nanoTime());
        } catch (Exception e){
            //Logging error doesn't prevent filter chain execution
        }
        chain.doFilter(request, response);
    }



    @Override
    public void destroy() {
        
    }
    
    protected void log(HttpServletRequest req, HttpServletResponse resp, long time) {
        // Log as normal
        initDispatcher();
        initMDCProperties();
        String eventJson = toJsonString(req, resp, time);
        dispatcher.dispatchMessage(eventJson);
    }
    

    /**
     * 
     */
    private void initMDCProperties() {
        if (mdcProps == null) {
            synchronized (this) {
                if (mdcProps == null) {
                    if (mdcProperties !=null && mdcProperties.length()!=0){
                        String[] propList = mdcProperties.split(",");
                        Map<String, String> map = new LinkedHashMap<String, String>(propList.length);
                        for (final String keyValue : propList) {
                            String[] split2 = keyValue.split("=");
                            map.put(split2[0], split2[1]);
                        }
                        mdcProps = map;
                    } else {
                        mdcProps = Collections.emptyMap();
                    }
                }
            }
        }
        
    }



    /**
     * @param req
     * @param resp
     * @param time
     * @return
     */
    protected String toJsonString(HttpServletRequest req, HttpServletResponse resp, long time) {
        StringWriter sw = new StringWriter();
        try (PrintWriter out = new PrintWriter(sw);) {
            out.print('{');
            out.print("\"@fields\": {");
            processFields(req, resp, out);
            out.print("},");
            out.printf("\"@timestamp\": \"%tFT%<tT.%<tLZ\",", System.currentTimeMillis());
            out.printf("\"@source_host\": \"%s\",", localHost.getFqdn());
            out.printf("\"@source_path\": \"%s\",", req.getRequestURI());
            // Make sure last (no trailing comma)
            out.printf("\"@message\": \"%s\"", req.getRequestURL());
            out.print('}');
        }
        return sw.toString();
    }

    /**
     * @param req
     * @param resp
     */
    protected void processFields(HttpServletRequest req, HttpServletResponse resp, PrintWriter out) {
        // Request
        out.printf("\"remote_host\": \"%s\",", req.getRemoteHost());
        out.printf("\"remote_user\": \"%s\",", req.getRemoteUser());
        out.printf("\"query\": \"%s\",", req.getQueryString());
        out.printf("\"uri\": \"%s\",", req.getRequestURI());
        out.printf("\"request_length\": %d,", req.getContentLength());
        out.printf("\"protocol\": \"%s\",", req.getProtocol());
        out.printf("\"method\": \"%s\",", req.getMethod());
        out.printf("\"request_content_type\": \"%s\",", req.getContentType());
        for (Entry<String,String> mdcEntry : mdcProps.entrySet()){
            out.printf("\"%s\": \"%s\",", mdcEntry.getKey(), MDC.get(mdcEntry.getValue()));
        }

        // Response
        out.printf("\"response_length\": %d,", resp.getHeader("Content-Length"));
        out.printf("\"response_content_type\": \"%s\",", resp.getContentType());
        // Last (no trailing comma)
        out.printf("\"status_code\": %d", resp.getStatus());
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



    /**
     * @param mdcProperties the mdcProperties to set
     */
    public void setMdcProperties(String mdcProperties) {
        this.mdcProperties = mdcProperties;
    }

    
}
