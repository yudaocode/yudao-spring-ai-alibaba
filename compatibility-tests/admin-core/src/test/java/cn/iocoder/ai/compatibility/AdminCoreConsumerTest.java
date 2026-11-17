/*
 * Copyright 2026 the original author or authors.
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

package cn.iocoder.ai.compatibility;

import java.util.Collections;

import com.alibaba.cloud.ai.studio.core.base.manager.SandboxManager;
import com.alibaba.cloud.ai.studio.core.config.ScriptEngineConfig;
import com.alibaba.cloud.ai.studio.core.rag.vectorstore.AdminSearchRequest;
import com.alibaba.cloud.ai.studio.core.rag.vectorstore.SearchType;
import com.alibaba.cloud.ai.studio.runtime.domain.Result;
import com.alibaba.cloud.ai.studio.runtime.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uses an installed Core jar in an independent Boot context with real H2 and engines.
 *
 * @author 芋道源码
 */
class AdminCoreConsumerTest {

    @Test
    void installedLibraryRunsWithTheHostBootDatabaseAndScriptEngines() throws Exception {
        assertEquals(System.getProperty("compat.expected.vm"), System.getProperty("java.specification.version"));
        assertTrue(SandboxManager.class.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith(".jar"));
        assertEquals(1, Collections.list(SearchRequest.class.getClassLoader().getResources(
                SearchRequest.class.getName().replace('.', '/') + ".class")).size());
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class)
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off", "logging.level.root=WARN",
                        "spring.datasource.url=jdbc:h2:mem:admin_core_consumer;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver")
                .run()) {
            assertEquals(Integer.valueOf(1), context.getBean(JdbcTemplate.class).queryForObject("select 1", Integer.class));
            assertTrue(context.containsBean("sqlSessionFactory"));
            SandboxManager manager = context.getBean(SandboxManager.class);
            Result<String> js = manager.executeScript("input + ' café'", Collections.singletonMap("input", "你好"), "js");
            assertTrue(js.isSuccess());
            assertEquals("你好 café", JsonUtils.fromJson(js.getData()).path("data").asText());
            Result<String> py = manager.executePython3Script("import json\n{'value': json.loads('{\"n\":3}')[\"n\"]}",
                    Collections.emptyMap(), "py");
            assertTrue(py.isSuccess(), String.valueOf(py));
            assertEquals(3, JsonUtils.fromJson(py.getData()).path("data").path("value").asInt());
            AdminSearchRequest request = AdminSearchRequest.adminBuilder().query("你好")
                    .searchType(SearchType.HYBRID).hybridWeight(0.5f).topK(4).build();
            assertEquals(SearchType.HYBRID, AdminSearchRequest.copyOf(request).build().getSearchType());
        }
    }

    /**
     * Imports only library components needed by this isolated consumer acceptance test.
     *
     * @author 芋道源码
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({ ScriptEngineConfig.class, SandboxManager.class })
    static class App {
    }

}
