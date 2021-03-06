/*
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.syndesis.connector.http;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import static io.syndesis.connector.http.HttpConnectorFactories.computeHttpUri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class HttpConnectorFactoriesTest {
    @Test
    public void testComputeHttpUri() {
        assertThat(
            computeHttpUri("http", mapOf(
                "baseUrl", "www.google.com"
            ))
        ).isEqualTo("www.google.com");

        assertThat(
            computeHttpUri("http", mapOf(
                "baseUrl", "www.google.com",
                "path", "/test"
            ))
        ).isEqualTo("www.google.com/test");

        assertThat(
            computeHttpUri("http", mapOf(
                "baseUrl", "www.google.com/",
                "path", "/test"
            ))
        ).isEqualTo("www.google.com/test");

        assertThat(
            computeHttpUri("http", mapOf(
                "baseUrl", "www.google.com",
                "path", "/test"
            ))
        ).isEqualTo("www.google.com/test");

        assertThat(
            computeHttpUri("http", mapOf(
                "baseUrl", "www.google.com",
                "path", "test"
            ))
        ).isEqualTo("www.google.com/test");

        assertThat(
            computeHttpUri("http", mapOf(
                "baseUrl", "http://www.google.com/",
                "path", "/test"
            ))
        ).isEqualTo("www.google.com/test");

        assertThat(
            computeHttpUri("http", mapOf(
                "baseUrl", "http://www.google.com/",
                "path", "/test"
            ))
        ).isEqualTo("www.google.com/test");

        assertThat(
            computeHttpUri("http", mapOf(
                "baseUrl", "http://www.google.com"
            ))
        ).isEqualTo("www.google.com");

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
            () -> computeHttpUri( "http", mapOf(
                "baseUrl", "https://www.google.com"
            ))
        );
    }

    // *************************
    // Helpers
    // *************************

    private static <K, V> Map<K, V> mapOf(K key, V value, Object... values) {
        Map<K, V> map = new LinkedHashMap<>();
        map.put(key, value);

        for(int i = 0; i < values.length; i += 2) {
            map.put(
                (K) values[i],
                (V) values[i + 1]
            );
        }

        return map;
    }
}
