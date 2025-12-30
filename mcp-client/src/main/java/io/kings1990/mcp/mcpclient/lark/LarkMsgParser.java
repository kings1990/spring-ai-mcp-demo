package io.kings1990.mcp.mcpclient.lark;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

public class LarkMsgParser {
    public static String extractText(String contentJson) {
        try {
            JSONObject obj = JSONUtil.parseObj(contentJson);
            if (obj.containsKey("text")) {
                return obj.getStr("text");
            }
            // 如果是富文本/别的类型，可以在这里扩展
            return contentJson;
        } catch (Exception e) {
            return contentJson;
        }
    }
}
