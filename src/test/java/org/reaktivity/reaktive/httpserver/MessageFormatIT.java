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
import static org.jmock.lib.script.ScriptedAction.perform;
import static org.junit.rules.RuleChain.outerRule;
import static org.reaktivity.reaktive.httpserver.test.HttpRequestHeadersMatcher.hasRequestHeader;

import java.net.InetSocketAddress;

import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.jmock.lib.concurrent.Synchroniser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;
import org.reaktivity.reaktive.httpserver.test.HttpServerRule;
import org.reaktivity.specification.nukleus.NukleusRule;

import com.sun.net.httpserver.HttpHandler;

@SuppressWarnings("restriction")
public class MessageFormatIT
{
    private final K3poRule k3po = new K3poRule();

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final NukleusRule nukleus = new NukleusRule()
        .directory("target/nukleus-itests")
        .streams("http", "tcp#any")
        .streams("httpserver", "http#tcp")
        .streams("http", "httpserver#http")
        .streams("tcp", "http#httpserver");

    private final HttpServerRule server = new HttpServerRule()
        .directory("target/nukleus-itests")
        .init(new InetSocketAddress("localhost", 8080), 0);

    @Rule
    public final JUnitRuleMockery mockery = new JUnitRuleMockery()
    { {
        setThreadingPolicy(new Synchroniser());
    } };

    @Rule
    public final TestRule chain = outerRule(k3po).around(nukleus).around(server).around(timeout);

    @Test
    @Specification({
        "inbound.should.accept.headers/request" })
    public void inboundShouldAcceptHeaders()
            throws Exception
    {
        // TODO: update HTTP specification K3PO scripts to add "read status"

        HttpHandler handler = mockery.mock(HttpHandler.class);
        mockery.checking(new Expectations()
        { {
            oneOf(handler).handle(with(hasRequestHeader("Some", equal("header"))));
            will(perform("$0.sendResponseHeaders(200, -1L); return;"));
        } });

        server.handler("/", handler);
        k3po.finish();
    }
}
