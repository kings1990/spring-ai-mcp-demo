package io.kings1990.mcp.mcpserver.service;

import io.kings1990.mcp.mcpserver.enums.WeatherType;
import io.kings1990.mcp.mcpserver.record.WeatherRequest;
import io.kings1990.mcp.mcpserver.record.WeatherResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WeatherService {


    @Tool(name = "getWeather", description = "查询指定城市的天气")
    public WeatherResult getWeather(@ToolParam(description = "请求参数") WeatherRequest req) {
        log.info("MCP Tool getWeather called, city={}", req.city());
        return new WeatherResult(
                req.city(),
                WeatherType.SUNNY,
                "25°C",
                "°C",
                "mcp:getWeather"
        );
    }
}