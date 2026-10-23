package com.recruit.resume.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 本地 mock embedding：ai-worker 不可用时的降级向量。
 *
 * <p>确定性哈希展开成 512 维并 L2 归一化；不追求语义质量，只保证链路可跑、
 * 同一文本向量稳定。生产环境应关闭降级（{@code EMBED_FALLBACK_MOCK=false}）。</p>
 */
public final class LocalMockEmbedding {

    private LocalMockEmbedding() {
    }

    public static float[] vector(String text, int dims) {
        String source = text == null ? "" : text;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            float[] values = new float[dims];
            int index = 0;
            int counter = 0;
            while (index < dims) {
                byte[] bytes = digest.digest((counter + ":" + source).getBytes(StandardCharsets.UTF_8));
                for (int i = 0; i + 4 <= bytes.length && index < dims; i += 4) {
                    int value = ((bytes[i] & 0xff) << 24)
                            | ((bytes[i + 1] & 0xff) << 16)
                            | ((bytes[i + 2] & 0xff) << 8)
                            | (bytes[i + 3] & 0xff);
                    values[index++] = value / (float) Integer.MAX_VALUE;
                }
                counter++;
            }
            normalize(values);
            return values;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }

    private static void normalize(float[] values) {
        double sum = 0;
        for (float value : values) {
            sum += (double) value * value;
        }
        double norm = Math.sqrt(sum);
        if (norm == 0) {
            return;
        }
        for (int i = 0; i < values.length; i++) {
            values[i] = (float) (values[i] / norm);
        }
    }
}
