package io.kings1990.mcp.mcpserver.config;

import io.kings1990.mcp.mcpserver.annotation.McpToolService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class ToolConfig {

    @Bean
    public ToolCallbackProvider tools(ApplicationContext ctx) {
        Map<String, Object> beans = ctx.getBeansWithAnnotation(McpToolService.class);
        return MethodToolCallbackProvider.builder()
                .toolObjects(beans.values().toArray())
                .build();
    }
}
