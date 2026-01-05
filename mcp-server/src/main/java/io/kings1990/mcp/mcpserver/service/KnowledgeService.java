package io.kings1990.mcp.mcpserver.service;

import io.kings1990.mcp.mcpserver.annotation.McpToolService;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@McpToolService
public class KnowledgeService {

    private static final String KB_PATH = "knowledges/知识库.md";

    /** 章节列表（按出现顺序） */
    private volatile List<Section> sections = List.of();

    /** 倒排索引：token -> sectionIds */
    private final Map<String, Set<String>> invertedIndex = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        reloadKnowledge();
    }

    /** 手动刷新（可选：如果你会更新 md 文件） */
    @Tool(name = "reloadKnowledge", description = "重新加载知识库文件（管理员/运维用）")
    public String reloadKnowledgeTool() {
        log.info("reloadKnowledgeTool called");
        reloadKnowledge();
        return "Knowledge reloaded. sections=" + sections.size();
    }

    private void reloadKnowledge() {
        String md = readResource(KB_PATH);
        if (md == null) {
            log.warn("Knowledge file not found: {}", KB_PATH);
            sections = List.of();
            invertedIndex.clear();
            return;
        }
        List<Section> parsed = parseMarkdownToSections(md);
        Map<String, Set<String>> index = buildInvertedIndex(parsed);

        this.sections = parsed;
        this.invertedIndex.clear();
        this.invertedIndex.putAll(index);

        log.info("Knowledge loaded. sections={}, indexKeys={}", sections.size(), invertedIndex.size());
    }

    /** 1) 目录：很省 token */
    @Tool(name = "listKnowledgeToc", description = "获取知识库目录（按标题层级展示），用于定位需要的章节")
    public String listKnowledgeToc(@ToolParam(description = "最多返回多少条目录项，默认100") Integer limit) {
        log.info("listKnowledgeToc: limit={}", limit);
        int lim = (limit == null || limit <= 0) ? 100 : Math.min(limit, 500);

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Section s : sections) {
            if (count++ >= lim) break;
            sb.append(s.getId())
                    .append(" | ")
                    .append("  ".repeat(Math.max(0, s.getLevel() - 1)))
                    .append("- ")
                    .append(s.getTitle())
                    .append("\n");
        }
        if (sections.size() > lim) sb.append("... (truncated, total=").append(sections.size()).append(")\n");
        return sb.toString();
    }

    /** 2) 搜索：默认走这个，返回少量片段 */
    @Tool(name = "searchKnowledge", description = "按关键词搜索知识库，返回最相关的片段（推荐优先使用，避免返回全文浪费token）")
    public String searchKnowledge(
            @ToolParam(description = "搜索关键词/问题") String query,
            @ToolParam(description = "返回TopK结果，默认5") Integer topK,
            @ToolParam(description = "每个片段最多返回字符数，默认400") Integer snippetChars
    ) {
        log.info("searchKnowledge: query={}, topK={}, snippetChars={}", query, topK, snippetChars);
        if (query == null || query.isBlank()) return "query is blank";

        int k = (topK == null || topK <= 0) ? 5 : Math.min(topK, 10);
        int snip = (snippetChars == null || snippetChars <= 0) ? 400 : Math.min(snippetChars, 2000);

        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) return "no valid tokens from query";

        // 候选章节集合：把各 token 的命中集合并
        Set<String> candidateIds = new HashSet<>();
        for (String t : tokens) {
            Set<String> ids = invertedIndex.get(t);
            if (ids != null) candidateIds.addAll(ids);
        }
        if (candidateIds.isEmpty()) return "no match";

        // 简单打分：命中 token 越多分越高 + 内容包含原query加分
        Map<String, Integer> score = new HashMap<>();
        for (String id : candidateIds) {
            Section s = findById(id);
            if (s == null) continue;
            int sc = 0;
            String contentLower = s.getContent().toLowerCase(Locale.ROOT);
            for (String t : tokens) {
                if (contentLower.contains(t)) sc += 2;
            }
            if (contentLower.contains(query.toLowerCase(Locale.ROOT))) sc += 3;
            score.put(id, sc);
        }

        List<Section> ranked = candidateIds.stream()
                .map(this::findById)
                .filter(Objects::nonNull)
                .sorted((a, b) -> Integer.compare(score.getOrDefault(b.id, 0), score.getOrDefault(a.id, 0)))
                .limit(k)
                .toList();

        StringBuilder sb = new StringBuilder();
        for (Section s : ranked) {
            sb.append("[").append(s.getId()).append("] ").append(s.getTitle()).append("\n");
            sb.append(truncate(s.getContent(), snip)).append("\n");
            sb.append("----\n");
        }
        return sb.toString();
    }

    /** 3) 按章节取内容：需要更多细节时再调用 */
    @Tool(name = "getKnowledgeSection", description = "按章节ID获取内容（建议配合目录/搜索使用，避免一次返回全文浪费token）")
    public String getKnowledgeSection(
            @ToolParam(description = "章节ID（从 listKnowledgeToc/searchKnowledge 得到）") String id,
            @ToolParam(description = "最多返回字符数，默认2000") Integer maxChars
    ) {
        if (id == null || id.isBlank()) return "id is blank";
        int max = (maxChars == null || maxChars <= 0) ? 2000 : Math.min(maxChars, 20000);

        Section s = findById(id.trim());
        if (s == null) return "section not found: " + id;

        return "[" + s.getId() + "] " + s.getTitle() + "\n" + truncate(s.getContent(), max);
    }

    // ----------------- helpers -----------------

    private Section findById(String id) {
        // sections 不大时线性查找足够；大了可加 Map<String, Section>
        for (Section s : sections) {
            if (s.id.equals(id)) return s;
        }
        return null;
    }

    private String readResource(String path) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("readResource error: {}", path, e);
            return null;
        }
    }

    private List<Section> parseMarkdownToSections(String md) {
        // 按标题切分：遇到 #/##/### 开新 section
        List<Section> result = new ArrayList<>();
        String[] lines = md.replace("\r\n", "\n").split("\n");

        Section current = null;
        int seq = 0;
        for (String line : lines) {
            Heading h = parseHeading(line);
            if (h != null) {
                if (current != null) {
                    current.content = current.content.strip();
                    result.add(current);
                }
                current = new Section();
                current.id = String.format("S%04d", ++seq);
                current.level = h.level;
                current.title = h.title;
                current.content = "";
            } else {
                if (current == null) {
                    // 没有标题前的内容，归到一个“前言”段
                    current = new Section();
                    current.id = String.format("S%04d", ++seq);
                    current.level = 1;
                    current.title = "前言";
                    current.content = "";
                }
                current.content += line + "\n";
            }
        }
        if (current != null) {
            current.content = current.content.strip();
            result.add(current);
        }
        return result;
    }

    private Map<String, Set<String>> buildInvertedIndex(List<Section> secs) {
        Map<String, Set<String>> idx = new HashMap<>();
        for (Section s : secs) {
            String combined = (s.title + "\n" + s.content).toLowerCase(Locale.ROOT);
            for (String t : tokenize(combined)) {
                idx.computeIfAbsent(t, k -> new HashSet<>()).add(s.id);
            }
        }
        return idx;
    }

    /**
     * 简单分词：
     * - 英文按非字母数字切
     * - 中文按连续中文字符块切（不做真正分词，但足以减少“全文返回”的问题）
     * 你后续可以替换成 IK / jieba / lucene analyzer
     */
    private List<String> tokenize(String text) {
        if (text == null) return List.of();
        String lower = text.toLowerCase(Locale.ROOT);

        List<String> tokens = new ArrayList<>();

        // 英文/数字
        for (String t : lower.split("[^a-z0-9]+")) {
            if (t.length() >= 2) tokens.add(t);
        }

        // 中文块（连续中文）
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (isCjk(c)) {
                buf.append(c);
            } else {
                if (buf.length() >= 2) tokens.add(buf.toString());
                buf.setLength(0);
            }
        }
        if (buf.length() >= 2) tokens.add(buf.toString());

        // 去重并限制一下，避免超长输入导致索引膨胀
        return tokens.stream().distinct().limit(50).toList();
    }

    private boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF);
    }

    private String truncate(String s, int maxChars) {
        if (s == null) return "";
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars) + "\n... (truncated, maxChars=" + maxChars + ")";
    }

    private Heading parseHeading(String line) {
        if (line == null) return null;
        String trimmed = line.stripLeading();
        int i = 0;
        while (i < trimmed.length() && trimmed.charAt(i) == '#') i++;
        if (i == 0) return null;
        if (i < trimmed.length() && trimmed.charAt(i) == ' ') {
            String title = trimmed.substring(i + 1).strip();
            if (!title.isEmpty()) {
                Heading h = new Heading();
                h.level = i;
                h.title = title;
                return h;
            }
        }
        return null;
    }

    @Data
    static class Section {
        private String id;
        private int level;
        private String title;
        private String content;
    }

    static class Heading {
        int level;
        String title;
    }
}
