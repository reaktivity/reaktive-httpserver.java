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

import java.io.Closeable;
import java.util.function.LongFunction;

import org.reaktivity.nukleus.Nukleus;
import org.reaktivity.reaktive.httpserver.internal.router.Router;
import org.reaktivity.reaktive.httpserver.internal.watcher.Watcher;

import com.sun.net.httpserver.HttpContext;

@SuppressWarnings("restriction")
public final class HttpServerNukleus extends Nukleus.Composite
{
    static final String NAME = "httpserver";

    private final Resolver resolver;
    private final Closeable cleanup;

    HttpServerNukleus(
        Watcher watcher,
        Router router,
        Resolver resolver,
        Closeable cleanup)
    {
        super(watcher, router);
        this.resolver = resolver;
        this.cleanup = cleanup;
    }

    @Override
    public String name()
    {
        return HttpServerNukleus.NAME;
    }

    @Override
    public void close() throws Exception
    {
        super.close();
        cleanup.close();
    }

    public void resolver(
        LongFunction<HttpContext> resolver)
    {
        this.resolver.delegate(resolver);
    }
}
