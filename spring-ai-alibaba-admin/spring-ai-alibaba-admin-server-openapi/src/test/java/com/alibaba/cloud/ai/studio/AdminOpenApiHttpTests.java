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

package com.alibaba.cloud.ai.studio;

import com.alibaba.cloud.ai.studio.controller.ChatController;
import com.alibaba.cloud.ai.studio.interceptor.ApiKeyAuthInterceptor;
import com.alibaba.cloud.ai.studio.core.base.manager.RedisManager;
import com.alibaba.cloud.ai.studio.core.base.service.AccountService;
import com.alibaba.cloud.ai.studio.core.base.service.AgentService;
import com.alibaba.cloud.ai.studio.core.base.service.ApiKeyService;
import com.alibaba.cloud.ai.studio.core.base.service.WorkflowService;
import com.alibaba.cloud.ai.studio.core.context.RequestContextHolder;
import com.alibaba.cloud.ai.studio.runtime.domain.account.Account;
import com.alibaba.cloud.ai.studio.runtime.domain.account.ApiKey;
import com.alibaba.cloud.ai.studio.runtime.domain.agent.AgentResponse;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.debug.WorkflowResponse;
import com.alibaba.cloud.ai.studio.runtime.enums.ErrorCode;
import com.alibaba.cloud.ai.studio.runtime.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;

/**
 * Exercises servlet authentication, JSON and SSE through actual MVC dispatch. Services
 * are deterministic fixtures; no cloud credentials or external database is used.
 *
 * @author 芋道源码
 */
class AdminOpenApiHttpTests {

	private final AccountService accounts = mock(AccountService.class);

	private final ApiKeyService keys = mock(ApiKeyService.class);

	private final AgentService agents = mock(AgentService.class);

	private final WorkflowService workflows = mock(WorkflowService.class);

	private final ApiKeyAuthInterceptor auth = new ApiKeyAuthInterceptor(accounts, keys);

	private MockMvc mvc;

	@BeforeEach
	void setup() {
		ApiKey key = new ApiKey();
		key.setAccountId("account-a");
		when(keys.getApiKey("secret")).thenReturn(key);
		Account account = new Account();
		account.setAccountId("account-a");
		account.setUsername("测试用户");
		account.setDefaultWorkspaceId("workspace-a");
		when(accounts.getAccount("account-a")).thenReturn(account);
		ChatController controller = new ChatController(agents, workflows, mock(RedisManager.class));
		mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(auth).build();
	}

	@AfterEach
	void cleanup() {
		RequestContextHolder.clearRequestContext();
	}

	@Test
	void missingKeyReturnsUnauthorizedJson() throws Exception {
		MvcResult result = mvc
			.perform(post("/api/v1/apps/chat/completions").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andReturn();
		assertEquals(401, result.getResponse().getStatus());
		assertTrue(result.getResponse().getContentAsString().contains("\"code\":401"));
		assertTrue(result.getResponse().getContentAsString().contains(ErrorCode.INVALID_API_KEY.getMessage()));
		verifyNoInteractions(agents, workflows);
	}

	@Test
	void unknownKeyReturnsUnauthorized() throws Exception {
		MvcResult result = mvc
			.perform(post("/api/v1/apps/chat/completions").header("Authorization", "Bearer unknown")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andReturn();
		assertEquals(401, result.getResponse().getStatus());
		verifyNoInteractions(accounts, agents);
	}

	@Test
	void keyWithMissingAccountIsRejected() throws Exception {
		when(accounts.getAccount("account-a")).thenReturn(null);
		MvcResult result = request("chat", false);
		assertEquals(401, result.getResponse().getStatus());
		verifyNoInteractions(agents);
	}

	@Test
	void optionsBypassesAuthentication() {
		MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/apps/chat/completions");
		assertTrue(auth.preHandle(request, new MockHttpServletResponse(), new Object()));
		verifyNoInteractions(keys, accounts);
	}

	@Test
	void agentSuccessUsesAuthenticatedWorkspaceAndJsonNames() throws Exception {
		when(agents.call(any())).thenAnswer(call -> {
			assertEquals("workspace-a", RequestContextHolder.getRequestContext().getWorkspaceId());
			assertEquals("测试用户", RequestContextHolder.getRequestContext().getUsername());
			return AgentResponse.builder().requestId("响应-café").build();
		});
		MvcResult result = request("chat", false);
		assertEquals(200, result.getResponse().getStatus());
		assertTrue(result.getResponse()
			.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
			.contains("\"request_id\":\"响应-café\""));
	}

	@Test
	void workflowSuccessPreservesRuntimeJson() throws Exception {
		when(workflows.call(any())).thenReturn(WorkflowResponse.builder().requestId("workflow-a").build());
		MvcResult result = request("workflow", false);
		assertEquals(200, result.getResponse().getStatus());
		assertTrue(result.getResponse().getContentAsString().contains("\"request_id\":\"workflow-a\""));
	}

	@Test
	void agentBusinessErrorPreservesStatusAndMessage() throws Exception {
		when(agents.call(any())).thenThrow(new BizException(ErrorCode.FORBIDDEN.toError()));
		MvcResult result = request("chat", false);
		assertEquals(403, result.getResponse().getStatus());
		assertTrue(result.getResponse().getContentAsString().contains(ErrorCode.FORBIDDEN.getCode()));
	}

	@Test
	void workflowBusinessErrorPreservesStatusAndMessage() throws Exception {
		when(workflows.call(any())).thenThrow(new BizException(ErrorCode.FORBIDDEN.toError()));
		MvcResult result = request("workflow", false);
		assertEquals(403, result.getResponse().getStatus());
		assertTrue(result.getResponse().getContentAsString().contains(ErrorCode.FORBIDDEN.getCode()));
	}

	@Test
	void agentStreamSendsBusinessErrorEvent() throws Exception {
		when(agents.streamCall(any())).thenReturn(Flux.error(new BizException(ErrorCode.FORBIDDEN.toError())));
		assertStreamError("chat");
	}

	@Test
	void workflowStreamSendsBusinessErrorEvent() throws Exception {
		when(workflows.streamCall(any())).thenReturn(Flux.error(new BizException(ErrorCode.FORBIDDEN.toError())));
		assertStreamError("workflow");
	}

	private void assertStreamError(String kind) throws Exception {
		MvcResult initial = request(kind, true);
		assertTrue(initial.getRequest().isAsyncStarted());
		initial.getAsyncResult(3000);
		MvcResult completed = mvc.perform(asyncDispatch(initial)).andReturn();
		assertEquals("no", completed.getResponse().getHeader("X-Accel-Buffering"));
		assertEquals(403, completed.getResponse().getStatus());
		assertTrue(completed.getResponse().getContentAsString().contains(ErrorCode.FORBIDDEN.getCode()));
	}

	private MvcResult request(String kind, boolean stream) throws Exception {
		return mvc
			.perform(post("/api/v1/apps/" + kind + "/completions").header("Authorization", "Bearer secret")
				.contentType(MediaType.APPLICATION_JSON)
				.characterEncoding("UTF-8")
				.content("{\"stream\":" + stream + "}"))
			.andReturn();
	}

}
