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

import static org.junit.Assert.*;

import java.util.LinkedList;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * TODO Description of TCPClientTest
 *
 * @author Andrew Taylor (andrew@brekka.org)
 */
public class TCPClientTest {

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
     * Test method for {@link org.brekka.logtools.stash.TCPClient#writeEvent(java.lang.String)}.
     */
    @Test
    public void testWriteEvent() throws Exception {
        Client client = new TCPClient("localhost", 9033);
        for (int i = 0; i < 10000; i++) {
            client.writeEvent(String.format("{ \"uuid\": \"%s\"}", UUID.randomUUID().toString()));
        }
        Thread.sleep(1000);
        LinkedList<String> messages = mockLogStashServer.getMessages();
        assertEquals(10000, messages.size());
    }

}
