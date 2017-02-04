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
package org.reaktivity.reaktive.httpserver.internal;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.reaktivity.nukleus.Configuration;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.spi.HttpServerProvider;

@SuppressWarnings("restriction")
public final class HttpServerProviderImpl extends HttpServerProvider
{
    public static final ThreadLocal<Configuration> CONFIGURATION = new ThreadLocal<Configuration>()
    {
        protected Configuration initialValue()
        {
            return new Configuration();
        }
    };

    @Override
    public HttpServer createHttpServer(
        InetSocketAddress addr,
        int backlog) throws IOException
    {
        HttpServer server = new HttpServerImpl(CONFIGURATION.get());
        if (addr != null)
        {
            server.bind(addr, backlog);
        }
        return server;
    }

    @Override
    public HttpsServer createHttpsServer(
        InetSocketAddress addr,
        int backlog) throws IOException
    {
        throw new UnsupportedOperationException("not yet implemented");
    }
}
