/**
 * Copyright 2016-2017 The Reaktivity Project
 *
 * The Reaktivity Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.reaktivity.reaktive.httpserver;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.rules.RuleChain.outerRule;

import java.net.InetSocketAddress;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

@SuppressWarnings("restriction")
public class MessageFormatIT
{
    private final K3poRule k3po = new K3poRule()
        .setScriptRoot("org/kaazing/specification/http/rfc7230/message.format");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final HttpServerRule server = new HttpServerRule()
            .init(new InetSocketAddress("localhost", 8080), 0);

    @Rule
    public final TestRule chain = outerRule(k3po).around(server).around(timeout);

    @Test
    @Specification({
        "inbound.should.accept.headers/request" })
    public void inboundShouldAcceptHeaders()
            throws Exception
    {
        server.handler("/", exchange ->
        {
            assertEquals("header", exchange.getRequestHeaders().getFirst("some"));
            exchange.sendResponseHeaders(200, 0L);
        });

        k3po.finish();
    }
}
