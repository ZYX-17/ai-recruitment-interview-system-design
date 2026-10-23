package com.recruit.resume.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalMockEmbeddingTest {

    @Test
    void vectorIsDeterministicAndNormalized() {
        float[] first = LocalMockEmbedding.vector("Java 后端开发", 512);
        float[] second = LocalMockEmbedding.vector("Java 后端开发", 512);

        assertEquals(512, first.length);
        for (int i = 0; i < first.length; i++) {
            assertEquals(first[i], second[i], 1e-6);
        }
        double norm = 0;
        for (float value : first) {
            norm += (double) value * value;
        }
        assertEquals(1.0, Math.sqrt(norm), 1e-5);
    }

    @Test
    void differentTextsProduceDifferentVectors() {
        float[] first = LocalMockEmbedding.vector("Java", 512);
        float[] second = LocalMockEmbedding.vector("产品经理", 512);
        boolean different = false;
        for (int i = 0; i < first.length; i++) {
            if (Math.abs(first[i] - second[i]) > 1e-6) {
                different = true;
                break;
            }
        }
        assertTrue(different);
    }

    @Test
    void handlesBlankText() {
        float[] vector = LocalMockEmbedding.vector(null, 512);
        assertEquals(512, vector.length);
        assertNotEquals(0.0f, vector[0]);
    }
}
