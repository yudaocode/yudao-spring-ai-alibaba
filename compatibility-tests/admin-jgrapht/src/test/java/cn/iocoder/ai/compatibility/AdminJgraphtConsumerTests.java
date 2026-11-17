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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jgrapht.Graph;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises installed graph classes in a standalone Boot2 consumer on native Java8.
 *
 * @author 芋道源码
 */
class AdminJgraphtConsumerTests {

    @Test
    void bootDagRejectsCyclesAndKeepsItsSupplierAfterSerialization() throws Exception {
        assertEquals(System.getProperty("compat.expected.vm"), System.getProperty("java.specification.version"));
        org.slf4j.MDC.put("jgrapht-native8", "ok");
        try {
            assertThat(org.slf4j.MDC.get("jgrapht-native8")).isEqualTo("ok");
            assertThat(org.slf4j.MarkerFactory.getMarker("jgrapht-native8").getName()).isEqualTo("jgrapht-native8");
        } finally { org.slf4j.MDC.remove("jgrapht-native8"); }
        assertThat(Graph.class.getProtectionDomain().getCodeSource().getLocation().getPath()).endsWith(".jar");
        assertThat(Collections.list(Graph.class.getClassLoader().getResources("org/jgrapht/Graph.class"))).hasSize(1);
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class)
                .web(WebApplicationType.NONE).properties("spring.main.banner-mode=off", "logging.level.root=WARN").run()) {
            @SuppressWarnings("unchecked")
            DirectedAcyclicGraph<String, PrivateEdge> graph = context.getBean(DirectedAcyclicGraph.class);
            for (String vertex : Arrays.asList("start", "tool", "end")) graph.addVertex(vertex);
            graph.addEdge("start", "tool");
            graph.addEdge("tool", "end");
            assertThrows(IllegalArgumentException.class, () -> graph.addEdge("end", "start"));
            List<String> order = new ArrayList<>();
            new TopologicalOrderIterator<>(graph).forEachRemaining(order::add);
            assertThat(order).containsExactly("start", "tool", "end");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream output = new ObjectOutputStream(bytes)) { output.writeObject(graph); }
            try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                @SuppressWarnings("unchecked")
                DirectedAcyclicGraph<String, PrivateEdge> restored = (DirectedAcyclicGraph<String, PrivateEdge>) input.readObject();
                restored.addVertex("finish");
                assertThat(restored.addEdge("end", "finish")).isInstanceOf(PrivateEdge.class);
                assertThat(restored.vertexSet()).hasSize(4);
            }
        }
    }

    @Test
    void shortestPathUsesTheInstalledHeapDependency() {
        assertEquals(System.getProperty("compat.expected.vm"), System.getProperty("java.specification.version"));
        Graph<String, DefaultWeightedEdge> graph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (String vertex : Arrays.asList("a", "b", "c")) graph.addVertex(vertex);
        graph.setEdgeWeight(graph.addEdge("a", "b"), 2);
        graph.setEdgeWeight(graph.addEdge("b", "c"), 3);
        graph.setEdgeWeight(graph.addEdge("a", "c"), 20);
        assertThat(new DijkstraShortestPath<>(graph).getPath("a", "c").getWeight()).isEqualTo(5.0);
    }

    /** @author 芋道源码 */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App {
        @Bean
        DirectedAcyclicGraph<String, PrivateEdge> graph() { return new DirectedAcyclicGraph<>(PrivateEdge.class); }
    }

    /** @author 芋道源码 */
    private static class PrivateEdge extends DefaultEdge {
        private static final long serialVersionUID = 1L;
        private PrivateEdge() { }
    }

}
