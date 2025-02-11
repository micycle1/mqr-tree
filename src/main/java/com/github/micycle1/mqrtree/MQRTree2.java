package com.github.micycle1.mqrtree;

import java.util.LinkedList;
import java.util.Queue;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;

import java.util.EnumMap;
import java.util.*;

/**
 * MQRTree – a two-dimensional spatial index based on the MQR-Tree.
 * 
 * Each node has five “locations” (quadrants): NE, NW, SW, SE and the center
 * (EQ). A location holds either an object (along with its MBR) or a pointer to
 * a child node.
 * 
 * This implementation handles node MBR expansion/contraction and reinsertion of
 * “misplaced” objects when the node centroid moves.
 * 
 * @param <T> the type of the stored objects.
 */
public class MQRTree2<T> {

	public enum Quadrant {
		NW, NE, SW, SE, CENTER;
	}

	public enum NodeType {
		NORMAL, CENTER;
	}

	/**
	 * An entry in either a node (a leaf entry) or a subtree pointer.
	 */
	public static class Entry<T> {
		public Envelope mbr; // the minimum bounding rectangle
		public T obj; // non-null only if this entry is an object item
		public Node<T> child; // non-null only if this entry is a subtree pointer

		public Entry(Envelope mbr, T obj) {
			// Always work with a copy of the envelope.
			this.mbr = new Envelope(mbr);
			this.obj = obj;
		}

		public Entry(Envelope mbr, Node<T> child) {
			this.mbr = new Envelope(mbr);
			this.child = child;
		}

		public boolean isNode() {
			return child != null;
		}

		@Override
		public String toString() {
			if (obj != null) {
				return "Entry[obj=" + obj + ", mbr=" + mbr + "]";
			} else {
				return "Entry[child.mbr=" + (child == null ? "null" : child.mbr) + "]";
			}
		}
	}

	/**
	 * A node in the MQRTree.
	 *
	 * Each node has a node–MBR, an associated NodeType (NORMAL or CENTER) and five
	 * locations. For the four off–center quadrants, they are stored in the children
	 * map. The CENTER location is also stored in the children map. (Later during
	 * re–insertion of “center” entries, if several objects share the center, the
	 * CENTER slot becomes a linked–list style bucket.)
	 */
	public static class Node<T> {
		public Envelope mbr; // covers union of all children mbr's
		public NodeType type; // NORMAL or CENTER
		// For quadrant entries. Note: For CENTER, we allow a linked list style chain.
		public EnumMap<Quadrant, Entry<T>> children;
		public Node<T> parent;

		public Node() {
			// Initially a node has an “empty” envelope.
			mbr = null;
			type = NodeType.NORMAL;
			children = new EnumMap<>(Quadrant.class);
			parent = null;
		}

		/**
		 * Returns true if this node has no child entries.
		 */
		public boolean isEmpty() {
			return children.isEmpty();
		}

		/**
		 * Recomputes the union envelope from the children entries.
		 */
		public void recomputeMBR() {
			Envelope env = null;
			// Check every quadrant slot
			for (Entry<T> entry : children.values()) {
				if (entry != null) {
					if (env == null) {
						env = new Envelope(entry.mbr);
					} else {
						env.expandToInclude(entry.mbr);
					}
				}
			}
			mbr = (env == null ? new Envelope() : env);
		}
	}

	/**
	 * A queue item used during insertion.
	 */
	private static class QueueItem<T> {
		public Quadrant quad; // the quadrant in which this entry should be reinserted
		public Entry<T> entry;

		public QueueItem(Quadrant quad, Entry<T> entry) {
			this.quad = quad;
			this.entry = entry;
		}
	}

	// The root node of the tree.
	public Node<T> root;

	public MQRTree2() {
		root = new Node<>();
	}

	/**
	 * Inserts a new object with the given envelope.
	 *
	 * @param obj the object to insert.
	 * @param env the envelope (MBR) for the object.
	 */
	public void insertOrig(T obj, Envelope env) {
		Entry<T> newEntry = new Entry<>(env, obj);
		if (root.isEmpty()) {
			// For an empty tree, simply set the mbr and add the object in the CENTER slot.
			root.mbr = new Envelope(env);
			root.children.put(Quadrant.CENTER, newEntry);
		} else {
			insert(root, newEntry);
		}
	}

	/**
	 * Inserts a new entry into the given node.
	 *
	 * Performs the following: 1. Merge the new entry's envelope in to the node's
	 * envelope. 2. Determine the proper quadrant for the new entry. 3. Enqueue the
	 * new entry. 4. Check all children to see if any no longer reside in the
	 * correct quadrant (shifted) and add them to the reinsertion queue. 5. Process
	 * the entire insertion queue.
	 */
	private void insert(Node<T> node, Entry<T> newEntry) {
		// Save original mbr.
		Envelope origMBR = new Envelope(node.mbr);

		// Adjust node mbr to include new entry.
		node.mbr.expandToInclude(newEntry.mbr);

		// Determine quadrant for new entry relative to the new node mbr.
		Quadrant q = findInsertQuadrant(newEntry.mbr, node.mbr);

		// Create an insertion queue; first add the new entry.
		Queue<QueueItem<T>> queue = new LinkedList<>();
		queue.offer(new QueueItem<>(q, newEntry));

		// Identify any entries whose proper quadrant has changed due to node mbr
		// change.
		findShiftedObjects(queue, node, origMBR);

		// (Re)insert all queued items into the node.
		insertQueue(node, queue);
	}

	/**
	 * Computes the quadrant in which an entry with envelope "entryEnv" should be
	 * placed in a node with mbr "nodeEnv".
	 *
	 * The rule is: - if the centroid of the entry equals the centroid of the node
	 * envelope, return CENTER. - Else, if entry centroid x < node centroid x then
	 * it goes to either NW (if y >= node center) or SW (if y < node center). -
	 * Otherwise, if entry centroid x >= node centroid x then it goes to NE (if y >=
	 * node center) or SE (if y < node center).
	 */
	private Quadrant findInsertQuadrant(Envelope entryEnv, Envelope nodeEnv) {
		// Compute center of node mbr.
		double cx = (nodeEnv.getMinX() + nodeEnv.getMaxX()) / 2.0;
		double cy = (nodeEnv.getMinY() + nodeEnv.getMaxY()) / 2.0;
		// Compute center of entry envelope.
		double ecx = (entryEnv.getMinX() + entryEnv.getMaxX()) / 2.0;
		double ecy = (entryEnv.getMinY() + entryEnv.getMaxY()) / 2.0;

		// If centers match exactly then return CENTER.
		if (Double.compare(ecx, cx) == 0 && Double.compare(ecy, cy) == 0) {
			return Quadrant.CENTER;
		}
		if (ecx < cx) {
			// left side: either NW or SW
			return (ecy >= cy) ? Quadrant.NW : Quadrant.SW;
		} else {
			// Right side: either NE or SE.
			return (ecy >= cy) ? Quadrant.NE : Quadrant.SE;
		}
	}

	/**
	 * Scans the children of the given node to see if any entry that was “correct”
	 * under the old mbr (origMBR) now must be placed in a new quadrant in node.mbr.
	 *
	 * For each entry whose proper quadrant changes, remove it from the node and add
	 * it to the insertion queue with its proper quadrant.
	 *
	 * If the node is a CENTER node then – per the pseudocode – all entries are
	 * removed.
	 */
	private void findShiftedObjects(Queue<QueueItem<T>> queue, Node<T> node, Envelope origMBR) {
		// Compute the center of the new node mbr.
		double newCenterX = (node.mbr.getMinX() + node.mbr.getMaxX()) / 2.0;
		double newCenterY = (node.mbr.getMinY() + node.mbr.getMaxY()) / 2.0;
		// Compute the center of the original node mbr.
		double origCenterX = (origMBR.getMinX() + origMBR.getMaxX()) / 2.0;
		double origCenterY = (origMBR.getMinY() + origMBR.getMaxY()) / 2.0;

		// If the centers did not change then no objects shift.
		if (Double.compare(newCenterX, origCenterX) == 0 && Double.compare(newCenterY, origCenterY) == 0) {
			return;
		}

		// Special case: if the node was previously a CENTER node,
		// then remove all entries.
		if (node.type == NodeType.CENTER) {
			List<Quadrant> keys = new ArrayList<>(node.children.keySet());
			for (Quadrant quad : keys) {
				Entry<T> entry = node.children.remove(quad);
				if (entry != null) {
					// Determine new quadrant using the original envelope to approximate direction.
					Quadrant newQuad = findInsertQuadrant(entry.mbr, node.mbr);
					queue.offer(new QueueItem<>(newQuad, entry));
				}
			}
			// Change node type to NORMAL so reinsertion will reassign appropriate
			// quadrants.
			node.type = NodeType.NORMAL;
			return;
		}

		// For each child currently in the node, recompute its proper quadrant.
		// Iterate over a copy of the key set.
		List<Quadrant> currentQuadrants = new ArrayList<>(node.children.keySet());
		for (Quadrant q : currentQuadrants) {
			Entry<T> entry = node.children.get(q);
			if (entry != null) {
				Quadrant properQuad = findInsertQuadrant(entry.mbr, node.mbr);
				// If the proper quadrant differs then remove and queue it.
				if (properQuad != q) {
					node.children.remove(q);
					queue.offer(new QueueItem<>(properQuad, entry));
				}
			}
		}
	}

	/**
	 * - Processes the insertion queue by inserting each queued item into the proper
	 * location of the node.
	 *
	 * This method will handle the following cases: 1) If the target quadrant is
	 * empty, simply assign the entry. 2) If the node is of type CENTER and the
	 * target quadrant is CENTER, then the new entry is stored by either placing it
	 * into the center slot or by forming a chain. 3) If the target quadrant already
	 * contains a leaf entry, then a new child node is created. 4) If the target
	 * quadrant contains a subtree, then the insertion recurses in that subtree.
	 */
	private void insertQueue(Node<T> node, Queue<QueueItem<T>> queue) {
		while (!queue.isEmpty()) {
			QueueItem<T> item = queue.poll();
			Quadrant targetQuad = item.quad;
			Entry<T> entry = item.entry;
			// System.out.println("Reinserting " + entry + " into quadrant " + targetQuad);
			if (targetQuad == Quadrant.CENTER) {
				// Insert into the CENTER branch.
				// When inserting into center: if the CENTER slot is empty, place the entry.
				// If there is already an entry there, then either the existing entry is a leaf
				// entry
				// or it is a node; if a leaf then create a new node (center chain) to hold
				// both.
				Entry<T> centerEntry = node.children.get(Quadrant.CENTER);
				if (centerEntry == null) {
					node.children.put(Quadrant.CENTER, entry);
					// When an entry is placed in the center, ensure the node type becomes CENTER.
					node.type = NodeType.CENTER;
				} else {
					// CENTER slot is not empty.
					if (centerEntry.child != null) {
						// It is a subtree; recursively insert.
						insert(centerEntry.child, entry);
						// Also adjust the center subtree envelope.
						centerEntry.mbr.expandToInclude(entry.mbr);
					} else {
						// The existing entry is a leaf. Create a chained center node.
						Node<T> centerChain = new Node<>();
						// Initially, the chain mbr covers both entries.
						centerChain.mbr = new Envelope(centerEntry.mbr);
						centerChain.mbr.expandToInclude(entry.mbr);
						// Insert both leaf entries into the new center node.
						centerChain.children.put(Quadrant.CENTER, centerEntry);
						insert(centerChain, entry);
						centerChain.parent = node;
						// Place the new center chain into the CENTER slot.
						node.children.put(Quadrant.CENTER, new Entry<>(centerChain.mbr, centerChain));
						node.type = NodeType.CENTER;
					}
				}
			} else { // For quadrants NW, NE, SW, SE.
				Entry<T> curr = node.children.get(targetQuad);
				if (curr == null) {
					// Slot is empty: insert the entry.
					node.children.put(targetQuad, entry);
				} else {
					if (curr.child != null) {
						// The slot references a subtree; recursively insert into that subtree.
						insert(curr.child, entry);
						// Adjust the envelope stored in the pointer.
						curr.mbr.expandToInclude(entry.mbr);
					} else {
						// The slot already contains a leaf entry. Create a new node to hold both.
						Node<T> newChild = new Node<>();
						// The new child mbr is the union of both envelopes.
						Envelope newChildMBR = new Envelope(curr.mbr);
						newChildMBR.expandToInclude(entry.mbr);
						newChild.mbr = newChildMBR;
						// Insert the two entries into the new child.
						// Determine quadrant for each entry in the new child.
						Quadrant quad1 = findInsertQuadrant(curr.mbr, newChild.mbr);
						Quadrant quad2 = findInsertQuadrant(entry.mbr, newChild.mbr);
						newChild.children.put(quad1, curr);
						newChild.children.put(quad2, entry);
						newChild.parent = node;
						// Then, update the pointer in the current node.
						node.children.put(targetQuad, new Entry<>(newChild.mbr, newChild));
					}
				}
			}
		}
	}

	// New helper class to represent an insertion task.
	private static class InsertionTask<T> {
		Node<T> node;
		Entry<T> entry;

		InsertionTask(Node<T> node, Entry<T> entry) {
			this.node = node;
			this.entry = entry;
		}
	}

	// A global work queue to process insertion tasks iteratively.
	private final Queue<InsertionTask<T>> insertWorkQueue = new LinkedList<>();

	/**
	 * Modified public insert method that uses an iterative insertion approach to
	 * avoid deep recursion when many overlapping rectangles trigger multiple
	 * knock‐on changes.
	 */
	public void insert(T obj, Envelope env) {
		Entry<T> newEntry = new Entry<>(env, obj);
		if (root.isEmpty()) {
			root.mbr = new Envelope(env);
			root.children.put(Quadrant.CENTER, newEntry);
		} else {
			// Instead of a recursive insert call, add a new task to the work queue.
			insertWorkQueue.offer(new InsertionTask<>(root, newEntry));
			processInsertionTasks();
		}
	}

	/**
	 * Processes pending insertion tasks iteratively.
	 */
	// Set an iteration limit to avoid an infinite loop.
	private static final int MAX_INSERTION_ITERATIONS = 50_000;

	/**
	 * Processes pending insertion tasks iteratively. If the number of insertion
	 * iterations exceeds the limit, the method logs a warning and stops processing
	 * further tasks.
	 */
	private void processInsertionTasks() {
		int iterations = 0;
		while (!insertWorkQueue.isEmpty()) {
			if (iterations++ >= MAX_INSERTION_ITERATIONS) {
				throw new IllegalStateException(
						"Reached maximum insertion iterations (" + MAX_INSERTION_ITERATIONS + "). Some insertions may not be fully rebalanced.");
			}
			InsertionTask<T> task = insertWorkQueue.poll();
			iterativeInsert(task.node, task.entry);
		}
	}

	/**
	 * Performs the insertion logic for a given node and entry without recursion.
	 * This method incorporates the logic for expanding the node's MBR, detecting
	 * shifted entries, and reassigning insert tasks when needed.
	 */
	private void iterativeInsert(Node<T> node, Entry<T> newEntry) {
		// Save the original MBR.
		Envelope origMBR = new Envelope(node.mbr);
		// Expand the node's MBR to include the new entry.
		node.mbr.expandToInclude(newEntry.mbr);

		// Determine the quadrant for the new entry relative to the updated node MBR.
		Quadrant q = findInsertQuadrant(newEntry.mbr, node.mbr);
		// Use a local queue to collect entries that need to be inserted into node.
		Queue<QueueItem<T>> localQueue = new LinkedList<>();
		localQueue.offer(new QueueItem<>(q, newEntry));

		// Check for shifted objects due to the change in node's MBR.
		findShiftedObjects(localQueue, node, origMBR);

		// Process the local insertion queue iteratively.
		while (!localQueue.isEmpty()) {
			QueueItem<T> item = localQueue.poll();
			Quadrant targetQuad = item.quad;
			Entry<T> entry = item.entry;
			if (targetQuad == Quadrant.CENTER) {
				Entry<T> centerEntry = node.children.get(Quadrant.CENTER);
				if (centerEntry == null) {
					node.children.put(Quadrant.CENTER, entry);
					node.type = NodeType.CENTER;
				} else {
					if (centerEntry.child != null) {
						// Instead of recursive call, add an insertion task.
						insertWorkQueue.offer(new InsertionTask<>(centerEntry.child, entry));
						centerEntry.mbr.expandToInclude(entry.mbr);
					} else {
						// Create a new child node for the center chain.
						Node<T> centerChain = new Node<>();
						centerChain.mbr = new Envelope(centerEntry.mbr);
						centerChain.mbr.expandToInclude(entry.mbr);
						centerChain.children.put(Quadrant.CENTER, centerEntry);
						// Schedule insertion into the newly created center node.
						insertWorkQueue.offer(new InsertionTask<>(centerChain, entry));
						centerChain.parent = node;
						node.children.put(Quadrant.CENTER, new Entry<>(centerChain.mbr, centerChain));
						node.type = NodeType.CENTER;
					}
				}
			} else {
				Entry<T> curr = node.children.get(targetQuad);
				if (curr == null) {
					node.children.put(targetQuad, entry);
				} else {
					if (curr.child != null) {
						// Instead of recursive call, add an insertion task.
						insertWorkQueue.offer(new InsertionTask<>(curr.child, entry));
						curr.mbr.expandToInclude(entry.mbr);
					} else {
						// Create a new child node to hold both entries.
						Node<T> newChild = new Node<>();
						Envelope newChildMBR = new Envelope(curr.mbr);
						newChildMBR.expandToInclude(entry.mbr);
						newChild.mbr = newChildMBR;
						Quadrant quad1 = findInsertQuadrant(curr.mbr, newChild.mbr);
						Quadrant quad2 = findInsertQuadrant(entry.mbr, newChild.mbr);
						newChild.children.put(quad1, curr);
						newChild.children.put(quad2, entry);
						newChild.parent = node;
						node.children.put(targetQuad, new Entry<>(newChild.mbr, newChild));
					}
				}
			}
		}
	}

	// --------------
	// The code below shows utility functions that may be useful for
	// further deletion/removal/reinsertion routines (for contraction cases).
	// In our implementation these functions are integrated into insert processing.
	// --------------

	/**
	 * Removes entries from the given subtree that lie within a given region, and
	 * enqueues them for reinsertion.
	 *
	 * This is a recursive routine. If the current location is a leaf entry and its
	 * centroid lies within the region, the entry is removed from the node and
	 * enqueued. If the location references a subtree then each child is examined.
	 *
	 * After processing, the node is adjusted (its mbr is recalculated). If no
	 * children remain, the node may be removed by the caller.
	 *
	 * This method roughly implements the "remove_and_q_objects" pseudocode.
	 */
	private void removeAndQueueObjects(Node<T> node, Quadrant destQuad, Envelope region, Queue<QueueItem<T>> queue) {
		if (node == null || node.isEmpty()) {
			return;
		}
		// For each quadrant in the node.
		List<Quadrant> keys = new ArrayList<>(node.children.keySet());
		for (Quadrant q : keys) {
			Entry<T> entry = node.children.get(q);
			if (entry == null) {
				continue;
			}
			// If the entry's envelope overlaps with the region.
			if (entry.mbr.intersects(region)) {
				// If the entry is a leaf, check the centroid.
				double centX = (entry.mbr.getMinX() + entry.mbr.getMaxX()) / 2.0;
				double centY = (entry.mbr.getMinY() + entry.mbr.getMaxY()) / 2.0;
				double regionCenterX = (region.getMinX() + region.getMaxX()) / 2.0;
				double regionCenterY = (region.getMinY() + region.getMaxY()) / 2.0;
				// If the centroid lies within the region, remove and queue.
				if (centroidWithin(centX, centY, region)) {
					node.children.remove(q);
					queue.offer(new QueueItem<>(destQuad, entry));
				} else if (entry.child != null) {
					// Recursive case: entry is a subtree.
					removeAndQueueObjects(entry.child, destQuad, region, queue);
					// Adjust the subtree envelope after removals.
					entry.child.recomputeMBR();
					// If the subtree becomes empty, remove this pointer.
					if (entry.child.isEmpty()) {
						node.children.remove(q);
					}
				}
			}
		}
		// Recalculate the node's own envelope.
		node.recomputeMBR();
	}

	/**
	 * Utility: Determines if the centroid (x,y) lies within the given region.
	 */
	private boolean centroidWithin(double x, double y, Envelope region) {
		return x >= region.getMinX() && x <= region.getMaxX() && y >= region.getMinY() && y <= region.getMaxY();
	}

	/**
	 * Performs a region search on the entire tree. Returns a list of objects whose
	 * MBRs intersect the given search region.
	 *
	 * @param searchRegion the region to search.
	 * @return a list of objects whose envelopes overlap the searchRegion.
	 */
	public List<T> search(Envelope searchRegion) {
		List<T> results = new ArrayList<>();
		regionSearch(root, searchRegion, results);
		return results;
	}

	/**
	 * Recursively searches the subtree rooted at the given node for all objects
	 * whose MBRs intersect the search region.
	 *
	 * @param node         the current node being searched.
	 * @param searchRegion the region to search.
	 * @param results      the list to collect results.
	 */
	private void regionSearch(Node<T> node, Envelope searchRegion, List<T> results) {
		if (node == null || node.mbr == null || !node.mbr.intersects(searchRegion)) {
			// If the node or its MBR does not intersect the search region, skip.
			return;
		}

		// Examine each child entry in the node.
		for (Entry<T> entry : node.children.values()) {
			if (entry == null || !entry.mbr.intersects(searchRegion)) {
				continue;
			}
			if (entry.child != null) {
				// The entry points to a subtree; recursively search that subtree.
				regionSearch(entry.child, searchRegion, results);
			} else {
				// Leaf entry - add the object if its envelope intersects.
				results.add(entry.obj);
			}
		}
	}

	/**
	 * Performs a k-Nearest Neighbour (k-NN) search using a JTS Coordinate as the
	 * query point.
	 * 
	 * This method first locates a starting node in the tree where the number of
	 * points in the subtree is near k, then collects candidate points, sorts them
	 * by increasing distance from the query point, and validates the candidate set
	 * against the node's "superMBR." If the candidate set is not valid, the search
	 * backtracks upward until a valid set is found or the root is reached.
	 *
	 * @param query the query point as a JTS Coordinate.
	 * @param k     the number of nearest neighbours to search for.
	 * @return a list of k (or more in case of ties) objects that are the nearest
	 *         neighbours.
	 */
	/**
	 * Performs a k-Nearest Neighbour (k-NN) search using a JTS Coordinate as the
	 * query point.
	 * 
	 * This method first locates a starting node in the tree where the number of
	 * points in the subtree is near k, then collects candidate points, sorts them
	 * by increasing distance from the query point, and validates the candidate set
	 * against the node's "superMBR." If the candidate set is not valid, the search
	 * backtracks upward until a valid set is found or the root is reached.
	 *
	 * @param query the query point as a JTS Coordinate.
	 * @param k     the number of nearest neighbours to search for.
	 * @return a list of k (or more in case of ties) objects that are the nearest
	 *         neighbours.
	 */
	public List<T> knnSearchOld(Coordinate query, int k) {
		if (root == null || root.isEmpty()) {
			return new ArrayList<>();
		}

		double qx = query.x;
		double qy = query.y;

		// 1. Locate a "starting node" X.
		Node<T> X = root;
		while (true) {
			int npoints = countPoints(X);
			// Stop if number of points in X is <= k.
			if (npoints <= k) {
				break;
			}
			// Determine the quadrant for the query point relative to X.mbr.
			double cx = (X.mbr.getMinX() + X.mbr.getMaxX()) / 2.0;
			double cy = (X.mbr.getMinY() + X.mbr.getMaxY()) / 2.0;
			Quadrant targetQuad;
			if (qx < cx && qy >= cy) {
				targetQuad = Quadrant.NW;
			} else if (qx >= cx && qy >= cy) {
				targetQuad = Quadrant.NE;
			} else if (qx >= cx && qy < cy) {
				targetQuad = Quadrant.SE;
			} else { // qx < cx && qy < cy
				targetQuad = Quadrant.SW;
			}
			// If the target quadrant exists and references a subtree, descend.
			Entry<T> candidate = X.children.get(targetQuad);
			if (candidate != null && candidate.child != null) {
				X = candidate.child;
			} else {
				break; // Otherwise, we stop the descent.
			}
		}
		// If the candidate count in X is too few, backtrack one level.
		if (countPoints(X) < k && X.parent != null) {
			X = X.parent;
		}

		// 2. Iterate upward until a valid candidate set is found.
		while (true) {
			List<Candidate<T>> candidates = new ArrayList<>();
			traverseSubtree(X, qx, qy, candidates);
			// Sort candidates by increasing distance.
			candidates.sort(Comparator.comparingDouble(c -> c.dist));
			// If not enough candidates, then we must backtrack.
			if (candidates.size() < k) {
				if (X.parent == null) {
					List<T> result = new ArrayList<>();
					for (Candidate<T> cand : candidates) {
						result.add(cand.obj);
					}
					return result;
				}
				X = X.parent;
				continue;
			}
			double kthDistance = candidates.get(k - 1).dist;

			// Instead of comparing the kth candidate to all sides individually,
			// compute the minimum distance from the query point to any edge of the
			// superMBR.
			double dTop = X.mbr.getMaxY() - qy;
			double dBottom = qy - X.mbr.getMinY();
			double dRight = X.mbr.getMaxX() - qx;
			double dLeft = qx - X.mbr.getMinX();
			double minBoundaryDist = Math.min(Math.min(dTop, dBottom), Math.min(dLeft, dRight));

			// Valid if the kth candidate is within the minimum distance.
			if (kthDistance <= minBoundaryDist) {
				List<T> result = new ArrayList<>();
				for (int i = 0; i < k; i++) {
					result.add(candidates.get(i).obj);
				}
				return result;
			} else {
				// Not valid: backtrack upward if possible.
				if (X.parent == null) {
					List<T> result = new ArrayList<>();
					for (int i = 0; i < k; i++) {
						result.add(candidates.get(i).obj);
					}
					return result;
				}
				X = X.parent;
			}
		}
	}

	/**
	 * Helper class representing an element (node) in the best–first search. The
	 * node’s distance is computed as the minimum distance from the query point to
	 * the node’s MBR.
	 */
	private static class KnnEntry<T> implements Comparable<KnnEntry<T>> {
		Node<T> node;
		double distance; // distance from query to node.mbr

		public KnnEntry(Node<T> node, double distance) {
			this.node = node;
			this.distance = distance;
		}

		@Override
		public int compareTo(KnnEntry<T> other) {
			return Double.compare(this.distance, other.distance);
		}
	}

	/**
	 * Returns the kth candidate's distance (after sorting) from the list of
	 * candidates. For small candidate lists this simple sort is acceptable.
	 */
	private double getKthDistance(List<Candidate<T>> candidates, int k) {
		candidates.sort(Comparator.comparingDouble(c -> c.dist));
		return candidates.get(k - 1).dist;
	}

	/**
	 * Performs a k–nearest neighbour (k–NN) search using a best–first search
	 * strategy. The method uses a priority queue to examine nodes in order of
	 * increasing minimum distance from the query point. Leaf entries are collected
	 * as candidates.
	 *
	 * @param query the query point as a JTS Coordinate.
	 * @param k     the number of nearest neighbours.
	 * @return a list of k (or more in the case of ties) objects that are the
	 *         nearest neighbours.
	 */
	/**
	 * Helper class for best-first search. Contains either a tree node or a leaf
	 * candidate.
	 */
	private static class SearchItem<T> implements Comparable<SearchItem<T>> {
		Node<T> node; // non-null if this represents an internal node
		Candidate<T> candidate; // non-null if this represents a leaf candidate
		double distSq; // squared distance from the query point

		// Create a SearchItem from a node.
		public SearchItem(Node<T> node, double distSq) {
			this.node = node;
			this.distSq = distSq;
		}

		// Create a SearchItem from a candidate.
		public SearchItem(Candidate<T> candidate, double distSq) {
			this.candidate = candidate;
			this.distSq = distSq;
		}

		@Override
		public int compareTo(SearchItem<T> other) {
			return Double.compare(this.distSq, other.distSq);
		}
	}

	/**
	 * Revised Candidate class. (Reusing the same candidate definition as before.)
	 */
	private static class Candidate<T> {
		T obj;
		Envelope mbr;
		double dist; // actual distance (not squared)

		Candidate(T obj, Envelope mbr, double dist) {
			this.obj = obj;
			this.mbr = new Envelope(mbr);
			this.dist = dist;
		}
	}

	/**
	 * Revised best-first k-NN search. This method uses a priority queue that holds
	 * both nodes and leaf candidates, ordered by squared distance to avoid
	 * computing expensive square roots multiple times.
	 *
	 * @param query the query point as a JTS Coordinate.
	 * @param k     the number of nearest neighbours to search for.
	 * @return a list of k (or more in the case of ties) objects that are the
	 *         nearest neighbours.
	 */
	public List<T> knnSearch(Coordinate query, int k) {
		PriorityQueue<SearchItem<T>> queue = new PriorityQueue<>();
		List<Candidate<T>> candidateList = new ArrayList<>();

		double qx = query.x, qy = query.y;
		// Start with the root node.
		queue.offer(new SearchItem<>(root, squaredDistanceToEnvelope(qx, qy, root.mbr)));

		while (!queue.isEmpty()) {
			SearchItem<T> curItem = queue.poll();

			// If this item is a candidate, add it to our candidate list
			if (curItem.candidate != null) {
				candidateList.add(curItem.candidate);
			} else if (curItem.node != null) {
				// Expand the node: add its children (both subtrees and leaves) to the queue.
				for (Entry<T> entry : curItem.node.children.values()) {
					if (entry == null)
						continue;
					double dSq = squaredDistanceToEnvelope(qx, qy, entry.mbr);
					if (entry.child != null) {
						queue.offer(new SearchItem<>(entry.child, dSq));
					} else {
						// For leaf candidates, compute actual distance.
						double d = Math.sqrt(dSq);
						Candidate<T> cand = new Candidate<>(entry.obj, entry.mbr, d);
						queue.offer(new SearchItem<>(cand, dSq));
					}
				}
			}
			// Check termination condition: if we have at least k candidates and the next
			// item's squared
			// distance is greater than or equal to the kth candidate's squared distance.
			if (candidateList.size() >= k && !queue.isEmpty()) {
				double kthDistSq = Double.POSITIVE_INFINITY;
				// Determine kth distance squared from candidateList.
				List<Double> candDistSq = new ArrayList<>();
				for (Candidate<T> cand : candidateList) {
					candDistSq.add(cand.dist * cand.dist);
				}
				candDistSq.sort(Double::compare);
				kthDistSq = candDistSq.get(k - 1);
				if (queue.peek().distSq >= kthDistSq) {
					break;
				}
			}
		}

		// Now sort candidates by the true distance.
		candidateList.sort(Comparator.comparingDouble(c -> c.dist));
		// Collect all candidates that are within the kth candidate distance (allowing
		// ties)
		List<T> result = new ArrayList<>();
		if (candidateList.isEmpty()) {
			return result;
		}
		double kthDistance = candidateList.size() < k ? candidateList.get(candidateList.size() - 1).dist : candidateList.get(k - 1).dist;
		for (Candidate<T> cand : candidateList) {
			if (cand.dist <= kthDistance) {
				result.add(cand.obj);
			}
		}
		return result;
	}

	/**
	 * Returns the Euclidean distance from the query point (qx, qy) to the centroid
	 * of the envelope.
	 */
	private double distanceToEnvelope(double qx, double qy, Envelope env) {
		double cx = (env.getMinX() + env.getMaxX()) / 2.0;
		double cy = (env.getMinY() + env.getMaxY()) / 2.0;
		return Math.hypot(cx - qx, cy - qy);
	}

	private double squaredDistanceToEnvelope(double qx, double qy, Envelope env) {
		double dx = 0.0;
		if (qx < env.getMinX()) {
			dx = env.getMinX() - qx;
		} else if (qx > env.getMaxX()) {
			dx = qx - env.getMaxX();
		}
		double dy = 0.0;
		if (qy < env.getMinY()) {
			dy = env.getMinY() - qy;
		} else if (qy > env.getMaxY()) {
			dy = qy - env.getMaxY();
		}
		return dx * dx + dy * dy;
	}

	/**
	 * Counts the number of points in the subtree rooted at the given node.
	 *
	 * This method recursively sums each leaf (object entry) in the subtree.
	 */
	private int countPoints(Node<T> node) {
		if (node == null)
			return 0;
		int count = 0;
		for (Entry<T> entry : node.children.values()) {
			if (entry == null)
				continue;
			if (entry.child != null) {
				count += countPoints(entry.child);
			} else {
				count += 1;
			}
		}
		return count;
	}

	/**
	 * Traverses the subtree rooted at 'node' and collects all candidate leaf
	 * entries, computing their distance from the query point (qx, qy).
	 */
	private void traverseSubtree(Node<T> node, double qx, double qy, List<Candidate<T>> candidates) {
		if (node == null)
			return;
		for (Entry<T> entry : node.children.values()) {
			if (entry == null)
				continue;
			if (entry.child != null) {
				traverseSubtree(entry.child, qx, qy, candidates);
			} else {
				double d = distanceToEnvelope(qx, qy, entry.mbr);
				candidates.add(new Candidate<>(entry.obj, entry.mbr, d));
			}
		}
	}

	// --------------
	// Additional public accessors for testing.
	// --------------

	/**
	 * Returns the node type of the root.
	 */
	public NodeType getRootType() {
		return root.type;
	}

	/**
	 * Returns the mbr of the root.
	 */
	public Envelope getRootMBR() {
		return new Envelope(root.mbr);
	}

	/**
	 * Returns the entry stored in the given quadrant of root.
	 */
	public Entry<T> getRootEntry(Quadrant q) {
		return root.children.get(q);
	}
}