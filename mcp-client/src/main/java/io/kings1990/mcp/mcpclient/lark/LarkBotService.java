package io.kings1990.mcp.mcpclient.lark;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class LarkBotService {

    @Resource
    private ChatClient chatClient;
    
    @Resource
    private SendMessageUtil sendMessageUtil;

    public void onUserMessage(String messageId, String userText) {
        try {
            sendMessageUtil.forwardMsgToFeishu(messageId, "AI 正在思考中...", "🤖 AI 回复");
            String ai = chatClient.prompt(userText).call().content();
            sendMessageUtil.forwardMsgToFeishu(messageId, ai, "🤖 AI 回复");
        } catch (Exception e) {
            try {
                sendMessageUtil.forwardMsgToFeishu(messageId,
                        "抱歉，我处理失败了：\n\n" + e.getMessage(),
                        "⚠️ 错误");
            } catch (Exception ignore) {}
        }
    }
}