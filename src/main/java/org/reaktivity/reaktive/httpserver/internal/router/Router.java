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
package org.reaktivity.reaktive.httpserver.internal.router;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.reaktivity.nukleus.Nukleus;
import org.reaktivity.nukleus.Reaktive;
import org.reaktivity.reaktive.httpserver.internal.Context;
import org.reaktivity.reaktive.httpserver.internal.Resolver;
import org.reaktivity.reaktive.httpserver.internal.routable.Routable;

import com.sun.net.httpserver.HttpContext;

@Reaktive
@SuppressWarnings("restriction")
public final class Router extends Nukleus.Composite
{
    private static final Pattern SOURCE_NAME = Pattern.compile("([^#]+).*");

    private final Context context;
    private final Map<String, Routable> routables;

    private LongFunction<HttpContext> supplyContext;

    public Router(
        Context context)
    {
        this.context = context;
        this.routables = new HashMap<>();
    }

    @Override
    public String name()
    {
        return "router";
    }

    public void setResolver(
        Resolver resolver)
    {
        this.supplyContext = resolver::resolve;
    }

    public void onReadable(
        Path sourcePath)
    {
        String sourceName = source(sourcePath);
        Routable routable = routables.computeIfAbsent(sourceName, this::newRoutable);
        String partitionName = sourcePath.getFileName().toString();
        routable.onReadable(partitionName);
    }

    public void onExpired(
        Path sourcePath)
    {
        // TODO:
    }

    private static String source(
        Path path)
    {
        Matcher matcher = SOURCE_NAME.matcher(path.getName(path.getNameCount() - 1).toString());
        if (matcher.matches())
        {
            return matcher.group(1);
        }
        else
        {
            throw new IllegalStateException();
        }
    }

    private Routable newRoutable(
        String sourceName)
    {
        return include(new Routable(context, sourceName, supplyContext));
    }
}
