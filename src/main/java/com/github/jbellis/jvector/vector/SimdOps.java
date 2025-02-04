package com.github.jbellis.jvector.vector;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;

import java.util.List;

public class SimdOps {
    public static float simdSum(float[] vector) {
        float sum = 0.0f;
        int vectorizedLength = (vector.length / FloatVector.SPECIES_PREFERRED.length()) * FloatVector.SPECIES_PREFERRED.length();

        // Process the vectorized part
        for (int i = 0; i < vectorizedLength; i += FloatVector.SPECIES_PREFERRED.length()) {
            FloatVector a = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, vector, i);
            sum += a.reduceLanes(VectorOperators.ADD);
        }

        // Process the tail
        for (int i = vectorizedLength; i < vector.length; i++) {
            sum += vector[i];
        }

        return sum;
    }

    public static float[] simdSum(List<float[]> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            throw new IllegalArgumentException("Input list cannot be null or empty");
        }

        int dimension = vectors.get(0).length;
        float[] sum = new float[dimension];

        // Process each vector from the list
        for (float[] vector : vectors) {
            simdAddInPlace(sum, vector);
        }

        return sum;
    }

    /**
     * Divide v1 by v2, in place (v1 will be modified)
     */
    public static void simdDivInPlace(float[] vector, float divisor) {
        int vectorizedLength = (vector.length / FloatVector.SPECIES_PREFERRED.length()) * FloatVector.SPECIES_PREFERRED.length();

        // Process the vectorized part
        for (int i = 0; i < vectorizedLength; i += FloatVector.SPECIES_PREFERRED.length()) {
            var a = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, vector, i);
            var divResult = a.div(divisor);
            divResult.intoArray(vector, i);
        }

        // Process the tail
        for (int i = vectorizedLength; i < vector.length; i++) {
            vector[i] = vector[i] / divisor;
        }
    }

    /**
     * Multiplies v1 by v2, in place (v1 will be modified)
     */
    public static void simdMulInPlace(float[] v1, float[] v2) {
        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }

        int vectorizedLength = (v1.length / FloatVector.SPECIES_PREFERRED.length()) * FloatVector.SPECIES_PREFERRED.length();

        // Process the vectorized part
        for (int i = 0; i < vectorizedLength; i += FloatVector.SPECIES_PREFERRED.length()) {
            var a = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, v1, i);
            var b = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, v2, i);
            var multiplyResult = a.mul(b);
            multiplyResult.intoArray(v1, i);
        }

        // Process the tail
        for (int i = vectorizedLength; i < v1.length; i++) {
            v1[i] = v1[i] * v2[i];
        }
    }

    /**
     * Computes the dot product of the first two floats in each vector
     * at the given offsets
     */
    public static float dot64(float[] v1, int offset1, float[] v2, int offset2) {
        var a = FloatVector.fromArray(FloatVector.SPECIES_64, v1, offset1);
        var b = FloatVector.fromArray(FloatVector.SPECIES_64, v2, offset2);
        var multiplyResult = a.mul(b);
        return multiplyResult.reduceLanes(VectorOperators.ADD);
    }

    public static float dotProduct(float[] v1, float[] v2) {
        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        var sum = FloatVector.zero(FloatVector.SPECIES_PREFERRED);
        int vectorizedLength = FloatVector.SPECIES_PREFERRED.loopBound(v1.length);

        // Process the vectorized part
        for (int i = 0; i < vectorizedLength; i += FloatVector.SPECIES_PREFERRED.length()) {
            var a = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, v1, i);
            var b = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, v2, i);
            sum = sum.add(a.mul(b));
        }

        float res = sum.reduceLanes(VectorOperators.ADD);

        // Process the tail
        for (int i = vectorizedLength; i < v1.length; i++) {
            res += v1[i] * v2[i];
        }

        return res;
    }

    public static int dotProduct(byte[] v1, byte[] v2) {
        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        var sum = IntVector.zero(IntVector.SPECIES_256);
        int vectorizedLength = ByteVector.SPECIES_64.loopBound(v1.length);

        // Process the vectorized part, convert from 8 bytes to 8 ints
        for (int i = 0; i < vectorizedLength; i += ByteVector.SPECIES_64.length()) {
            var a = ByteVector.fromArray(ByteVector.SPECIES_64, v1, i).castShape(IntVector.SPECIES_256, 0);
            var b = ByteVector.fromArray(ByteVector.SPECIES_64, v2, i).castShape(IntVector.SPECIES_256, 0);
            sum = sum.add(a.mul(b));
        }

        int res = sum.reduceLanes(VectorOperators.ADD);

        // Process the tail
        for (int i = vectorizedLength; i < v1.length; i++) {
            res += v1[i] * v2[i];
        }

        return res;
    }

    public static float cosineSimilarity(float[] v1, float[] v2) {
        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }

        var vsum = FloatVector.zero(FloatVector.SPECIES_PREFERRED);
        var vaMagnitude = FloatVector.zero(FloatVector.SPECIES_PREFERRED);
        var vbMagnitude = FloatVector.zero(FloatVector.SPECIES_PREFERRED);

        int vectorizedLength = FloatVector.SPECIES_PREFERRED.loopBound(v1.length);
        // Process the vectorized part, convert from 8 bytes to 8 ints
        for (int i = 0; i < vectorizedLength; i += FloatVector.SPECIES_PREFERRED.length()) {
            var a = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, v1, i);
            var b = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, v2, i);
            vsum = vsum.add(a.mul(b));
            vaMagnitude = vaMagnitude.add(a.mul(a));
            vbMagnitude = vbMagnitude.add(b.mul(b));
        }

        float sum = vsum.reduceLanes(VectorOperators.ADD);
        float aMagnitude = vaMagnitude.reduceLanes(VectorOperators.ADD);
        float bMagnitude = vbMagnitude.reduceLanes(VectorOperators.ADD);

        // Process the tail
        for (int i = vectorizedLength; i < v1.length; i++) {
            sum += v1[i] * v2[i];
            aMagnitude += v1[i] * v1[i];
            bMagnitude += v2[i] * v2[i];
        }

        return (float) (sum / Math.sqrt(aMagnitude * bMagnitude));
    }

    public static float cosineSimilarity(byte[] v1, byte[] v2) {
        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }

        var vsum = IntVector.zero(IntVector.SPECIES_256);
        var vaMagnitude = IntVector.zero(IntVector.SPECIES_256);
        var vbMagnitude = IntVector.zero(IntVector.SPECIES_256);

        int vectorizedLength = ByteVector.SPECIES_64.loopBound(v1.length);
        // Process the vectorized part, convert from 8 bytes to 8 ints
        for (int i = 0; i < vectorizedLength; i += ByteVector.SPECIES_64.length()) {
            var a = ByteVector.fromArray(ByteVector.SPECIES_64, v1, i).castShape(IntVector.SPECIES_256, 0);
            var b = ByteVector.fromArray(ByteVector.SPECIES_64, v2, i).castShape(IntVector.SPECIES_256, 0);
            vsum = vsum.add(a.mul(b));
            vaMagnitude = vaMagnitude.add(a.mul(a));
            vbMagnitude = vbMagnitude.add(b.mul(b));
        }

        int sum = vsum.reduceLanes(VectorOperators.ADD);
        int aMagnitude = vaMagnitude.reduceLanes(VectorOperators.ADD);
        int bMagnitude = vbMagnitude.reduceLanes(VectorOperators.ADD);

        // Process the tail
        for (int i = vectorizedLength; i < v1.length; i++) {
            sum += v1[i] * v2[i];
            aMagnitude += v1[i] * v1[i];
            bMagnitude += v2[i] * v2[i];
        }

        return (float) (sum / Math.sqrt(aMagnitude * bMagnitude));
    }

    public static float squareDistance(float[] v1, float[] v2) {
        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }

        var vdiffSumSquared = FloatVector.zero(FloatVector.SPECIES_PREFERRED);

        int vectorizedLength = FloatVector.SPECIES_PREFERRED.loopBound(v1.length);
        // Process the vectorized part
        for (int i = 0; i < vectorizedLength; i += FloatVector.SPECIES_PREFERRED.length()) {
            var a = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, v1, i);
            var b = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, v2, i);

            var diff = a.sub(b);
            vdiffSumSquared = vdiffSumSquared.add(diff.mul(diff));
        }

        float diffSumSquared = vdiffSumSquared.reduceLanes(VectorOperators.ADD);

        // Process the tail
        for (int i = vectorizedLength; i < v1.length; i++) {
            diffSumSquared += (v1[i] - v2[i]) * (v1[i] - v2[i]);
        }

        return diffSumSquared;
    }

    public static int squareDistance(byte[] v1, byte[] v2) {
        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }

        var vdiffSumSquared = IntVector.zero(IntVector.SPECIES_256);

        int vectorizedLength = ByteVector.SPECIES_64.loopBound(v1.length);
        // Process the vectorized part
        for (int i = 0; i < vectorizedLength; i += ByteVector.SPECIES_64.length()) {
            var a = ByteVector.fromArray(ByteVector.SPECIES_64, v1, i).castShape(IntVector.SPECIES_256, 0);
            var b = ByteVector.fromArray(ByteVector.SPECIES_64, v2, i).castShape(IntVector.SPECIES_256, 0);

            var diff = a.sub(b);
            vdiffSumSquared = vdiffSumSquared.add(diff.mul(diff));
        }

        int diffSumSquared = vdiffSumSquared.reduceLanes(VectorOperators.ADD);

        // Process the tail
        for (int i = vectorizedLength; i < v1.length; i++) {
            diffSumSquared += (v1[i] - v2[i]) * (v1[i] - v2[i]);
        }

        return diffSumSquared;
    }

    /**
     * Adds v2 into v1, in place (v1 will be modified)
     */
    public static void simdAddInPlace(float[] v1, float[] v2) {
        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }

        int vectorizedLength = (v1.length / FloatVector.SPECIES_PREFERRED.length()) * FloatVector.SPECIES_PREFERRED.length();

        // Process the vectorized part
        for (int i = 0; i < vectorizedLength; i += FloatVector.SPECIES_PREFERRED.length()) {
            var a = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, v1, i);
            var b = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, v2, i);
            var addResult = a.add(b);
            addResult.intoArray(v1, i);
        }

        // Process the tail
        for (int i = vectorizedLength; i < v1.length; i++) {
            v1[i] = v1[i] + v2[i];
        }
    }

    /**
     * @return lhs - rhs, element-wise
     */
    public static float[] simdSub(float[] lhs, float[] rhs) {
        if (lhs.length != rhs.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }

        float[] result = new float[lhs.length];
        int vectorizedLength = (lhs.length / FloatVector.SPECIES_PREFERRED.length()) * FloatVector.SPECIES_PREFERRED.length();

        // Process the vectorized part
        for (int i = 0; i < vectorizedLength; i += FloatVector.SPECIES_PREFERRED.length()) {
            var a = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, lhs, i);
            var b = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, rhs, i);
            var subResult = a.sub(b);
            subResult.intoArray(result, i);
        }

        // Process the tail
        for (int i = vectorizedLength; i < lhs.length; i++) {
            result[i] = lhs[i] - rhs[i];
        }

        return result;
    }
}
