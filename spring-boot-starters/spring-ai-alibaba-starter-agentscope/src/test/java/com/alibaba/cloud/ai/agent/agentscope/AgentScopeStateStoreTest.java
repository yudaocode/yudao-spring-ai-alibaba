/*
 * Copyright 2025-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.agent.agentscope;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.state.InMemoryAgentStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises real AgentScope state persistence through the graph adapter without a remote
 * model.
 *
 * @author 芋道源码
 */
@Timeout(15)
class AgentScopeStateStoreTest {

	@Test
	void restoresSameThreadAndSeparatesOtherThreadsAndAgentNames() throws Exception {
		RecordingModel model = new RecordingModel();
		InMemoryAgentStateStore store = new InMemoryAgentStateStore();
		ReActAgent.Builder template = ReActAgent.builder().name("local").model(model);
		AgentScopeAgent agent = AgentScopeAgent.fromBuilder(template).name("writer").stateStore(store).build();
		RunnableConfig first = RunnableConfig.builder().threadId("thread-one").build();
		RunnableConfig second = RunnableConfig.builder().threadId("thread-two").build();

		assertEquals("answer-1", agent.call("first-question", first).getText());
		assertTrue(store.exists(null, "thread-one_writer"), "State must be saved before call returns");
		assertEquals("answer-2", agent.call("follow-up", first).getText());
		assertTrue(model.inputs.get(1).contains("first-question"));
		assertTrue(model.inputs.get(1).contains("answer-1"));
		assertTrue(model.inputs.get(1).contains("follow-up"));

		assertEquals("answer-3", agent.call("other-thread", second).getText());
		assertFalse(model.inputs.get(2).contains("first-question"));
		assertFalse(model.inputs.get(2).contains("follow-up"));
		AgentScopeAgent other = AgentScopeAgent.fromBuilder(template).name("reviewer").stateStore(store).build();
		assertEquals("answer-4", other.call("other-agent", first).getText());
		assertFalse(model.inputs.get(3).contains("first-question"));
		assertTrue(store.exists(null, "thread-one_reviewer"));
		assertEquals(3, store.listSessionIds(null).size());
	}

	@Test
	void modelFailurePersistsSafeInputBeforeGraphErrorReturns() throws Exception {
		RecordingModel model = new RecordingModel();
		model.behavior = "fail";
		InMemoryAgentStateStore store = new InMemoryAgentStateStore();
		AgentScopeAgent agent = AgentScopeAgent.fromBuilder(ReActAgent.builder().name("local").model(model))
			.name("writer")
			.stateStore(store)
			.build();
		RunnableConfig config = RunnableConfig.builder().threadId("failure").build();
		org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> agent.call("failed-question", config));
		assertTrue(store.exists(null, "failure_writer"));
		List<String> safe = store.get(null, "failure_writer", "agent_state", io.agentscope.core.state.AgentState.class)
			.orElseThrow()
			.getContext()
			.stream()
			.map(Msg::getTextContent)
			.collect(Collectors.toList());
		assertTrue(safe.contains("failed-question"));
		assertFalse(safe.contains("incomplete-answer"));
		model.behavior = "answer";
		agent.call("continue", config);
		List<String> recovered = model.inputs.get(model.inputs.size() - 1);
		assertTrue(recovered.contains("failed-question"));
		assertTrue(recovered.contains("continue"));
		assertFalse(recovered.contains("incomplete-answer"));
	}

	@Test
	void cancelledStreamRetainsCommittedFileStateAndCanResume(
			@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
		RecordingModel model = new RecordingModel();
		io.agentscope.core.state.JsonFileAgentStateStore store = new io.agentscope.core.state.JsonFileAgentStateStore(
				directory);
		AgentScopeAgent agent = AgentScopeAgent.fromBuilder(ReActAgent.builder().name("local").model(model))
			.name("writer")
			.stateStore(store)
			.build();
		RunnableConfig config = RunnableConfig.builder().threadId("cancellation").build();
		agent.call("committed-question", config);
		long committedVersion = store
			.getVersioned(null, "cancellation_writer", "agent_state", io.agentscope.core.state.AgentState.class)
			.version();
		model.behavior = "never";
		reactor.core.Disposable pending = agent.stream("cancelled-question", config).subscribe();
		try {
			assertTrue(model.started.await(3, java.util.concurrent.TimeUnit.SECONDS));
			pending.dispose();
			assertTrue(model.cancelled.await(3, java.util.concurrent.TimeUnit.SECONDS));
			// Graph state has its own message history. Check the actual persistent store,
			// then use a new graph wrapper so recovery cannot reuse that in-memory
			// history.
			io.agentscope.core.state.VersionedState<io.agentscope.core.state.AgentState> persisted = store
				.getVersioned(null, "cancellation_writer", "agent_state", io.agentscope.core.state.AgentState.class);
			assertEquals(committedVersion, persisted.version());
			List<String> committed = persisted.value()
				.getContext()
				.stream()
				.map(Msg::getTextContent)
				.collect(Collectors.toList());
			assertTrue(committed.contains("committed-question"));
			assertFalse(committed.contains("cancelled-question"));
			model.behavior = "answer";
			AgentScopeAgent fresh = AgentScopeAgent.fromBuilder(ReActAgent.builder().name("local").model(model))
				.name("writer")
				.stateStore(store)
				.build();
			fresh.call("continue-after-cancel", config);
			List<String> recovered = model.inputs.get(model.inputs.size() - 1);
			assertTrue(recovered.contains("committed-question"));
			assertTrue(recovered.contains("continue-after-cancel"));
			assertFalse(recovered.contains("cancelled-question"));
		}
		finally {
			pending.dispose();
		}
	}

	/**
	 * @author 芋道源码
	 */
	private static final class RecordingModel implements Model {

		private final List<List<String>> inputs = new CopyOnWriteArrayList<>();

		private volatile String behavior = "answer";

		private final java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);

		private final java.util.concurrent.CountDownLatch cancelled = new java.util.concurrent.CountDownLatch(1);

		@Override
		public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
			List<String> texts = messages.stream().map(Msg::getTextContent).collect(Collectors.toList());
			inputs.add(new ArrayList<>(texts));
			if (behavior.equals("fail")) {
				return Flux.concat(Flux.just(ChatResponse.builder()
					.content(List.of(TextBlock.builder().text("incomplete-answer").build()))
					.build()), Flux.error(new IllegalStateException("local model failure")));
			}
			if (behavior.equals("never")) {
				return Flux.<ChatResponse>never()
					.doOnSubscribe(ignored -> started.countDown())
					.doOnCancel(cancelled::countDown);
			}
			return Flux.just(ChatResponse.builder()
				.content(List.of(TextBlock.builder().text("answer-" + inputs.size()).build()))
				.build());
		}

		@Override
		public String getModelName() {
			return "local-recording-model";
		}

	}

}
