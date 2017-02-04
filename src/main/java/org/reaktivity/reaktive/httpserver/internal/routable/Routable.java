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
package org.reaktivity.reaktive.httpserver.internal.routable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.reaktivity.nukleus.Nukleus;
import org.reaktivity.nukleus.Reaktive;
import org.reaktivity.reaktive.httpserver.internal.Context;
import org.reaktivity.reaktive.httpserver.internal.layouts.StreamsLayout;

import com.sun.net.httpserver.HttpContext;

@Reaktive
@SuppressWarnings("restriction")
public final class Routable extends Nukleus.Composite
{
    private final Context context;
    private final String sourceName;
    private final AtomicBuffer writeBuffer;
    private final Map<String, Source> sourcesByPartitionName;
    private final Map<String, Target> targetsByName;
    private final LongSupplier supplyTargetId;
    private final LongFunction<HttpContext> supplyContext;

    public Routable(
        Context context,
        String sourceName,
        LongFunction<HttpContext> supplyContext)
    {
        this.context = context;
        this.sourceName = sourceName;
        this.supplyContext = supplyContext;
        this.writeBuffer = new UnsafeBuffer(new byte[context.maxMessageLength()]);
        this.sourcesByPartitionName = new HashMap<>();
        this.targetsByName = new HashMap<>();
        this.supplyTargetId = context.counters().streamsSourced()::increment;
    }

    @Override
    public String name()
    {
        return sourceName;
    }

    public void onReadable(
        String partitionName)
    {
        sourcesByPartitionName.computeIfAbsent(partitionName, this::newSource);
    }

    private Source newSource(
        String partitionName)
    {
        StreamsLayout layout = new StreamsLayout.Builder()
            .path(context.sourceStreamsPath().apply(partitionName))
            .streamsCapacity(context.streamsBufferCapacity())
            .throttleCapacity(context.throttleBufferCapacity())
            .readonly(true)
            .build();

        return include(new Source(sourceName, partitionName, layout, writeBuffer,
                                  supplyContext, supplyTargetId, this::supplyTarget));
    }

    private Target supplyTarget(
        String targetName)
    {
        return targetsByName.computeIfAbsent(targetName, this::newTarget);
    }

    private Target newTarget(
        String targetName)
    {
        StreamsLayout layout = new StreamsLayout.Builder()
                .path(context.targetStreamsPath().apply(sourceName, targetName))
                .streamsCapacity(context.streamsBufferCapacity())
                .throttleCapacity(context.throttleBufferCapacity())
                .readonly(false)
                .build();

        return include(new Target(targetName, layout, writeBuffer));
    }
}
