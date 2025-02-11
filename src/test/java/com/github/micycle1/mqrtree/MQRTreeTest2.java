package com.github.micycle1.mqrtree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Envelope;

// Assuming the classes MQRTree, MQRTree.Node, MQRTree.Entry, MQRTree.Quadrant, and Envelope are in package "mqr"
// import your.mqr.MQRTree;
// import your.mqr.Envelope;

public class MQRTreeTest2 {

	/**
	 * This test inserts several envelopes into an MQRTree and then checks that: (1)
	 * Each node's MBR exactly equals the union of its children's MBRs. (2) Each
	 * child's assigned quadrant key matches the computed quadrant relative to the
	 * parent node's centroid.
	 */
	@Test
	public void testTreeInvariants() {
		// Create a new tree.
		MQRTree<Integer> tree = new MQRTree<>();

		// Insert some sample envelopes. You can adjust these values to get a variety of
		// quadrants.
		tree.insert(1, new Envelope(10, 10, 20, 20));
		tree.insert(2, new Envelope(15, 15, 25, 25));
		tree.insert(3, new Envelope(5, 12, 12, 18));
		tree.insert(4, new Envelope(30, 30, 40, 40));
		tree.insert(5, new Envelope(22, 8, 28, 16));

		// Get the tree's root node.
		MQRTree.Node<Integer> root = tree.root;
		if (root == null) {
			fail("Tree root is null.");
		}

		// Recursively check invariants across all nodes.
		checkNodeInvariants(root);
	}

	@ParameterizedTest
	@ValueSource(ints = { 750 })
	public void benchmarkRectEnvelopeInsertionSorted(int num) {
		MQRTree<Integer> tree = new MQRTree<>();
		Random rnd = new Random();
		List<Envelope> envelopes = new ArrayList<>();

		for (int i = 0; i < num; i++) {
			double x = rnd.nextDouble() * 1000;
			double y = rnd.nextDouble() * 1000;
			envelopes.add(new Envelope(x, x, y, y));
		}

		long startTime = System.currentTimeMillis();

		var e = EnvelopeMortonComparator.computeGlobalEnvelope(envelopes);
//		envelopes.sort(new EnvelopeMortonComparator(e.getMinX(), e.getMinY(), e.getMaxX(), e.getMaxY()));

		for (Envelope envelope : envelopes) {
			tree.insert(null, envelope);
		}

		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;
		System.out.println("Insertion of " + num + " rectangular envelopes took " + duration + " ms.");

		// Get the tree's root node.
		var root = tree.root;
		if (root == null) {
			fail("Tree root is null.");
		}

		// Recursively check invariants across all nodes.
		checkNodeInvariants(root);
	}

	/**
	 * Recursively checks the invariants for a node: 1. The node's MBR equals the
	 * union of child entry MBRs. 2. Each child's quadrant key matches the expected
	 * quadrant computed from the node's MBR.
	 *
	 * @param node The node being checked.
	 */
	private void checkNodeInvariants(MQRTree.Node<Integer> node) {
		// Calculate the union of all children's MBRs.
		Envelope union = null;
		assertTrue(node.children.size() <= 5);
		for (Map.Entry<MQRTree.Quadrant, MQRTree.Entry<Integer>> e : node.children.entrySet()) {
			MQRTree.Entry<Integer> childEntry = e.getValue();
			if (childEntry == null) {
				continue;
			}
			if (union == null) {
				union = new Envelope(childEntry.mbr); // Copy constructor assumed.
			} else {
				union.expandToInclude(childEntry.mbr);
			}

			// Compute the expected quadrant.
			MQRTree.Quadrant expectedQuad = computeQuadrant(childEntry.mbr, node.mbr);
			// Compare with the key stored in the children map.
			assertEquals(expectedQuad, e.getKey(), "Child quadrant mismatch");

			// If the entry represents an internal node, recursively check its invariants.
			if (childEntry.isNode()) {
				checkNodeInvariants(childEntry.child);
			}
		}

		// Test 1: The node's MBR should equal the union of its children's MBRs (if any
		// children exist).
		if (union != null) {
			assertEquals(union.getMinX(), node.mbr.getMinX(), 1e-9, "Node MBR minX incorrect");
			assertEquals(union.getMinY(), node.mbr.getMinY(), 1e-9, "Node MBR minY incorrect");
			assertEquals(union.getMaxX(), node.mbr.getMaxX(), 1e-9, "Node MBR maxX incorrect");
			assertEquals(union.getMaxY(), node.mbr.getMaxY(), 1e-9, "Node MBR maxY incorrect");
		}
	}

	/**
	 * Computes the quadrant for an envelope relative to a given node's MBR. The
	 * quadrant is determined by comparing the centroid of the envelope with the
	 * centroid of the node's MBR. If they are equal (within a tolerance), CENTER is
	 * returned.
	 *
	 * @param entryEnv The envelope of the entry (child).
	 * @param nodeMBR  The MBR of the node (parent).
	 * @return The quadrant in which entryEnv lies relative to nodeMBR.
	 */
	private MQRTree.Quadrant computeQuadrant(Envelope entryEnv, Envelope nodeMBR) {
		double entryCx = (entryEnv.getMinX() + entryEnv.getMaxX()) / 2.0;
		double entryCy = (entryEnv.getMinY() + entryEnv.getMaxY()) / 2.0;
		double nodeCx = (nodeMBR.getMinX() + nodeMBR.getMaxX()) / 2.0;
		double nodeCy = (nodeMBR.getMinY() + nodeMBR.getMaxY()) / 2.0;
		final double EPS = 1e-9;

		if (Math.abs(entryCx - nodeCx) < EPS && Math.abs(entryCy - nodeCy) < EPS) {
			return MQRTree.Quadrant.CENTER;
		}
		if (entryCx < nodeCx) {
			if (entryCy < nodeCy) {
				return MQRTree.Quadrant.SW;
			} else {
				return MQRTree.Quadrant.NW;
			}
		} else { // entryCx >= nodeCx
			if (entryCy >= nodeCy) {
				return MQRTree.Quadrant.NE;
			} else {
				return MQRTree.Quadrant.SE;
			}
		}
	}
}
