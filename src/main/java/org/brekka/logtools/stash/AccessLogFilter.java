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

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.brekka.logtools.SourceHost;

public class AccessLogFilter implements Filter {

    private static final Logger logger = Logger.getLogger(AccessLogFilter.class);
    
    private SourceHost sourceHost;
    
    private String mdcProperties;
    
    private Map<String,String> mdcProps;

    private Level priority;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        mdcProperties = getParamOrDefault(filterConfig, "mdcProperties", null);
        priority = Level.toLevel(getParamOrDefault(filterConfig, "priority", "DEBUG"));
        sourceHost = new SourceHost();
        initMDCProperties();
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
            chain.doFilter(request, response);
        } finally {
            try {
                HttpServletRequest req = (HttpServletRequest) request;
                HttpServletResponse resp = (HttpServletResponse) response;
                log(req, resp, System.nanoTime());
            } catch (Exception e){
                //Logging error doesn't prevent filter chain execution
            }
        }
    }

    @Override
    public void destroy() {
        // Not needed
    }
    
    protected void log(HttpServletRequest req, HttpServletResponse resp, long time) {
        // Log as normal
        String eventJson = toJsonString(req, resp, time);
        logger.log(priority, eventJson);
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
            out.printf("\"@source_host\": \"%s\",", sourceHost.getFqdn());
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
}
