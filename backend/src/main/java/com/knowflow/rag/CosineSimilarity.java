package com.knowflow.rag;

public final class CosineSimilarity {

    private CosineSimilarity() {
    }

    public static double score(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || left.length != right.length) {
            return Double.NEGATIVE_INFINITY;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            double a = left[i];
            double b = right[i];
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
