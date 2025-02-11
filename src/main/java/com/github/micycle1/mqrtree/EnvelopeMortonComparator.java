package com.github.micycle1.mqrtree;

import java.util.Collection;
import java.util.Comparator;

import org.locationtech.jts.geom.Envelope;

/**
 * Comparator for Envelope objects based on the Morton (Z-order)
 * value computed from the envelope’s centroid.
 *
 * To use, you need to know the overall bounds of your data (minX, minY, maxX, maxY)
 * so that you can normalize envelope centroids to a fixed integer grid.
 */
public class EnvelopeMortonComparator implements Comparator<Envelope> {
    // The bounds for normalization.
    private final double dataMinX;
    private final double dataMinY;
    private final double dataMaxX;
    private final double dataMaxY;

    // The number of bits per coordinate after normalization.
    // For example, 16 bits gives a grid of size 65536 x 65536.
    private final int BITS = 16;
    private final int MAX_COORD = (1 << BITS) - 1; // 2^BITS - 1

    /**
     * Construct a comparator given the overall bounds of the data.
     */
    public EnvelopeMortonComparator(double dataMinX, double dataMinY, double dataMaxX, double dataMaxY) {
        this.dataMinX = dataMinX;
        this.dataMinY = dataMinY;
        this.dataMaxX = dataMaxX;
        this.dataMaxY = dataMaxY;
    }

    /**
     * Compare two envelopes by computing the Morton code of their centroids.
     */
    @Override
    public int compare(Envelope e1, Envelope e2) {
        long code1 = mortonCode(e1);
        long code2 = mortonCode(e2);
        return Long.compare(code1, code2);
    }

    /**
     * Compute the Morton code for an envelope's centroid.
     */
    private long mortonCode(Envelope env) {
        double centerX = (env.getMinX() + env.getMaxX()) / 2.0;
        double centerY = (env.getMinY() + env.getMaxY()) / 2.0;
        int ix = normalize(centerX, dataMinX, dataMaxX);
        int iy = normalize(centerY, dataMinY, dataMaxY);
        return interleave(ix, iy);
    }

    /**
     * Normalize a coordinate value to an integer in the [0, MAX_COORD] range.
     */
    private int normalize(double value, double min, double max) {
        if (max == min) {
            return 0;
        }
        double normalized = (value - min) / (max - min);
        // Clamp to [0, 1] then scale to integer grid.
        normalized = Math.max(0.0, Math.min(1.0, normalized));
        return (int) (normalized * MAX_COORD);
    }

    /**
     * Interleave two 16-bit integers. This produces a 32-bit Morton code.
     *
     * There are highly optimized bit-interleaving algorithms available.
     * For clarity we use an understandable method.
     */
    private long interleave(int x, int y) {
        long z = 0;
        for (int i = 0; i < BITS; i++) {
            // Select the i'th bit from x and y.
            int bitX = (x >> i) & 1;
            int bitY = (y >> i) & 1;
            // Place bits in the proper positions of the result.
            z |= ((long) bitX << (2 * i));
            z |= ((long) bitY << (2 * i + 1));
        }
        return z;
    }
    
    public static Envelope computeGlobalEnvelope(Collection<Envelope> envelopes) {
        if (envelopes == null || envelopes.isEmpty()) {
            throw new IllegalArgumentException("Envelope collection is null or empty.");
        }
        
        // Initialize with extreme values.
        double globalMinX = Double.POSITIVE_INFINITY;
        double globalMinY = Double.POSITIVE_INFINITY;
        double globalMaxX = Double.NEGATIVE_INFINITY;
        double globalMaxY = Double.NEGATIVE_INFINITY;
        
        for (Envelope env : envelopes) {
            // Update global min/max for X.
            if (env.getMinX() < globalMinX) {
                globalMinX = env.getMinX();
            }
            if (env.getMaxX() > globalMaxX) {
                globalMaxX = env.getMaxX();
            }
            
            // Update global min/max for Y.
            if (env.getMinY() < globalMinY) {
                globalMinY = env.getMinY();
            }
            if (env.getMaxY() > globalMaxY) {
                globalMaxY = env.getMaxY();
            }
        }
        
        return new Envelope(globalMinX, globalMinY, globalMaxX, globalMaxY);
    }
}

