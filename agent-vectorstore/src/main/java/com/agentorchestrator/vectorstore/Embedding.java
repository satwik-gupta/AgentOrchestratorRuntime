package com.agentorchestrator.vectorstore;

import java.util.Arrays;
import java.util.Objects;

public final class Embedding {
    private final double[] vector;

    public Embedding(double[] vector) {
        Objects.requireNonNull(vector, "vector");
        if (vector.length == 0) throw new IllegalArgumentException("embedding must have positive dimension");
        this.vector = vector.clone();
    }

    public int dimension() {
        return vector.length;
    }

    /**
     * Returns a defensive copy of the internal array.
     */
    public double[] toArray() {
        return vector.clone();
    }

    /**
     * Package-private direct access for internal high-performance calculations.
     */
    double[] internalArray() {
        return vector;
    }

    public double dot(Embedding other) {
        validateSameDimension(other);
        double[] a = this.vector;
        double[] b = other.vector;
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    public double norm() {
        double s = 0.0;
        for (int i = 0; i < vector.length; i++) {
            double v = vector[i];
            s += v * v;
        }
        return Math.sqrt(s);
    }

    private void validateSameDimension(Embedding other) {
        Objects.requireNonNull(other, "other");
        if (this.dimension() != other.dimension()) {
            throw new IllegalArgumentException("Embedding dimension mismatch: " + this.dimension() + " vs " + other.dimension());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Embedding)) return false;
        Embedding that = (Embedding) o;
        return Arrays.equals(this.vector, that.vector);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(vector);
    }
}
