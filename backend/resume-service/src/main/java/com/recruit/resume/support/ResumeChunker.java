package com.recruit.resume.support;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简历 Markdown 分块器（对应 docs/b/07-ai-rules.md §7.2）。
 *
 * <ul>
 *   <li>目标 300 token、上限 500 token；按标题/段落边界聚合；</li>
 *   <li>表格整体独立成块；超长表格按行分组，每组重复表头；</li>
 *   <li>每块前缀注入标题路径，如 {@code [工作经历>腾讯]}；</li>
 *   <li>输出 TEXT / TABLE 两类，写入 resume_chunk 并对应 ES 文档。</li>
 * </ul>
 *
 * <p>token 数为估算值：CJK 字符按 1 token，其他字符按 4 字符 ≈ 1 token。
 * 真实 tokenizer 随模型不同，估算足以控制块大小与召回粒度。</p>
 */
public final class ResumeChunker {

    public static final int TARGET_TOKENS = 300;
    public static final int MAX_TOKENS = 500;

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\s*\\|.*\\|\\s*$");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\s*\\|[\\s:|-]+\\|\\s*$");

    private ResumeChunker() {
    }

    /**
     * @param content     已注入标题路径前缀的块内容
     * @param sectionPath 标题路径，如 {@code 工作经历/腾讯}
     * @param chunkType   TEXT 或 TABLE
     */
    public record Chunk(String content, String sectionPath, String chunkType) {
    }

    public static List<Chunk> chunk(String markdown) {
        List<Chunk> chunks = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return chunks;
        }

        Deque<Heading> headings = new ArrayDeque<>();
        List<String> buffer = new ArrayList<>();
        int[] bufferTokens = {0};
        boolean paragraphBreak = false;

        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int index = 0;
        while (index < lines.length) {
            String line = lines[index];

            Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                flushText(chunks, buffer, bufferTokens, sectionPath(headings));
                int level = heading.group(1).length();
                while (!headings.isEmpty() && headings.peekLast().level >= level) {
                    headings.removeLast();
                }
                headings.addLast(new Heading(level, heading.group(2).trim()));
                paragraphBreak = false;
                index++;
                continue;
            }

            if (TABLE_ROW.matcher(line).matches()) {
                flushText(chunks, buffer, bufferTokens, sectionPath(headings));
                List<String> tableLines = new ArrayList<>();
                while (index < lines.length && TABLE_ROW.matcher(lines[index]).matches()) {
                    tableLines.add(lines[index]);
                    index++;
                }
                addTableChunks(chunks, tableLines, sectionPath(headings));
                paragraphBreak = false;
                continue;
            }

            if (line.isBlank()) {
                paragraphBreak = true;
                index++;
                continue;
            }

            for (String piece : splitLongLine(line)) {
                int pieceTokens = estimateTokens(piece);
                if (bufferTokens[0] + pieceTokens > MAX_TOKENS && !buffer.isEmpty()) {
                    flushText(chunks, buffer, bufferTokens, sectionPath(headings));
                }
                if (paragraphBreak && !buffer.isEmpty()) {
                    buffer.add("");
                }
                paragraphBreak = false;
                buffer.add(piece);
                bufferTokens[0] += pieceTokens;
                if (bufferTokens[0] >= TARGET_TOKENS) {
                    flushText(chunks, buffer, bufferTokens, sectionPath(headings));
                }
            }
            index++;
        }
        flushText(chunks, buffer, bufferTokens, sectionPath(headings));
        return chunks;
    }

    private static void flushText(List<Chunk> chunks, List<String> buffer, int[] bufferTokens,
                                  String sectionPath) {
        if (buffer.isEmpty()) {
            return;
        }
        String text = String.join("\n", buffer).trim();
        buffer.clear();
        bufferTokens[0] = 0;
        if (text.isEmpty()) {
            return;
        }
        chunks.add(new Chunk(withPath(sectionPath, text), sectionPath, "TEXT"));
    }

    /**
     * 表格按行分组；每组都保留表头（第一行 + 分隔行），避免召回时丢列语义。
     */
    private static void addTableChunks(List<Chunk> chunks, List<String> tableLines, String sectionPath) {
        if (tableLines.isEmpty()) {
            return;
        }
        int bodyStart = tableLines.size() >= 2
                && TABLE_SEPARATOR.matcher(tableLines.get(1)).matches() ? 2 : 1;
        List<String> header = new ArrayList<>(tableLines.subList(0, bodyStart));
        List<String> body = tableLines.subList(bodyStart, tableLines.size());
        int headerTokens = header.stream().mapToInt(ResumeChunker::estimateTokens).sum();

        if (body.isEmpty()) {
            addTableChunk(chunks, header, sectionPath);
            return;
        }

        List<String> group = new ArrayList<>();
        int groupTokens = headerTokens;
        for (String row : body) {
            int rowTokens = estimateTokens(row);
            if (!group.isEmpty() && groupTokens + rowTokens > MAX_TOKENS) {
                addTableChunk(chunks, concat(header, group), sectionPath);
                group = new ArrayList<>();
                groupTokens = headerTokens;
            }
            group.add(row);
            groupTokens += rowTokens;
        }
        if (!group.isEmpty()) {
            addTableChunk(chunks, concat(header, group), sectionPath);
        }
    }

    private static void addTableChunk(List<Chunk> chunks, List<String> lines, String sectionPath) {
        String content = String.join("\n", lines);
        chunks.add(new Chunk(withPath(sectionPath, content), sectionPath, "TABLE"));
    }

    /**
     * 单行超长（无换行的长段落）按 token 上限切开，避免整块超过 500。
     */
    private static List<String> splitLongLine(String line) {
        if (estimateTokens(line) <= MAX_TOKENS) {
            return List.of(line);
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        double tokens = 0;
        for (int i = 0; i < line.length(); ) {
            int codePoint = line.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            current.append(line, i, i + charCount);
            tokens += isCjk(codePoint) ? 1 : 0.25;
            i += charCount;
            if (tokens >= MAX_TOKENS) {
                parts.add(current.toString());
                current.setLength(0);
                tokens = 0;
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }

    private static String withPath(String sectionPath, String content) {
        return sectionPath == null || sectionPath.isBlank()
                ? content
                : "[" + sectionPath + "]\n" + content;
    }

    private static String sectionPath(Deque<Heading> headings) {
        return headings.stream().map(Heading::title).reduce((a, b) -> a + "/" + b).orElse("");
    }

    /**
     * 估算 token 数：CJK 1 字符 ≈ 1 token，其他 4 字符 ≈ 1 token。
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (isCjk(codePoint)) {
                cjk++;
            } else {
                other++;
            }
            i += Character.charCount(codePoint);
        }
        return cjk + (int) Math.ceil(other / 4.0);
    }

    private static boolean isCjk(int codePoint) {
        return (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0x3040 && codePoint <= 0x30FF)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7AF);
    }

    private record Heading(int level, String title) {
    }
}
