package com.github.micycle1.mqrtree;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;

public class MQRTreeBenchmark {

	private MQRTree<String> tree;
	private List<Datum> bruteForceList; // used for brute-force comparisons

	// A simple class to hold a point (or region) with its associated key.
	private static class Datum {
		String key;
		Envelope env;
		Coordinate center;

		Datum(String key, Envelope env) {
			this.key = key;
			this.env = new Envelope(env);
			double cx = (env.getMinX() + env.getMaxX()) / 2.0;
			double cy = (env.getMinY() + env.getMaxY()) / 2.0;
			this.center = new Coordinate(cx, cy);
		}
	}

	@BeforeEach
	public void setUp() {
		tree = new MQRTree<>();
		bruteForceList = new ArrayList<>();
	}

	@ParameterizedTest
	@ValueSource(ints = { 10_000, 50_000, 100_000 })
	public void benchmarkPointalEnvelopeInsertion(int num) {
		Random rnd = new Random();
		long startTime = System.currentTimeMillis();

		for (int i = 0; i < num; i++) {
			double x = rnd.nextDouble() * 1000;
			double y = rnd.nextDouble() * 1000;
			tree.insert(null, new Envelope(x, x, y, y));
		}

		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;
		System.out.println("Insertion of " + num + " points took " + duration + " ms.");
	}

	@ParameterizedTest
	@ValueSource(ints = { 10_000, 50_000, 100_000 })
	public void benchmarkRectEnvelopeInsertion(int num) {
		Random rnd = new Random();
		long startTime = System.currentTimeMillis();

		for (int i = 0; i < num; i++) {
			// Generate random rectangular envelope
			double minX = rnd.nextDouble() * 1000;
			double minY = rnd.nextDouble() * 1000;
			double maxX = minX + rnd.nextDouble() * 1000; // Random width (up to 100 units)
			double maxY = minY + rnd.nextDouble() * 1000; // Random height (up to 100 units)
			Envelope rectEnv = new Envelope(minX, maxX, minY, maxY);

			// Insert the rectangular envelope
			tree.insert(null, rectEnv);
		}

		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;
		System.out.println("Insertion of " + num + " rectangular envelopes took " + duration + " ms.");
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 10, 100, 1000 })
	public void benchmarkKnnQuery(int k) {
		Random rnd = new Random();
		int numPoints = 100_000; // Number of points to insert
		for (int i = 0; i < numPoints; i++) {
			double x = rnd.nextDouble() * 1000;
			double y = rnd.nextDouble() * 1000;
			insertAndRecord(null, new Envelope(x, x, y, y));
		}

		int q = 10;
		// int k = 100; // Number of nearest neighbors to find
		long startTime = System.currentTimeMillis();
		for (int i = 0; i < q; i++) {
			Coordinate query = new Coordinate(rnd.nextDouble(1000), rnd.nextDouble(1000)); // Center of the space
			List<String> knnResults = tree.knnSearch(query, k);
		}
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;

		// Perform k-NN query

		System.out.println(q + " k-NN query with k=" + k + " took " + duration + " ms.");
	}

	@Test
	public void benchmarkRegionQuery() {
		Random rnd = new Random();
		int numPoints = 100_000; // Number of points to insert
		for (int i = 0; i < numPoints; i++) {
			double x = rnd.nextDouble() * 1000;
			double y = rnd.nextDouble() * 1000;
			insertAndRecord(null, new Envelope(x, x, y, y));
		}

		// Define a region to search
		Envelope searchEnv = new Envelope(400, 600, 400, 600); // A 200x200 region in the center
		long startTime = System.currentTimeMillis();
		List<String> regionResults = tree.search(searchEnv);
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;

		System.out.println("Region query took " + duration + " ms. Found " + regionResults.size() + " results.");
	}

	// Helper method to insert into tree and record in bruteForceList
	private void insertAndRecord(String key, Envelope env) {
		tree.insert(key, env);
		bruteForceList.add(new Datum(key, env));
	}

}
