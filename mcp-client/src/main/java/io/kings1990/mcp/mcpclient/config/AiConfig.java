package io.kings1990.mcp.mcpclient.config;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, List<McpSyncClient> mcpSyncClients) {
        return builder
                .defaultSystem("你是一个AI助手，必须调用工具 spring-ai-mcp-tools 下的方法，如果工具不可用，就明确说明无法调用工具，不要编造。")
                .defaultToolCallbacks(
                        SyncMcpToolCallbackProvider.builder()
                                .mcpClients(mcpSyncClients)
                                .build()
                )
                .build();
    }
}
