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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

@SuppressWarnings("restriction")
public final class HttpContextImpl extends HttpContext
{
    private final HttpServer server;
    private final String path;

    private HttpHandler handler;
    private Map<String, Object> attributes;
    private List<Filter> filters;
    private Authenticator authenticator;

    HttpContextImpl(
        HttpServer server,
        String path)
    {
        this.server = server;
        this.path = path;
    }

    @Override
    public HttpHandler getHandler()
    {
        return handler;
    }

    @Override
    public void setHandler(
        HttpHandler handler)
    {
        this.handler = handler;
    }

    @Override
    public String getPath()
    {
        return path;
    }

    @Override
    public HttpServer getServer()
    {
        return server;
    }

    @Override
    public Map<String, Object> getAttributes()
    {
        if (attributes == null)
        {
            attributes = new HashMap<>();
        }

        return attributes;
    }

    @Override
    public List<Filter> getFilters()
    {
        if (filters == null)
        {
            filters = new LinkedList<>();
        }

        return filters;
    }

    @Override
    public Authenticator setAuthenticator(
        Authenticator authenticator)
    {
        Authenticator previous = this.authenticator;
        this.authenticator = authenticator;
        return previous;
    }

    @Override
    public Authenticator getAuthenticator()
    {
        return authenticator;
    }
}
