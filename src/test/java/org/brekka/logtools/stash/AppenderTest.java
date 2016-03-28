/**
 * Copyright (c) 2016 Digital Shadows Ltd.
 */
package org.brekka.logtools.stash;

import static org.junit.Assert.assertEquals;

import java.util.Date;
import java.util.LinkedList;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 *
 * @author ataylor
 */
public class AppenderTest extends Appender {
    private MockLogStashServer mockLogStashServer;

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        mockLogStashServer = new MockLogStashServer();
    }

    @After
    public void tearDown() throws Exception {
        mockLogStashServer.close();
    }

    /**
     * Test method for {@link org.brekka.logtools.stash.Appender#append(org.apache.log4j.spi.LoggingEvent)}.
     */
    @Test
    public void appendLoggingEvent() throws Exception {
        Appender appender = new Appender();
        appender.setHost("localhost");
        appender.setPort(9033);
        appender.setApplication("test");
        appender.setName("appender");
        Logger logger = Logger.getRootLogger();
        RuntimeException ex = new RuntimeException();
        long now = System.currentTimeMillis();
        LoggingEvent event = new LoggingEvent(AppenderTest.class.getName(), logger, now, Level.INFO, "Message", ex);
        for (int i = 0; i < 10; i++) {
            appender.append(event);
        }
        Thread.sleep(1000);
        LinkedList<String> messages = mockLogStashServer.getMessages();
        assertEquals(10, messages.size());
        String top = messages.get(0);
        ObjectMapper om = new ObjectMapper();
        ObjectNode json = om.readValue(top, ObjectNode.class);
        assertEquals(om.readValue("\"" + json.get("@timestamp").asText() + "\"", Date.class), new Date(now));
        assertEquals(json.get("@source_path").asText(), logger.getName());
        assertEquals(json.get("@message").asText(), "Message");
        assertEquals(json.get("@fields").get("priority").asText(), Level.INFO.toString());
    }
}
