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
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.brekka.logtools.SourceHost;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Servlet filter used to capture and log request/response access details to the Log4J logger named after this class.
 */
public class AccessLogFilter implements Filter {

    private static final Logger logger = Logger.getLogger(AccessLogFilter.class);

    private FilterConfig config;

    private SourceHost sourceHost;

    private String mdcProperties;

    private Map<String,String> mdcProps;

    private Level priority;

    private ObjectMapper objectMapper;

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        mdcProperties = getParamOrDefault(filterConfig, "mdcProperties", null);
        priority = Level.toLevel(getParamOrDefault(filterConfig, "priority", "DEBUG"));
        sourceHost = new SourceHost();
        initMDCProperties();
        config = filterConfig;
        objectMapper = new ObjectMapper();
        objectMapper.setConfig(objectMapper.getSerializationConfig().withoutFeatures(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        objectMapper.setSerializationInclusion(Include.NON_NULL);
    }

    protected String getParamOrDefault(final FilterConfig filterConfig, final String paramName,final String defaultValue){
        String initParameter = filterConfig.getInitParameter(paramName);
        if (initParameter==null){
            return defaultValue;
        }
        return initParameter;
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } finally {
            try {
                HttpServletRequest req = (HttpServletRequest) request;
                HttpServletResponse resp = (HttpServletResponse) response;
                log(req, resp);
            } catch (Exception e){
                config.getServletContext().log("Failed to log access", e);
            }
        }
    }

    @Override
    public void destroy() {
        // Not needed
    }

    protected void log(final HttpServletRequest req, final HttpServletResponse resp) {
        try {
            ObjectNode node = toObjectNode(req, resp);
            // Log as normal
            String eventJson = objectMapper.writeValueAsString(node);
            logger.log(priority, eventJson);
        } catch (final IOException e) {
            req.getServletContext().log("Failed to write access log", e);
        }
    }

    /**
     * @param req
     * @param resp
     * @return
     */
    protected ObjectNode toObjectNode(final HttpServletRequest req, final HttpServletResponse resp) {
        ObjectNode json = objectMapper.createObjectNode();
        json.putPOJO("@timestamp", new Date());
        json.put("@source_host", sourceHost.getFqdn());
        json.put("@source_path", req.getRequestURI());
        json.put("@message", req.getRequestURL().toString());
        ObjectNode fields = json.putObject("@fields");
        processFields(fields, req, resp);
        return json;
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

    protected void processFields(final ObjectNode json, final HttpServletRequest req, final HttpServletResponse resp) {
        json.put("remote_host", req.getRemoteHost());
        json.put("remote_user", req.getRemoteUser());
        json.put("query", req.getQueryString());
        json.put("uri", req.getRequestURI());
        json.put("request_length", req.getContentLength());
        json.put("protocol", req.getProtocol());
        json.put("method", req.getMethod());
        json.put("request_content_type", req.getContentType());
        json.put("response_length", resp.getHeader("Content-Length"));
        json.put("response_content_type", resp.getContentType());
        json.put("status_code", resp.getStatus());

        for (Entry<String,String> mdcEntry : mdcProps.entrySet()){
            json.put(mdcEntry.getKey(), Objects.toString(MDC.get(mdcEntry.getValue()), null));
        }
    }
}
