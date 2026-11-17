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
package com.alibaba.cloud.ai.studio.core.agent;

import com.alibaba.cloud.ai.studio.core.base.manager.AppComponentManager;
import com.alibaba.cloud.ai.studio.core.base.manager.DocumentRetrieverManager;
import com.alibaba.cloud.ai.studio.core.base.manager.FileManager;
import com.alibaba.cloud.ai.studio.core.base.service.McpServerService;
import com.alibaba.cloud.ai.studio.core.base.service.PluginService;
import com.alibaba.cloud.ai.studio.core.base.service.ToolExecutionService;
import com.alibaba.cloud.ai.studio.core.config.CommonConfig;
import com.alibaba.cloud.ai.studio.core.model.llm.ModelFactory;
import com.alibaba.cloud.ai.studio.runtime.domain.agent.AgentRequest;
import com.alibaba.cloud.ai.studio.runtime.domain.app.AgentConfig;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author 芋道源码
 */
class BasicAgentExecutorTest {

	@Test
	void buildChatClientAddsCallbacksToPromptOptions() throws Exception {
		ModelFactory modelFactory = mock(ModelFactory.class);
		ChatModel model = mock(ChatModel.class);
		when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
		when(modelFactory.getChatModel("dashscope")).thenReturn(model);
		when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(
				Collections.singletonList(new Generation(new AssistantMessage("ok")))));

		BasicAgentExecutor executor = new BasicAgentExecutor(
				mock(ToolExecutionService.class), mock(PluginService.class), mock(McpServerService.class),
				mock(AppComponentManager.class), mock(DocumentRetrieverManager.class), mock(ChatMemory.class),
				mock(CommonConfig.class), modelFactory, mock(FileManager.class));

		AgentConfig config = new AgentConfig();
		config.setModelProvider("dashscope");
		AgentContext context = new AgentContext();
		context.setConfig(config);
		context.setRequest(new AgentRequest());
		ToolCallback toolCallback = mock(ToolCallback.class);
		when(toolCallback.getToolDefinition()).thenReturn(ToolDefinition.builder()
			.name("echo").description("echo").inputSchema("{}").build());
		ToolCallbackProvider toolCallbackProvider = () -> new ToolCallback[] { toolCallback };
		ToolCallingChatOptions chatOptions = OpenAiChatOptions.builder().build();
		Method withToolCallbacks = BasicAgentExecutor.class.getDeclaredMethod("withToolCallbacks",
				ToolCallingChatOptions.class, ToolCallbackProvider.class);
		withToolCallbacks.setAccessible(true);
		ToolCallingChatOptions configured = (ToolCallingChatOptions) withToolCallbacks.invoke(
				executor, chatOptions, toolCallbackProvider);

		Method buildChatClient = BasicAgentExecutor.class.getDeclaredMethod("buildChatClient", AgentContext.class,
				ToolCallingChatOptions.class, ToolCallbackProvider.class);
		buildChatClient.setAccessible(true);
		ChatClient.Builder client = (ChatClient.Builder) buildChatClient.invoke(
				executor, context, configured, toolCallbackProvider);
		client.build().prompt(new Prompt(Collections.singletonList(new UserMessage("hi")), configured))
			.call().chatResponse();

		ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
		verify(model).call(prompt.capture());
		assertThat(chatOptions.getToolCallbacks()).isNullOrEmpty();
		assertThat(((ToolCallingChatOptions) prompt.getValue().getOptions()).getToolCallbacks())
			.containsExactly(toolCallback);
	}

}
