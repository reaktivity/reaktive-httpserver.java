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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.function.Consumer;

import org.reaktivity.reaktive.httpserver.internal.types.HttpHeaderFW;
import org.reaktivity.reaktive.httpserver.internal.types.stream.HttpBeginExFW;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;

@SuppressWarnings("restriction")
public final class HttpExchangeImpl extends HttpExchange
{
    private HttpContext httpContext;

    private InetSocketAddress localAddress;
    private InetSocketAddress remoteAddress;

    private String requestMethod;
    private URI requestURI;
    private String protocol;
    private Headers requestHeaders;

    private HttpPrincipal principal;

    private InputStream requestBody;
    private InputStream requestBodyOverride;

    private int responseCode;
    private Headers responseHeaders;

    private OutputStream responseBody;
    private OutputStream responseBodyOverride;

    private Map<String, Object> attributes;

    private boolean httpBeginSent;

    private Consumer<HttpExchange> doHttpBegin;
    private Consumer<HttpExchange> doHttpEnd;

    @Override
    public HttpPrincipal getPrincipal()
    {
        return principal;
    }

    @Override
    public Headers getRequestHeaders()
    {
        return requestHeaders;
    }

    @Override
    public Headers getResponseHeaders()
    {
        return responseHeaders;
    }

    @Override
    public URI getRequestURI()
    {
        return requestURI;
    }

    @Override
    public String getRequestMethod()
    {
        return requestMethod;
    }

    @Override
    public HttpContext getHttpContext()
    {
        return httpContext;
    }

    @Override
    public void close()
    {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public InputStream getRequestBody()
    {
        InputStream requestBody = requestBodyOverride;

        if (requestBody == null)
        {
            requestBody = this.requestBody;
        }

        if (requestBody == null)
        {
            throw new UnsupportedOperationException("not yet implemented");
        }

        return requestBody;
    }

    @Override
    public OutputStream getResponseBody()
    {
        OutputStream responseBody = responseBodyOverride;

        if (responseBody == null)
        {
            responseBody = this.responseBody;
        }

        if (responseBody == null)
        {
            throw new UnsupportedOperationException("not yet implemented");
        }

        return responseBody;
    }

    @Override
    public void sendResponseHeaders(
        int responseCode,
        long responseLength) throws IOException
    {
        if (httpBeginSent)
        {
            throw new IOException("headers already sent");
        }

        this.responseCode = responseCode;

        doHttpBegin.accept(this);
        httpBeginSent = true;

        if (responseLength == -1L)
        {
            doHttpEnd.accept(this);
        }
    }

    @Override
    public InetSocketAddress getRemoteAddress()
    {
        return remoteAddress;
    }

    @Override
    public int getResponseCode()
    {
        return responseCode;
    }

    @Override
    public InetSocketAddress getLocalAddress()
    {
        return localAddress;
    }

    @Override
    public String getProtocol()
    {
        return protocol;
    }

    @Override
    public Object getAttribute(
        String name)
    {
        return attributes().get(name);
    }

    @Override
    public void setAttribute(
        String name,
        Object value)
    {
        attributes().put(name, value);
    }

    @Override
    public void setStreams(
        InputStream input,
        OutputStream output)
    {
        this.requestBodyOverride = input;
        this.responseBodyOverride = output;
    }

    private Map<String, Object> attributes()
    {
        if (attributes == null)
        {
            attributes = httpContext.getAttributes();
        }

        return attributes;
    }

    public void init(
        HttpContext httpContext,
        HttpBeginExFW beginEx,
        Consumer<HttpExchange> doHttpBegin,
        Consumer<HttpExchange> doHttpEnd)
    {
        this.httpContext = httpContext;

        this.localAddress = httpContext.getServer().getAddress();
        this.remoteAddress = null;  // TODO

        this.protocol = "HTTP/1.1";
        this.requestHeaders = new Headers();
        beginEx.headers().forEach(this::processHeader);

        this.responseCode = -1;
        this.responseHeaders = new Headers();

        this.attributes = null;

        this.doHttpBegin = doHttpBegin;
        this.doHttpEnd = doHttpEnd;
    }

    private void processHeader(
        HttpHeaderFW header)
    {
        final String name = header.name().asString();
        final String value = header.value().asString();

        // detect colon-prefixed pseudo header
        if (name.charAt(0) == ':')
        {
            switch (name)
            {
            case ":method":
                this.requestMethod = value;
                break;
            case ":path":
                this.requestURI = URI.create(value);
                break;
            }
        }
        else
        {
            requestHeaders.add(name, value);
        }
    }
}
