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

import java.io.IOException;
import java.net.InetSocketAddress;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;

@SuppressWarnings("restriction")
public final class HttpServerRule implements TestRule
{
    private HttpServer server;

    public HttpServerRule init(
        InetSocketAddress address,
        int backlog)
    {
        try
        {
            this.server = HttpServer.create(address, backlog);
        }
        catch (IOException ex)
        {
            throw new RuntimeException(ex);
        }
        return this;
    }

    public HttpServerRule handler(
        String path,
        HttpHandler handler)
    {
        HttpServer server = serverAfterInit();
        server.createContext(path, handler);
        return this;
    }

    @Override
    public Statement apply(
        Statement base,
        Description description)
    {
        return new Statement()
        {
            @Override
            public void evaluate() throws Throwable
            {
                final HttpServer server = serverAfterInit();
                try
                {
                    server.start();
                    base.evaluate();
                }
                finally
                {
                    server.stop(0);
                }
            }
        };
    }

    private HttpServer serverAfterInit()
    {
        if (server == null)
        {
            throw new IllegalStateException("not initialized");
        }

        return server;
    }
}
