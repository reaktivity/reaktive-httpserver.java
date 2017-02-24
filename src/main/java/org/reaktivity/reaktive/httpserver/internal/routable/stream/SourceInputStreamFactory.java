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
package org.reaktivity.reaktive.httpserver.internal.routable.stream;

import java.io.IOException;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.MessageHandler;
import org.reaktivity.reaktive.httpserver.internal.HttpExchangeImpl;
import org.reaktivity.reaktive.httpserver.internal.routable.Source;
import org.reaktivity.reaktive.httpserver.internal.routable.Target;
import org.reaktivity.reaktive.httpserver.internal.types.OctetsFW;
import org.reaktivity.reaktive.httpserver.internal.types.stream.BeginFW;
import org.reaktivity.reaktive.httpserver.internal.types.stream.DataFW;
import org.reaktivity.reaktive.httpserver.internal.types.stream.EndFW;
import org.reaktivity.reaktive.httpserver.internal.types.stream.FrameFW;
import org.reaktivity.reaktive.httpserver.internal.types.stream.HttpBeginExFW;
import org.reaktivity.reaktive.httpserver.internal.types.stream.ResetFW;
import org.reaktivity.reaktive.httpserver.internal.types.stream.WindowFW;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

@SuppressWarnings("restriction")
public final class SourceInputStreamFactory
{
    private final FrameFW frameRO = new FrameFW();

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();

    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();

    private final HttpBeginExFW beginExRO = new HttpBeginExFW();

    private final Source source;
    private final Target target;
    private final LongSupplier supplyTargetId;
    private final LongFunction<HttpContext> supplyContext;

    public SourceInputStreamFactory(
        Source source,
        Target target,
        LongFunction<HttpContext> supplyContext,
        LongSupplier supplyTargetId)
    {
        this.source = source;
        this.target = target;
        this.supplyContext = supplyContext;
        this.supplyTargetId = supplyTargetId;
    }

    public MessageHandler newStream()
    {
        return new SourceInputStream()::handleStream;
    }

    private final class SourceInputStream
    {
        private MessageHandler streamState;
        private MessageHandler throttleState;

        private long correlationId;
        private long sourceId;
        private long targetId;
        private int window;

        @Override
        public String toString()
        {
            return String.format("%s[source=%s, sourceId=%016x, window=%d, targetId=%016x]",
                    getClass().getSimpleName(), source.routableName(), sourceId, window, targetId);
        }

        private SourceInputStream()
        {
            this.streamState = this::beforeBegin;
            this.throttleState = this::throttleSkipNextWindow;
        }

        private void handleStream(
            int msgTypeId,
            MutableDirectBuffer buffer,
            int index,
            int length)
        {
            streamState.onMessage(msgTypeId, buffer, index, length);
        }

        private void beforeBegin(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            if (msgTypeId == BeginFW.TYPE_ID)
            {
                processBegin(buffer, index, length);
            }
            else
            {
                processUnexpected(buffer, index, length);
            }
        }

        private void afterBeginOrData(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case DataFW.TYPE_ID:
                processData(buffer, index, length);
                break;
            case EndFW.TYPE_ID:
                processEnd(buffer, index, length);
                break;
            default:
                processUnexpected(buffer, index, length);
                break;
            }
        }

        private void afterEnd(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            processUnexpected(buffer, index, length);
        }

        private void afterRejectOrReset(
            int msgTypeId,
            MutableDirectBuffer buffer,
            int index,
            int length)
        {
            if (msgTypeId == DataFW.TYPE_ID)
            {
                dataRO.wrap(buffer, index, index + length);
                final long streamId = dataRO.streamId();

                source.doWindow(streamId, length);
            }
            else if (msgTypeId == EndFW.TYPE_ID)
            {
                endRO.wrap(buffer, index, index + length);
                final long streamId = endRO.streamId();

                source.removeStream(streamId);

                this.streamState = this::afterEnd;
            }
        }

        private void processUnexpected(
            DirectBuffer buffer,
            int index,
            int length)
        {
            frameRO.wrap(buffer, index, index + length);

            final long streamId = frameRO.streamId();

            source.doReset(streamId);

            this.streamState = this::afterRejectOrReset;
        }

        private void processBegin(
            DirectBuffer buffer,
            int index,
            int length)
        {
            beginRO.wrap(buffer, index, index + length);

            final long newSourceId = beginRO.streamId();
            final long newSourceRef = beginRO.referenceId();
            final long correlationId = beginRO.correlationId();
            final OctetsFW extension = beginRO.extension();

            final long newTargetId = supplyTargetId.getAsLong();

            final HttpContext context = supplyContext.apply(newSourceRef);
            final HttpHandler handler = (context != null) ? context.getHandler() : null;

            if (handler != null && extension.sizeof() > 0)
            {
                this.correlationId = correlationId;
                this.sourceId = newSourceId;
                this.targetId = newTargetId;

                final HttpExchangeImpl exchange = new HttpExchangeImpl();
                exchange.init(context, extension.get(beginExRO::wrap), this::doHttpBegin, this::doHttpEnd);

                try
                {
                    // TODO: dispatch on Executor if request body incomplete
                    handler.handle(exchange);
                }
                catch (IOException ex)
                {
                    LangUtil.rethrowUnchecked(ex);
                }

                this.streamState = this::afterBeginOrData;
                this.throttleState = this::throttleNextThenSkipWindow;
            }
            else
            {
                processUnexpected(buffer, index, length);
            }
        }

        private void doHttpBegin(
            HttpExchange exchange)
        {
            int status = exchange.getResponseCode();
            Headers headers = exchange.getResponseHeaders();
            target.doHttpBegin(targetId, 0L, correlationId, hs ->
            {
                hs.item(i -> i.name(":status").value(Integer.toString(status)));
                headers.forEach((k, vs) -> vs.forEach(v -> hs.item(i -> i.name(k).value(v))));
            });
        }

        private void doHttpEnd(
            HttpExchange exchange)
        {
            target.doHttpEnd(targetId);
        }

        private void processData(
            DirectBuffer buffer,
            int index,
            int length)
        {

            dataRO.wrap(buffer, index, index + length);

            OctetsFW payload = dataRO.payload();
            window -= dataRO.length();

            if (window < 0)
            {
                processUnexpected(buffer, index, length);
            }
            else
            {
                target.doData(targetId, payload);
            }
        }

        private void processEnd(
            DirectBuffer buffer,
            int index,
            int length)
        {
            endRO.wrap(buffer, index, index + length);

            target.removeThrottle(targetId);
            source.removeStream(sourceId);
        }

        private void handleThrottle(
            int msgTypeId,
            MutableDirectBuffer buffer,
            int index,
            int length)
        {
            throttleState.onMessage(msgTypeId, buffer, index, length);
        }

        private void throttleNextThenSkipWindow(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case WindowFW.TYPE_ID:
                processNextThenSkipWindow(buffer, index, length);
                break;
            case ResetFW.TYPE_ID:
                processReset(buffer, index, length);
                break;
            default:
                // ignore
                break;
            }
        }

        private void throttleSkipNextWindow(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case WindowFW.TYPE_ID:
                processSkipNextWindow(buffer, index, length);
                break;
            case ResetFW.TYPE_ID:
                processReset(buffer, index, length);
                break;
            default:
                // ignore
                break;
            }
        }

        private void throttleNextWindow(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case WindowFW.TYPE_ID:
                processNextWindow(buffer, index, length);
                break;
            case ResetFW.TYPE_ID:
                processReset(buffer, index, length);
                break;
            default:
                // ignore
                break;
            }
        }

        private void processSkipNextWindow(
            DirectBuffer buffer,
            int index,
            int length)
        {
            windowRO.wrap(buffer, index, index + length);

            throttleState = this::throttleNextWindow;
        }

        private void processNextWindow(
            DirectBuffer buffer,
            int index,
            int length)
        {
            windowRO.wrap(buffer, index, index + length);

            final int update = windowRO.update();

            window += update;
            source.doWindow(sourceId, update);
        }

        private void processNextThenSkipWindow(
            DirectBuffer buffer,
            int index,
            int length)
        {
            windowRO.wrap(buffer, index, index + length);

            final int update = windowRO.update();

            window += update;
            source.doWindow(sourceId, update);

            throttleState = this::throttleSkipNextWindow;
        }

        private void processReset(
            DirectBuffer buffer,
            int index,
            int length)
        {
            resetRO.wrap(buffer, index, index + length);

            source.doReset(sourceId);
        }
    }
}
