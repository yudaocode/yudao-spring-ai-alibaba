/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.graph.store.stores;

import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.alibaba.cloud.ai.graph.store.StoreSearchRequest;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the SQL store on the MySQL and Oracle dialects.
 *
 * @author 芋道源码
 */
@Testcontainers(disabledWithoutDocker = true)
class DatabaseStoreDialectDockerTest {

    @Test
    void persistsAndUpsertsOnMysql() {
        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")) {
            mysql.start();
            verifyStore(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        }
    }

    @Test
    void persistsAndUpsertsOnOracle() {
        try (OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.7-slim-faststart")
                .withUsername("testuser")
                .withPassword("testpwd")) {
            oracle.start();
            verifyStore(oracle.getJdbcUrl(), oracle.getUsername(), oracle.getPassword());
        }
    }

    private void verifyStore(String jdbcUrl, String user, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(2);
        List<String> namespace = Collections.singletonList("graph");
        String key = "配置";
        Map<String, Object> first = Collections.singletonMap("revision", 1);
        Map<String, Object> second = Collections.singletonMap("revision", 2);
        try (HikariDataSource source = new HikariDataSource(config)) {
            DatabaseStore store = new DatabaseStore(source, "compat_graph_store");
            store.putItem(StoreItem.of(namespace, key, first));
            assertThat(store.getItem(namespace, key)).hasValueSatisfying(item ->
                    assertThat(item.getValue()).isEqualTo(first));
            store.putItem(StoreItem.of(namespace, key, second));
            assertThat(new DatabaseStore(source, "compat_graph_store").getItem(namespace, key))
                    .hasValueSatisfying(item -> assertThat(item.getValue()).isEqualTo(second));
            assertThat(store.searchItems(StoreSearchRequest.builder().build()).getItems()).hasSize(1);
            assertThat(store.deleteItem(namespace, key)).isTrue();
            assertThat(store.getItem(namespace, key)).isEmpty();
        }
    }
}
