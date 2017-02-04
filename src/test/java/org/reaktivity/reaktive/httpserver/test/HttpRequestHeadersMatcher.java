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
package org.reaktivity.reaktive.httpserver.test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.hamcrest.core.IsCollectionContaining.hasItem;

import java.util.List;

import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

@SuppressWarnings("restriction")
public final class HttpRequestHeadersMatcher extends TypeSafeMatcher<HttpExchange>
{
    private final Matcher<? super Headers> headersMatcher;

    private HttpRequestHeadersMatcher(
        Matcher<? super Headers> headersMatcher)
    {
        this.headersMatcher = headersMatcher;
    }

    @Override
    protected boolean matchesSafely(
        HttpExchange exchange)
    {
        Headers headers = exchange.getRequestHeaders();

        return headersMatcher.matches(headers);
    }

    @Override
    public void describeTo(
        Description description)
    {
        description.appendText("an exchange with request headers ")
                   .appendDescriptionOf(headersMatcher);
    }

    @Factory
    public static Matcher<HttpExchange> hasRequestHeader(
        String headerName,
        Matcher<String> valueMatcher)
    {
        Matcher<String> nameMatcher = equalTo(headerName);
        Matcher<? super Headers> headers = hasRequestHeaders(nameMatcher, valueMatcher);

        return hasRequestHeaders(headers);
    }

    @Factory
    public static Matcher<? super Headers> hasRequestHeaders(
        Matcher<String> nameMatcher,
        Matcher<String> valueMatcher)
    {
        Matcher<? super List<String>> valuesMatcher = hasItem(valueMatcher);

        return hasEntry(nameMatcher, valuesMatcher);
    }

    @Factory
    public static Matcher<HttpExchange> hasRequestHeaders(
        Matcher<? super Headers> headersMatcher)
    {
        return new HttpRequestHeadersMatcher(headersMatcher);
    }
}
