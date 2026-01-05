package io.kings1990.mcp.mcpserver.service;

import io.kings1990.mcp.mcpserver.annotation.McpToolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
@Slf4j
@McpToolService
public class KnowledgeService {
    @Tool(name = "getKnowledge", description = "查询知识库内容")
    public String getMdKnowledge() {
        log.info("MCP Tool getKnowledge called");
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("knowledges/知识库.md")) {
            if (inputStream == null) {
                return "File not found: 中台.md";
            }
            return new String(inputStream.readAllBytes());
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }
}
