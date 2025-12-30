package io.kings1990.mcp.mcpclient.lark;

import cn.hutool.core.thread.ThreadUtil;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.ws.Client;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class LarkWsListener implements CommandLineRunner, DisposableBean {

    @Resource
    private LarkBotService botService;

    @Resource
    private Client.Builder larkWsBuilder;

    @Override
    public void run(String... args) {
        //verificationToken和 encryptionKey 可选，用于验证和解密事件
        EventDispatcher handler = EventDispatcher.newBuilder("", "")
                .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                    @Override
                    public void handle(P2MessageReceiveV1 event) throws Exception {

                        // 1) messageId 用于 reply
                        String messageId = event.getEvent().getMessage().getMessageId();

                        // 2) content 是 JSON 字符串，需要解析出文本
                        String contentJson = event.getEvent().getMessage().getContent();

                        System.err.println("收到消息: " + contentJson);

                        String userText = LarkMsgParser.extractText(contentJson);

                        ThreadUtil.execAsync(() -> {
                            botService.onUserMessage(messageId, userText);
                        });

                    }
                })
                .build();

        // 建议把 appId/appSecret 放配置文件
        Client wsClient = larkWsBuilder.eventHandler(handler).build();

        wsClient.start();
    }


    @Override
    public void destroy() throws Exception {

    }
}
