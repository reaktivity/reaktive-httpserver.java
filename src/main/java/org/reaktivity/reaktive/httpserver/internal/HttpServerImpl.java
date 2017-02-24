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

import static java.util.Collections.singletonMap;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executor;

import org.agrona.CloseHelper;
import org.reaktivity.nukleus.Configuration;
import org.reaktivity.nukleus.Controller;
import org.reaktivity.nukleus.http.internal.HttpController;
import org.reaktivity.nukleus.tcp.internal.TcpController;
import org.reaktivity.reaktor.Reaktor;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

@SuppressWarnings("restriction")
public final class HttpServerImpl extends HttpServer
{
    private static final String PATH_PSEUDO_HEADER_NAME = ":path";
    private static final String HTTP_NUKLEUS_NAME = "http";
    private static final String TCP_NUKLEUS_NAME = "tcp";
    private static final String ANY_DEVICE_NAME = "any";

    private final Reaktor reaktor;
    private final ConcurrentMap<Long, HttpContext> contextsByRef;

    private InetSocketAddress address;
    private Executor executor;
    private long httpSourceRef;

    HttpServerImpl(
        Configuration config)
    {
        this.reaktor = Reaktor.builder()
                .config(config)
                .discover(this::matchNukleus)
                .discover(this::matchController)
                .errorHandler(ex -> ex.printStackTrace(System.err))
                .build()
                .start();
        this.contextsByRef = new ConcurrentSkipListMap<>();
    }

    @Override
    public void bind(
        InetSocketAddress addr,
        int backlog) throws IOException
    {
        if (this.address == null)
        {
            int port = addr.getPort();
            InetAddress address = addr.getAddress();

            final long httpTargetRef = System.identityHashCode(this);
            final Map<String, String> headers = Collections.emptyMap();

            HttpController http = reaktor.controller(HttpController.class);
            this.httpSourceRef = http.routeInputNew(TCP_NUKLEUS_NAME, 0L, HttpServerNukleus.NAME, httpTargetRef, headers).join();
            http.unrouteInputNew(TCP_NUKLEUS_NAME, httpSourceRef, HttpServerNukleus.NAME, httpTargetRef, headers).join();

            TcpController tcp = reaktor.controller(TcpController.class);
            tcp.routeInputNew(ANY_DEVICE_NAME, port, HTTP_NUKLEUS_NAME, httpSourceRef, address).join();

            this.address = addr;

            this.reaktor.detach(this::matchTcpController);
        }
        else
        {
            throw new IOException("already bound");
        }
    }

    @Override
    public HttpContext createContext(
        String path)
    {
        return createContext0(path);
    }

    @Override
    public HttpContext createContext(
        String path,
        HttpHandler handler)
    {
        HttpContext context = createContext0(path);
        context.setHandler(handler);
        return context;
    }

    @Override
    public InetSocketAddress getAddress()
    {
        return address;
    }

    @Override
    public Executor getExecutor()
    {
        return executor;
   }

    @Override
    public void removeContext(
        String path)
            throws IllegalArgumentException
    {
        removeContext0(path);
    }

    @Override
    public void removeContext(
        HttpContext context)
    {
        removeContext0(context.getPath());
    }

    @Override
    public void setExecutor(
        Executor executor)
    {
        this.executor = executor;
    }

    @Override
    public void start()
    {
        HttpServerNukleus nukleus = reaktor.nukleus(HttpServerNukleus.NAME, HttpServerNukleus.class);
        nukleus.resolver(contextsByRef::get);
    }

    @Override
    public void stop(
        int delay)
    {
        CloseHelper.close(reaktor);
    }

    private boolean matchNukleus(
        String name)
    {
        return TCP_NUKLEUS_NAME.equals(name) ||
                HTTP_NUKLEUS_NAME.equals(name) ||
                HttpServerNukleus.NAME.equals(name);
    }

    private boolean matchController(
        Class<? extends Controller> controller)
    {
        return matchTcpController(controller) ||
                HttpController.class.equals(controller);
    }

    private boolean matchTcpController(
        Class<? extends Controller> controller)
    {
        return TcpController.class.equals(controller);
    }

    private HttpContext createContext0(
        String path)
    {
        HttpContext context = new HttpContextImpl(this, path);
        long contextRef = System.identityHashCode(path.intern());

        if (contextsByRef.putIfAbsent(contextRef, context) != null)
        {
            throw new IllegalArgumentException("context already exists");
        }

        Map<String, String> headers = singletonMap(PATH_PSEUDO_HEADER_NAME, path);

        HttpController http = reaktor.controller(HttpController.class);
        http.routeInputNew(TCP_NUKLEUS_NAME, httpSourceRef, HttpServerNukleus.NAME, contextRef, headers).join();

        return context;
    }

    private HttpContext removeContext0(
        String path)
    {
        long sourceRef = System.identityHashCode(path.intern());
        HttpContext context = contextsByRef.remove(sourceRef);

        if (context != null)
        {
            long targetRef = System.identityHashCode(context);
            Map<String, String> headers = singletonMap(PATH_PSEUDO_HEADER_NAME, path);

            HttpController http = reaktor.controller(HttpController.class);
            http.unrouteInputNew(TCP_NUKLEUS_NAME, httpSourceRef, HttpServerNukleus.NAME, targetRef, headers).join();
        }

        return context;
    }
}
