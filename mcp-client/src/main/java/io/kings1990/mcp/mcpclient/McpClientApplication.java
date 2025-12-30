package io.kings1990.mcp.mcpclient;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Scanner;

@SpringBootApplication
public class McpClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpClientApplication.class, args);
    }
    
    //CommandLine 模式
//    @Bean
    public CommandLineRunner demo(ChatClient.Builder builder, List<McpSyncClient> mcpSyncClients) {
        return args -> {
            ChatClient chatClient = builder
                    .defaultSystem("你是一个助手，必须调用工具spring-ai-mcp-tools 下的方法，如果工具不可用，就明确说明无法调用工具，不要编造。")
                    // ✅ 注意：这里用 defaultToolCallbacks，而不是 defaultTools
                    .defaultToolCallbacks(
                            SyncMcpToolCallbackProvider.builder()
                                    .mcpClients(mcpSyncClients)
                                    .build()
                    )
                    .build();

            Scanner scanner = new Scanner(System.in);
            while (true) {
               try {
                   System.out.print("\n用户: ");
                   String input = scanner.nextLine();
                   System.out.println("助手: " + chatClient.prompt(input).call().content());
               } catch (Exception e) {
                   System.out.println("助手: 抱歉，我处理失败了：\n\n" + e.getMessage());
               }
            }
        };
    }


}
