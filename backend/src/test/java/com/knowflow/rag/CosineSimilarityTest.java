package com.knowflow.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosineSimilarityTest {

    @Test
    void identicalVectorsScoreOne() {
        float[] vector = {1f, 2f, 3f};
        assertEquals(1.0, CosineSimilarity.score(vector, vector), 0.0001);
    }

    @Test
    void orthogonalVectorsScoreZero() {
        assertEquals(0.0, CosineSimilarity.score(new float[] {1f, 0f}, new float[] {0f, 1f}), 0.0001);
    }

    @Test
    void mismatchedLengthIsIgnored() {
        assertTrue(Double.isInfinite(CosineSimilarity.score(new float[] {1f}, new float[] {1f, 2f})));
    }
}
