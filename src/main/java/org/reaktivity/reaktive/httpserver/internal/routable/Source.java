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


import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.reaktivity.nukleus.Nukleus;
import org.reaktivity.reaktive.httpserver.internal.layouts.StreamsLayout;
import org.reaktivity.reaktive.httpserver.internal.routable.stream.SourceInputStreamFactory;
import org.reaktivity.reaktive.httpserver.internal.types.stream.BeginFW;
import org.reaktivity.reaktive.httpserver.internal.types.stream.FrameFW;
import org.reaktivity.reaktive.httpserver.internal.types.stream.ResetFW;
import org.reaktivity.reaktive.httpserver.internal.types.stream.WindowFW;

import com.sun.net.httpserver.HttpContext;

@SuppressWarnings("restriction")
public final class Source implements Nukleus
{
    private final FrameFW frameRO = new FrameFW();
    private final BeginFW beginRO = new BeginFW();

    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();

    private final String sourceName;
    private final String partitionName;
    private final StreamsLayout layout;
    private final AtomicBuffer writeBuffer;
    private final RingBuffer streamsBuffer;
    private final RingBuffer throttleBuffer;
    private final Supplier<MessageHandler> streamFactory;
    private final Long2ObjectHashMap<MessageHandler> streams;

    Source(
        String sourceName,
        String partitionName,
        StreamsLayout layout,
        AtomicBuffer writeBuffer,
        LongFunction<HttpContext> resolver,
        LongSupplier supplyTargetId,
        Function<String, Target> supplyTarget)
    {
        this.sourceName = sourceName;
        this.partitionName = partitionName;
        this.layout = layout;
        this.writeBuffer = writeBuffer;

        this.streamsBuffer = layout.streamsBuffer();
        this.throttleBuffer = layout.throttleBuffer();
        this.streams = new Long2ObjectHashMap<>();

        Target target = supplyTarget.apply(sourceName);
        this.streamFactory = new SourceInputStreamFactory(this, target, resolver, supplyTargetId)::newStream;
    }

    @Override
    public int process()
    {
        return streamsBuffer.read(this::handleRead);
    }

    @Override
    public void close() throws Exception
    {
        layout.close();
    }

    @Override
    public String name()
    {
        return partitionName;
    }

    public String routableName()
    {
        return sourceName;
    }

    @Override
    public String toString()
    {
        return String.format("%s[name=%s]", getClass().getSimpleName(), partitionName);
    }

    private void handleRead(
        int msgTypeId,
        MutableDirectBuffer buffer,
        int index,
        int length)
    {
        frameRO.wrap(buffer, index, index + length);

        final long streamId = frameRO.streamId();

        // TODO: use Long2ObjectHashMap.getOrDefault(long, this::handleUnrecognized)
        final MessageHandler handler = streams.get(streamId);

        if (handler != null)
        {
            handler.onMessage(msgTypeId, buffer, index, length);
        }
        else
        {
            handleUnrecognized(msgTypeId, buffer, index, length);
        }
    }

    private void handleUnrecognized(
        int msgTypeId,
        MutableDirectBuffer buffer,
        int index,
        int length)
    {
        if (msgTypeId == BeginFW.TYPE_ID)
        {
            handleBegin(msgTypeId, buffer, index, length);
        }
        else
        {
            frameRO.wrap(buffer, index, index + length);

            final long streamId = frameRO.streamId();

            doReset(streamId);
        }
    }

    private void handleBegin(
        int msgTypeId,
        MutableDirectBuffer buffer,
        int index,
        int length)
    {
        beginRO.wrap(buffer, index, index + length);
        final long sourceId = beginRO.streamId();

        final MessageHandler newStream = streamFactory.get();
        streams.put(sourceId, newStream);
        newStream.onMessage(msgTypeId, buffer, index, length);
    }

    public void doWindow(
        final long streamId,
        final int update)
    {
        final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .streamId(streamId).update(update).build();

        throttleBuffer.write(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    public void doReset(
        final long streamId)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .streamId(streamId).build();

        throttleBuffer.write(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    public void removeStream(
        long streamId)
    {
        streams.remove(streamId);
    }
}
