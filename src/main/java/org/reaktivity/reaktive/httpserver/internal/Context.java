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

import static java.lang.String.format;
import static org.agrona.CloseHelper.quietClose;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchService;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;

import org.agrona.ErrorHandler;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.broadcast.BroadcastTransmitter;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.agrona.concurrent.status.CountersManager;
import org.reaktivity.nukleus.Configuration;
import org.reaktivity.nukleus.http.internal.layouts.ControlLayout;

public final class Context implements Closeable
{
    private final ControlLayout.Builder controlRW = new ControlLayout.Builder();

    private boolean readonly;
    private Path configDirectory;
    private ControlLayout controlRO;
    private int maximumStreamsCount;
    private int streamsBufferCapacity;
    private int throttleBufferCapacity;
    private Function<String, Path> sourceStreamsPath;
    private BiFunction<String, String, Path> targetStreamsPath;
    private IdleStrategy idleStrategy;
    private ErrorHandler errorHandler;
    private CountersManager countersManager;
    private Counters counters;
    private RingBuffer toConductorCommands;
    private AtomicBuffer fromConductorResponseBuffer;
    private BroadcastTransmitter fromConductorResponses;

    private WatchService watchService;

    private Path streamsPath;

    private int maximumControlResponseLength;

    public Context readonly(
        boolean readonly)
    {
        this.readonly = readonly;
        return this;
    }

    public boolean readonly()
    {
        return readonly;
    }

    public int maximumStreamsCount()
    {
        return maximumStreamsCount;
    }

    public int streamsBufferCapacity()
    {
        return streamsBufferCapacity;
    }

    public int throttleBufferCapacity()
    {
        return throttleBufferCapacity;
    }

    public int maxMessageLength()
    {
        // see RingBuffer.maxMessageLength()
        return streamsBufferCapacity / 8;
    }

    public int maxControlResponseLength()
    {
        return maximumControlResponseLength;
    }

    public Context watchService(
        WatchService watchService)
    {
        this.watchService = watchService;
        return this;
    }

    public WatchService watchService()
    {
        return watchService;
    }

    public Context streamsPath(
        Path streamsPath)
    {
        this.streamsPath = streamsPath;
        return this;
    }

    public Path streamsPath()
    {
        return streamsPath;
    }

    public Context sourceStreamsPath(
        Function<String, Path> sourceStreamsPath)
    {
        this.sourceStreamsPath = sourceStreamsPath;
        return this;
    }

    public Function<String, Path> sourceStreamsPath()
    {
        return sourceStreamsPath;
    }

    public Context targetStreamsPath(
        BiFunction<String, String, Path> targetStreamsPath)
    {
        this.targetStreamsPath = targetStreamsPath;
        return this;
    }

    public BiFunction<String, String, Path> targetStreamsPath()
    {
        return targetStreamsPath;
    }

    public Context idleStrategy(
        IdleStrategy idleStrategy)
    {
        this.idleStrategy = idleStrategy;
        return this;
    }

    public IdleStrategy idleStrategy()
    {
        return idleStrategy;
    }

    public Context errorHandler(
        ErrorHandler errorHandler)
    {
        this.errorHandler = errorHandler;
        return this;
    }

    public ErrorHandler errorHandler()
    {
        return errorHandler;
    }

    public Context counterLabelsBuffer(
        AtomicBuffer counterLabelsBuffer)
    {
        controlRW.counterLabelsBuffer(counterLabelsBuffer);
        return this;
    }

    public Context counterValuesBuffer(
        AtomicBuffer counterValuesBuffer)
    {
        controlRW.counterValuesBuffer(counterValuesBuffer);
        return this;
    }

    public Context conductorCommands(
        RingBuffer conductorCommands)
    {
        this.toConductorCommands = conductorCommands;
        return this;
    }

    public RingBuffer conductorCommands()
    {
        return toConductorCommands;
    }

    public Context conductorResponseBuffer(
        AtomicBuffer conductorResponseBuffer)
    {
        this.fromConductorResponseBuffer = conductorResponseBuffer;
        return this;
    }

    public AtomicBuffer conductorResponseBuffer()
    {
        return fromConductorResponseBuffer;
    }

    public Context conductorResponses(
        BroadcastTransmitter conductorResponses)
    {
        this.fromConductorResponses = conductorResponses;
        return this;
    }

    public BroadcastTransmitter conductorResponses()
    {
        return fromConductorResponses;
    }

    public Logger logger()
    {
        return Logger.getLogger("reaktivity.httpserver");
    }

    public Context countersManager(
        CountersManager countersManager)
    {
        this.countersManager = countersManager;
        return this;
    }

    public CountersManager countersManager()
    {
        return countersManager;
    }

    public Counters counters()
    {
        return counters;
    }

    public Context conclude(
        Configuration config)
    {
        try
        {
            this.configDirectory = config.directory();

            this.maximumStreamsCount = config.maximumStreamsCount();

            this.streamsBufferCapacity = config.streamsBufferCapacity();

            this.throttleBufferCapacity = config.throttleBufferCapacity();

            this.maximumControlResponseLength = config.responseBufferCapacity() / 8;

            // default FileSystem cannot be closed
            watchService(FileSystems.getDefault().newWatchService());
            streamsPath(configDirectory.resolve("httpserver/streams"));

            sourceStreamsPath(source -> configDirectory.resolve(format("httpserver/streams/%s", source)));

            targetStreamsPath((source, target) -> configDirectory.resolve(format("%s/streams/httpserver#%s", target, source)));

            this.controlRO = controlRW.controlPath(config.directory().resolve("httpserver/control"))
                                      .commandBufferCapacity(config.commandBufferCapacity())
                                      .responseBufferCapacity(config.responseBufferCapacity())
                                      .counterLabelsBufferCapacity(config.counterLabelsBufferCapacity())
                                      .counterValuesBufferCapacity(config.counterValuesBufferCapacity())
                                      .readonly(readonly())
                                      .build();

            conductorCommands(new ManyToOneRingBuffer(controlRO.commandBuffer()));

            conductorResponseBuffer(controlRO.responseBuffer());

            conductorResponses(new BroadcastTransmitter(conductorResponseBuffer()));

            concludeCounters();
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }

        return this;
    }

    @Override
    public void close() throws IOException
    {
        quietClose(watchService);
        quietClose(controlRO);
    }

    private void concludeCounters()
    {
        if (countersManager == null)
        {
            countersManager(new CountersManager(
                    controlRO.counterLabelsBuffer(),
                    controlRO.counterValuesBuffer()));
        }

        if (counters == null)
        {
            counters = new Counters(countersManager);
        }
    }
}
