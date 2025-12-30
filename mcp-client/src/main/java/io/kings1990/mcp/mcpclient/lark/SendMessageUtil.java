package io.kings1990.mcp.mcpclient.lark;

import cn.hutool.json.JSONUtil;
import com.google.gson.JsonParser;
import com.lark.oapi.Client;
import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.service.im.v1.model.ReplyMessageReq;
import com.lark.oapi.service.im.v1.model.ReplyMessageReqBody;
import com.lark.oapi.service.im.v1.model.ReplyMessageResp;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SendMessageUtil {

    private static final int CHUNK_SIZE = 3500;

    private static final Pattern CODE_BLOCK =
            Pattern.compile("```(\\w+)?\\n([\\s\\S]*?)\\n```", Pattern.MULTILINE);

    @Resource
    private Client larkApiClient;
    

    /** content 必须是 post 的 content JSON（即 {"zh_cn":{...}}），msgType=post */
    public void sendReply(String messageId,String content) throws Exception {
        ReplyMessageReq req = ReplyMessageReq.newBuilder()
                .messageId(messageId)
                .replyMessageReqBody(ReplyMessageReqBody.newBuilder()
                        .content(content)
                        .msgType("post")
                        .build())
                .build();

        ReplyMessageResp resp = larkApiClient.im().v1().message().reply(req);

        if (!resp.success()) {
            System.out.printf(
                    "code:%s,msg:%s,reqId:%s, resp:%s%n",
                    resp.getCode(),
                    resp.getMsg(),
                    resp.getRequestId(),
                    Jsons.createGSON(true, false).toJson(
                            JsonParser.parseString(new String(resp.getRawResponse().getBody(), StandardCharsets.UTF_8))
                    ));
        }
    }

    public void forwardMsgToFeishu(String messageId,String aiText, String title) throws Exception {
        String normalized = normalize(aiText);
        List<String> chunks = splitByLength(normalized, CHUNK_SIZE);

        for (int i = 0; i < chunks.size(); i++) {
            String t = (chunks.size() == 1) ? title : (title + " (" + (i + 1) + "/" + chunks.size() + ")");
            Map<String, Object> postContent = buildPostPayload(t, chunks.get(i)); // 返回 {"zh_cn":{...}}
            String body = JSONUtil.toJsonStr(postContent);
            sendReply(messageId, body);
        }
    }

    /** 规范化：统一换行 + 去控制字符（保留 \n \t） */
    private static String normalize(String s) {
        if (s == null) return "";
        s = s.replace("\r\n", "\n").replace("\r", "\n");
        s = s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        return s; // 不要 trim
    }

    /** 按长度拆分：不要 trim chunk，否则会吃掉换行 */
    private static List<String> splitByLength(String s, int max) {
        List<String> res = new ArrayList<>();
        if (s.length() <= max) {
            res.add(s);
            return res;
        }
        int start = 0;
        while (start < s.length()) {
            int end = Math.min(start + max, s.length());

            int lastNewline = s.lastIndexOf('\n', end);
            if (lastNewline > start + 200) end = lastNewline;

            res.add(s.substring(start, end)); // 不 trim
            start = end;
        }
        return res;
    }

    /**
     * 返回格式：
     * {
     *   "zh_cn": { "title": "...", "content": [ [ {tag...} ], ... ] }
     * }
     *
     * 普通文本：用 md 输出，但“空行”用独立的 text("\n") 行保证不被折叠
     * 代码块：code_block
     */
    private static Map<String, Object> buildPostPayload(String title, String text) {
        Map<String, Object> root = new HashMap<>();

        Map<String, Object> zhCn = new HashMap<>();
        zhCn.put("title", title);

        List<List<Map<String, Object>>> content = new ArrayList<>();

        int idx = 0;
        Matcher m = CODE_BLOCK.matcher(text);

        StringBuilder mdBuf = new StringBuilder();

        while (m.find()) {
            mdBuf.append(text, idx, m.start());
            flushMdPreserveBlankLines(content, mdBuf); // 关键：保留空行

            String lang = normalizeLang(m.group(1));
            String code = m.group(2);
            content.add(List.of(codeBlock(lang, code)));

            idx = m.end();
        }

        mdBuf.append(text.substring(idx));
        flushMdPreserveBlankLines(content, mdBuf);

        zhCn.put("content", content);
        root.put("zh_cn", zhCn);
        return root;
    }

    private static String normalizeLang(String lang) {
        if (lang == null) return null;
        lang = lang.trim();
        return lang.isEmpty() ? null : lang;
    }

    /**
     * 关键方法：把普通文本刷出到 content，并且“空行”用独立行 text("\n") 表示，
     * 这样空行数量不会被 md 渲染折叠。
     */
    private static void flushMdPreserveBlankLines(List<List<Map<String, Object>>> content, StringBuilder mdBuf) {
        if (mdBuf.length() == 0) return;

        String s = mdBuf.toString().replace("\r\n", "\n").replace("\r", "\n");
        mdBuf.setLength(0);

        // 按行处理：连续非空行 -> 合并成一个 md；空行 -> 输出一个 text("\n")
        String[] lines = s.split("\n", -1); // -1 保留末尾空行
        StringBuilder curMd = new StringBuilder();

        for (String line : lines) {
            if (line.isBlank()) {
                // 先输出累计 md
                if (curMd.length() > 0) {
                    content.add(List.of(Map.of("tag", "md", "text", curMd.toString())));
                    curMd.setLength(0);
                }
                // 再输出一个“真正的空行”
                content.add(List.of(text("\n")));
            } else {
                // 非空行：进入 md 缓冲
                if (curMd.length() > 0) curMd.append("\n");
                curMd.append(line);
            }
        }

        if (curMd.length() > 0) {
            content.add(List.of(Map.of("tag", "md", "text", curMd.toString())));
        }
    }

    private static Map<String, Object> text(String t) {
        return Map.of("tag", "text", "text", t);
    }

    private static Map<String, Object> codeBlock(String language, String code) {
        Map<String, Object> map = new HashMap<>();
        map.put("tag", "code_block");
        if (language != null && !language.isBlank()) map.put("language", language);
        map.put("text", code == null ? "" : code);
        return map;
    }
}