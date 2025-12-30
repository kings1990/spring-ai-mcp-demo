package io.kings1990.mcp.mcpclient.lark;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LarkConfig {

    @Value("${lark.bot.app-id}")
    private String appId;

    @Value("${lark.bot.app-secret}")
    private String appSecret;
    
    
    @Bean
    public com.lark.oapi.Client larkApiClient() {
        return com.lark.oapi.Client.newBuilder(appId, appSecret).build();
    }

    @Bean
    public com.lark.oapi.ws.Client.Builder larkWsBuilder() {
        return new com.lark.oapi.ws.Client.Builder(appId, appSecret);
    }
    
    
}
