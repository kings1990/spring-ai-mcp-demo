package io.kings1990.mcp.mcpserver.service;

import io.kings1990.mcp.mcpserver.annotation.McpToolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@McpToolService
public class WarehouseService {

    @Tool(name = "getWarehouseData", description = "查询仓库信息")
    public String getWarehouseData() {
        String result = "1、发货共计1000件，其中2C三方300件，2C自营400件，2B三方200件，2B自营100件";
        log.info("MCP Tool getWarehouseData called. result={}", result);
        return result;
    }
}