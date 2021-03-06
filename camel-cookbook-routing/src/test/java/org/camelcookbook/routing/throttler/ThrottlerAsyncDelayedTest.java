/*
 * Copyright (C) Scott Cranton and Jakub Korab
 * https://github.com/CamelCookbook
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.camelcookbook.routing.throttler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ThreadPoolProfileBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThrottlerAsyncDelayedTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ThrottlerAsyncDelayedTest.class);

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        ThreadPoolProfileBuilder builder = new ThreadPoolProfileBuilder("myThrottler");
        builder.maxQueueSize(5);
        context.getExecutorServiceManager().registerThreadPoolProfile(builder.build());

        return new ThrottlerAsyncDelayedRouteBuilder();
    }

    @Override
    protected int getShutdownTimeout() {
        // tell CamelTestSupport to shutdown in 1 second versus default of 10
        // expect several in-flight messages that we don't care about
        return 1;
    }

    @Test
    public void testAsyncDelayedThrottle() throws Exception {
        final int throttleRate = 5;
        final int messageCount = throttleRate + 2;

        getMockEndpoint("mock:unthrottled").expectedMessageCount(messageCount);
        getMockEndpoint("mock:throttled").expectedMessageCount(throttleRate);
        getMockEndpoint("mock:after").expectedMessageCount(throttleRate);

        ExecutorService executor = Executors.newFixedThreadPool(messageCount);

        // Send the message on separate threads as sendBody will block on the throttler
        final AtomicInteger threadCount = new AtomicInteger(0);
        for (int i = 0; i < messageCount; i++) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    template.sendBody("direct:start", "Camel Rocks");

                    final int threadId = threadCount.incrementAndGet();
                    LOG.info("Thread {} finished", threadId);
                }
            });
        }

        assertMockEndpointsSatisfied();

        LOG.info("Threads completed {} of {}", threadCount.get(), messageCount);
        //assertEquals("Threads completed should equal throttle rate", throttleRate, threadCount.get());

        executor.shutdownNow();
    }
}
