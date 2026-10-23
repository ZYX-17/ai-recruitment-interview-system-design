package com.recruit.resume.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeChunkerTest {

    @Test
    void emptyMarkdownReturnsEmpty() {
        assertTrue(ResumeChunker.chunk(null).isEmpty());
        assertTrue(ResumeChunker.chunk("   ").isEmpty());
    }

    @Test
    void injectsHeadingPathAndSplitsBySection() {
        String markdown = """
                # 个人信息
                张三，13800000000，上海

                ## 工作经历

                ### 腾讯
                2019-2022 Java 开发工程师，负责订单系统。

                ### 阿里
                2022-2024 高级开发工程师，负责交易链路。
                """;

        List<ResumeChunker.Chunk> chunks = ResumeChunker.chunk(markdown);

        assertEquals(3, chunks.size());
        assertEquals("个人信息", chunks.get(0).sectionPath());
        assertTrue(chunks.get(0).content().startsWith("[个人信息]"));
        assertEquals("TEXT", chunks.get(0).chunkType());

        // Markdown 层级：H1 个人信息 → H2 工作经历 → H3 腾讯，路径保留完整父子关系
        assertEquals("个人信息/工作经历/腾讯", chunks.get(1).sectionPath());
        assertTrue(chunks.get(1).content().startsWith("[个人信息/工作经历/腾讯]"));
        assertTrue(chunks.get(1).content().contains("负责订单系统"));

        assertEquals("个人信息/工作经历/阿里", chunks.get(2).sectionPath());
        assertTrue(chunks.get(2).content().contains("负责交易链路"));
    }

    @Test
    void longTableIsSplitAndHeaderRepeated() {
        StringBuilder markdown = new StringBuilder("# 教育经历\n\n| 学校 | 专业 | 时间 |\n| --- | --- | --- |\n");
        for (int i = 1; i <= 200; i++) {
            markdown.append("| 学校").append(i).append(" | 计算机科学与技术 | 2019-2023 |\n");
        }

        List<ResumeChunker.Chunk> chunks = ResumeChunker.chunk(markdown.toString());
        List<ResumeChunker.Chunk> tables = chunks.stream()
                .filter(chunk -> "TABLE".equals(chunk.chunkType()))
                .toList();

        assertTrue(tables.size() > 1, "长表格应拆成多块");
        for (ResumeChunker.Chunk table : tables) {
            assertTrue(table.content().startsWith("[教育经历]"));
            assertTrue(table.content().contains("| 学校 | 专业 | 时间 |"), "每块都要重复表头");
            assertTrue(table.content().contains("| --- | --- | --- |"), "每块都要重复分隔行");
        }
        assertTrue(chunks.stream().noneMatch(chunk -> "TEXT".equals(chunk.chunkType())),
                "只有标题和表格时不应产生正文块");
    }

    @Test
    void splitsOverlongParagraph() {
        String longLine = "简历内容".repeat(400); // 1600 个 CJK 字符
        List<ResumeChunker.Chunk> chunks = ResumeChunker.chunk("# 项目经历\n\n" + longLine);

        assertTrue(chunks.size() >= 4);
        for (ResumeChunker.Chunk chunk : chunks) {
            assertTrue(ResumeChunker.estimateTokens(chunk.content()) <= ResumeChunker.MAX_TOKENS + 20,
                    "块内容不能明显超过 500 token");
        }
    }

    @Test
    void tableAndTextAreSeparated() {
        String markdown = """
                # 技能
                熟悉 Java、Spring、MySQL。

                | 技能 | 年限 |
                | --- | --- |
                | Java | 5 |
                | Spring | 4 |
                """;

        List<ResumeChunker.Chunk> chunks = ResumeChunker.chunk(markdown);

        assertEquals(2, chunks.size());
        assertEquals("TEXT", chunks.get(0).chunkType());
        assertEquals("TABLE", chunks.get(1).chunkType());
        assertTrue(chunks.get(1).content().contains("| Java | 5 |"));
        assertFalse(chunks.get(0).content().contains("| 技能 | 年限 |"));
    }
}
