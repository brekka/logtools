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

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test the server becoming unavailable and then resuming
 *
 * @author Andrew Taylor (andrew@brekka.org)
 */
public class DispatcherResilienceTest {


    /**
     * Test method for {@link org.brekka.logtools.stash.TCPClient#writeEvent(java.lang.String)}.
     */
    @Test
    public void testWriteEvent() throws Exception {
        MockLogStashServer initialMockLogStashServer = new MockLogStashServer();
        MockLogStashServer mockLogStashServer = initialMockLogStashServer;
        Client client = new TCPClient("localhost", 9033);
        Dispatcher dispatcher = new Dispatcher(client, 1000, 4);
        int totalMessages = 1000;
        Set<String> messages = new HashSet<>();
        for (int i = 0; i < totalMessages; i++) {
            if (i == 200) {
                initialMockLogStashServer.close();
            } else if (i == 400) {
                mockLogStashServer = new MockLogStashServer();
            }
            String message = String.format("{ 'log-event': %d, 'uuid': %s", i, UUID.randomUUID());
            dispatcher.dispatchMessage(message);
            messages.add(message);
            Thread.sleep(2);
        }
        Thread.sleep(2000);
        mockLogStashServer.close();
        messages.removeAll(initialMockLogStashServer.getMessages());
        messages.removeAll(mockLogStashServer.getMessages());
        assertEquals(Collections.emptySet(), messages);
    }

}
