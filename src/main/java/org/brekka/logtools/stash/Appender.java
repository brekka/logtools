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
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.MDC;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;
import org.brekka.logtools.SourceHost;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Log4J appender for writing events to LogStash via the TCP input.
 *
 * @author Andrew Taylor (andrew@brekka.org)
 */
public class Appender extends AppenderSkeleton {

    /**
     * The logging server host name
     */
    private String host;

    /**
     * The logging server listening port.
     */
    private int port;

    private int connectionTimeoutMillis;

    private int socketTimeoutMillis;

    private int priority = 4;

    private int eventBufferSize = 1000;

    /**
     * The name of the application sending the events
     */
    private String application;

    /**
     * Where the message should appear to come from (usually fqdn of the host).
     */
    private String sourceHostName;

    private volatile Dispatcher dispatcher;
    private SourceHost sourceHost;

    private String mdcProperties;
    private volatile Map<String,String> mdcProps;

    private ObjectMapper objectMapper;

    /*
     * (non-Javadoc)
     *
     * @see org.apache.log4j.Appender#close()
     */
    @Override
    public void close() {
        if (dispatcher != null) {
            dispatcher.close();
        }
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * @param host the host to set
     */
    public void setHost(final String host) {
        this.host = host;
    }

    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * @param port
     *            the port to set
     */
    public void setPort(final int port) {
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
    public void setConnectionTimeoutMillis(final int connectionTimeoutMillis) {
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
    public void setSocketTimeoutMillis(final int socketTimeoutMillis) {
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
    public void setEventBufferSize(final int eventBufferSize) {
        this.eventBufferSize = eventBufferSize;
    }

    /**
     * @return the application
     */
    public String getApplication() {
        return application;
    }

    /**
     * @param application
     *            the application to set
     */
    public void setApplication(final String application) {
        this.application = application;
    }

    /**
     * @return the priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * @param priority the priority to set
     */
    public void setPriority(final int priority) {
        this.priority = priority;
    }

    /**
     * @return the sourceHostName
     * @deprecated use getSourceHostName() instead
     */
    @Deprecated
    public String getLocalHostName() {
        return sourceHostName;
    }

    /**
     * @param sourceHostName the sourceHostName to set
     * @deprecated use setSourceHostName() instead
     */
    @Deprecated
    public void setLocalHostName(final String localHostName) {
        this.sourceHostName = localHostName;
    }

    /**
     * @return the sourceHostName
     */
    public String getSourceHostName() {
        return sourceHostName;
    }

    /**
     * @param sourceHostName the sourceHostName to set
     */
    public void setSourceHostName(final String sourceHostName) {
        this.sourceHostName = sourceHostName;
    }

    @Override
    public boolean requiresLayout() {
        return false;
    }

    @Override
    protected void append(final LoggingEvent event) {
        initDispatcher();
        initMDCProperties();
        String eventJson = toJsonString(event);
        dispatcher.dispatchMessage(eventJson);
    }

    protected String toJsonString(final LoggingEvent event) {
        try {
            ObjectNode node = toObjectNode(event);
            String eventJson = objectMapper.writeValueAsString(node);
            return eventJson;
        } catch (final IOException e) {
            throw new IllegalStateException("Unable to append event", e);
        }
    }

    /**
     * @param req
     * @param resp
     * @return
     */
    protected ObjectNode toObjectNode(final LoggingEvent event) {
        ObjectNode json = objectMapper.createObjectNode();
        json.putPOJO("@timestamp", new Date(event.getTimeStamp()));
        json.put("@source_host", sourceHost.getFqdn());
        json.put("@source_path", event.getLoggerName());
        json.put("@message", Objects.toString(event.getMessage(), null));
        ObjectNode fields = json.putObject("@fields");
        processFields(fields, event);
        return json;
    }

    protected void processFields(final ObjectNode json, final LoggingEvent event) {
        json.put("logger_name", event.getLoggerName());
        json.put("thread", event.getThreadName());
        json.put("priority", Objects.toString(event.getLevel(), null));
        if (application != null) {
            json.put("application", application);
        }
        if (event.getThrowableInformation() != null) {
            json.put("stack_trace", formatStackTrace(event));
        }
        for (Entry<String,String> mdcEntry : mdcProps.entrySet()){
            json.put(mdcEntry.getKey(), Objects.toString(MDC.get(mdcEntry.getValue()), null));
        }
    }

    /**
     * @param event
     * @return
     */
    protected String formatStackTrace(final LoggingEvent event) {
        ThrowableInformation throwableInformation = event.getThrowableInformation();
        Throwable throwable = throwableInformation.getThrowable();
        StringWriter sw = new StringWriter();
        try (PrintWriter out = new PrintWriter(sw);) {
            throwable.printStackTrace(out);
        }
        String trace = sw.toString();
        trace = trace.replaceAll("\n", "\\\\n");
        trace = trace.replaceAll("\t", "\\\\t");
        return trace;
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
                    if (sourceHostName != null) {
                        sourceHost = new SourceHost(sourceHostName);
                    } else {
                        sourceHost = new SourceHost();
                    }
                    objectMapper = new ObjectMapper();
                    objectMapper.setConfig(objectMapper.getSerializationConfig().withoutFeatures(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
                    objectMapper.setSerializationInclusion(Include.NON_NULL);
                }
            }
        }
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
                        Map<String, String> map = new LinkedHashMap<>(propList.length);
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
     * @param mdcProperties the mdcProperties to set
     */
    public void setMdcProperties(final String mdcProperties) {
        this.mdcProperties = mdcProperties;
    }
}
