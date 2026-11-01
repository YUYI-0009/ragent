/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.agent.config;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.agent.dao.mapper.AgentStateMapper;
import com.nageoffer.ai.ragent.agent.state.PgAgentStateStore;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.enums.ModelProvider;
import io.agentscope.core.model.transport.HttpTransportConfig;
import io.agentscope.core.model.transport.OkHttpTransport;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.compat.deepseek.DeepSeekFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

/**
 * Agent 引擎装配：模型与状态存储
 */
@Configuration
@ConditionalOnAgentEngine
@RequiredArgsConstructor
public class AgentEngineConfiguration {

    private final AgentProperties agentProperties;
    private final AIModelProperties aiModelProperties;

    @Bean
    public OpenAIChatModel agentChatModel() {
        AgentProperties.Chat chat = agentProperties.getChat();
        if (chat == null || StrUtil.isBlank(chat.getProvider()) || StrUtil.isBlank(chat.getModel())) {
            throw new IllegalStateException("agent.chat.provider / agent.chat.model 未配置");
        }

        Map<String, AIModelProperties.ProviderConfig> providers = aiModelProperties.getProviders();
        AIModelProperties.ProviderConfig provider = providers == null ? null : providers.get(chat.getProvider());
        if (provider == null) {
            throw new IllegalStateException("agent.chat.provider 在 ai.providers 中不存在: " + chat.getProvider());
        }
        String endpointPath = provider.getEndpoints() == null ? null : provider.getEndpoints().get("chat");
        if (StrUtil.isBlank(provider.getUrl()) || StrUtil.isBlank(endpointPath)) {
            throw new IllegalStateException("供应商缺少 url 或 endpoints.chat: " + chat.getProvider());
        }

        // Agent 通道显式使用 OkHttp 连接池。
        // JDK HttpClient 的 HTTP/2 长连接在透明代理后会被静默黑洞，而 JDK 17 没有空闲淘汰，
        // 表现为只收到 meta 后长时间阻塞；OkHttp 按 keepAliveDuration 淘汰空闲连接并支持读超时。
        HttpTransportConfig transportConfig = HttpTransportConfig.builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(120))
                .writeTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofMinutes(5))
                .streamIdleTimeout(Duration.ofMinutes(5))
                .maxIdleConnections(4)
                .keepAliveDuration(Duration.ofSeconds(30))
                .build();

        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .baseUrl(provider.getUrl())
                .endpointPath(endpointPath)
                .apiKey(provider.getApiKey())
                .modelName(chat.getModel())
                .stream(true)
                // 兼容端点普遍无法同时处理 response_format 与工具调用，统一走 generate_response 兜底
                .nativeStructuredOutputWithTools(false)
                .httpTransport(new OkHttpTransport(transportConfig));
        applyProviderFormatter(builder, chat.getProvider());
        return builder.build();
    }

    /*
     * DeepSeek 使用专用 formatter，与通用实现有两处差异：
     *
     * 1. reasoning_content：本轮完整回传，历史仅回传调过工具的轮次；
     *    通用实现则全部回传
     * 2. strict：移除 DeepSeek 不支持的工具定义字段；
     *    当前未设置该字段，仅作防御性处理
     *
     * 兼容性注意：文档要求带 tools 时完整回传历史思考
     * 上述筛选当前实测未报错，但不属于协议保证
     * 若服务端收紧校验，因缺失 reasoning_content 返回 400，可回退通用 formatter，恢复全量回传
     */
    private void applyProviderFormatter(OpenAIChatModel.Builder builder, String providerId) {
        if (ModelProvider.DEEP_SEEK.matches(providerId)) {
            builder.formatter(new DeepSeekFormatter());
        }
    }

    @Bean
    public PgAgentStateStore agentStateStore(AgentStateMapper agentStateMapper) {
        return new PgAgentStateStore(agentStateMapper);
    }
}
