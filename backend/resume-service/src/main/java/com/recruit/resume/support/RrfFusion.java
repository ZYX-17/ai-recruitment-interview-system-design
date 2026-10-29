package com.recruit.resume.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion（RRF）。
 *
 * <p>公式：{@code score(d) = Σ 1 / (k + rank_i(d))}，rank 从 1 开始。
 * k 默认 60，用于削弱单路排名第一的绝对优势；输出按分数降序。</p>
 */
public final class RrfFusion {

    public static final int DEFAULT_K = 60;

    private RrfFusion() {
    }

    public static Map<String, Double> fuse(List<String> firstRanking, List<String> secondRanking) {
        return fuse(firstRanking, secondRanking, DEFAULT_K);
    }

    public static Map<String, Double> fuse(List<String> firstRanking, List<String> secondRanking, int k) {
        int safeK = k < 1 ? DEFAULT_K : k;
        Map<String, Double> scores = new LinkedHashMap<>();
        addRanking(scores, firstRanking, safeK);
        addRanking(scores, secondRanking, safeK);
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                        LinkedHashMap::putAll);
    }

    private static void addRanking(Map<String, Double> scores, List<String> ranking, int k) {
        if (ranking == null) {
            return;
        }
        for (int i = 0; i < ranking.size(); i++) {
            String id = ranking.get(i);
            if (id == null || id.isBlank()) {
                continue;
            }
            scores.merge(id, 1.0 / (k + i + 1), Double::sum);
        }
    }
}
