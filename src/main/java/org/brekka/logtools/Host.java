package org.brekka.logtools;

import java.net.InetAddress;
import java.net.UnknownHostException;

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

/**
 * Capture details about the host. If the name is not explicitly provided, an attempt will be made to determine the host
 * name automatically. This can be assisted by setting the 'fqdn' system property, otherwise the name of localhost
 * adapter will be used.
 *
 * @author Andrew Taylor (andrew@brekka.org)
 */
public class Host {

    private final String fqdn;

    /**
     * 
     */
    public Host() {
        this(determineHostName());
    }

    public Host(String fqdn) {
        this.fqdn = fqdn;
    }

    /**
     * @return the fqdn
     */
    public String getFqdn() {
        return fqdn;
    }

    private static final String determineHostName() {
        String fqdn = System.getProperty("fqdn");
        if (fqdn != null) {
            try {
                InetAddress iAddress = InetAddress.getLocalHost();
                fqdn = iAddress.getCanonicalHostName();
            } catch (UnknownHostException e) {
                fqdn = "unknown_hostname";
            }
        }
        return fqdn;
    }
}
