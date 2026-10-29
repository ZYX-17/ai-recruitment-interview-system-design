package com.recruit.resume.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RrfFusionTest {

    @Test
    void fusesTwoRankingsWithReciprocalRank() {
        Map<String, Double> scores = RrfFusion.fuse(
                List.of("a", "b", "c"),
                List.of("b", "a"),
                60);

        assertEquals(1.0 / 61 + 1.0 / 62, scores.get("a"), 1e-9);
        assertEquals(1.0 / 62 + 1.0 / 61, scores.get("b"), 1e-9);
        assertEquals(1.0 / 63, scores.get("c"), 1e-9);
        assertTrue(scores.get("a") > scores.get("c"));
    }

    @Test
    void handlesEmptyAndNullRankings() {
        assertTrue(RrfFusion.fuse(null, null).isEmpty());
        assertEquals(1.0 / 61, RrfFusion.fuse(List.of("a"), List.of(), 60).get("a"), 1e-9);
    }

    @Test
    void ignoresBlankIds() {
        Map<String, Double> scores = RrfFusion.fuse(List.of("a", " ", ""), List.of("a"), 60);
        assertEquals(1, scores.size());
    }
}
