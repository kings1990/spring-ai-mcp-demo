package io.kings1990.mcp.mcpserver.record;

import io.kings1990.mcp.mcpserver.enums.WeatherType;

public record WeatherResult(
        String city,
        WeatherType weather,       // 建议用枚举值，例如 SUNNY/CLOUDY/RAIN
        String temperature,   // 或者用 double + unit
        String unit,
        String source
) {}
