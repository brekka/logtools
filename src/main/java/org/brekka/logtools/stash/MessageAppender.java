/**
 * Copyright (c) 2014 Digital Shadows Ltd.
 */
package org.brekka.logtools.stash;

import org.apache.log4j.spi.LoggingEvent;

/**
 * Appender for dispatching log events where the message is already a LogStash json formatter message
 * @author Ben.Gilbert
 */
public class MessageAppender extends Appender {

    @Override
    protected String toJsonString(LoggingEvent event) {
        return String.valueOf(event.getMessage());
    }

}
