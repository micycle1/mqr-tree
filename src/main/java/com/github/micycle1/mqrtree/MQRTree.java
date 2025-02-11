package com.github.micycle1.mqrtree;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * MQRTree spatial index.
 *
 * @param <T> the type of object stored in the tree.
 * @author Michael Carleton
 */
public class MQRTree<T> {

	Node<T> root;
	private boolean counted = true;

	public MQRTree() {
		this.root = new Node<>(null);
	}

	/**
	 * Public insertion method.
	 * 
	 * @param obj the object to insert.
	 * @param env the envelope (MBR) of the object.
	 */
	public void insert(T obj, Envelope env) {
		Entry<T> entry = new Entry<>(env, obj);
		// Always start insertion from the root.
		root = insert(root, entry);
		counted = false;
	}

	/**
	 * Insert an entry into a node (or subtree) per the insertion strategy. This
	 * method implements: 1) preparing the new entry, 2) expanding the node MBR, 3)
	 * finding shifted objects, and 4) re‐inserting queued entries.
	 *
	 * @param node  the node in which to insert.
	 * @param entry the entry (object or subtree reference) to insert.
	 * @return the node (possibly new, if the tree was empty) after insertion.
	 */
	private Node<T> insert(Node<T> node, Entry<T> entry) {
		// If node is null or has no children, initialize the node.
		if (node == null || node.isEmpty()) {
			if (node == null) {
				node = new Node<>(null);
			}
			node.mbr = new Envelope(entry.mbr);
			// Insert the entry in the center and mark the node as CENTER.
			node.children.put(Quadrant.CENTER, entry);
			node.type = NodeType.CENTER; // Mark as CENTER so that later insertions trigger a reassign.
			return node;
		}

		// Save the original MBR (before merging the new entry).
		Envelope origMBR = new Envelope(node.mbr);

		// Merge the new entry’s MBR into the node’s MBR.
		node.mbr.expandToInclude(entry.mbr);

		// Create an insertion queue and determine where the new entry should go.
		Deque<InsertionItem<T>> queue = new ArrayDeque<>();
		Quadrant targetQuad = findInsertQuad(entry.mbr, node.mbr);
		queue.add(new InsertionItem<>(targetQuad, entry));

		// Identify and remove any objects (or subtrees) whose location is now invalid.
		findShiftedObjs(queue, node, origMBR);

		// Reinsert all queued items into this node.
		insertQueue(node, queue);

		return node;
	}

	/**
	 * Given an object envelope and the node’s current MBR, determine in what
	 * quadrant the object should be inserted. We compare the centroid of the object
	 * with the centroid of the node’s MBR.
	 *
	 * @param objEnv  the envelope for the object.
	 * @param nodeMBR the current MBR of the node.
	 * @return the quadrant
	 */
	private Quadrant findInsertQuad(Envelope objEnv, Envelope nodeMBR) {
		double objCx = (objEnv.getMinX() + objEnv.getMaxX()) / 2.0;
		double objCy = (objEnv.getMinY() + objEnv.getMaxY()) / 2.0;
		double nodeCx = (nodeMBR.getMinX() + nodeMBR.getMaxX()) / 2.0;
		double nodeCy = (nodeMBR.getMinY() + nodeMBR.getMaxY()) / 2.0;

		// If centroids of the object and node exactly coincide, assign to CENTER (EQ).
		if (Double.compare(objCx, nodeCx) == 0 && Double.compare(objCy, nodeCy) == 0) {
			return Quadrant.CENTER;
		}

		// Determine relative quadrant.
		if (objCx < nodeCx) {
			if (objCy < nodeCy) {
				return Quadrant.SW;
			} else { // on or above nodeCy goes to NW
				return Quadrant.NW;
			}
		} else { // objCx > nodeCx
			if (objCy >= nodeCy) {
				return Quadrant.NE;
			} else {
				return Quadrant.SE;
			}
		}
	}

	/**
	 * Identify the MBRs (objects or subtrees) within a node that have now shifted
	 * (i.e. their location is no longer valid because the node’s MBR centroid
	 * changed after expansion or contraction).
	 *
	 * This method implements the pseudocode from Figs. 6 & 7.
	 *
	 * @param queue   the insertion queue to add shifted objects.
	 * @param node    the node to be examined.
	 * @param origMBR the MBR of the node before the new entry was merged.
	 */
	private void findShiftedObjs(Deque<InsertionItem<T>> queue, Node<T> node, Envelope origMBR) {
	    double origCx = (origMBR.getMinX() + origMBR.getMaxX()) / 2.0;
	    double origCy = (origMBR.getMinY() + origMBR.getMaxY()) / 2.0;
	    double newCx = (node.mbr.getMinX() + node.mbr.getMaxX()) / 2.0;
	    double newCy = (node.mbr.getMinY() + node.mbr.getMaxY()) / 2.0;

	    // If the centroid of the node did not change,
	    // then no object has shifted.
	    if (Double.compare(origCx, newCx) == 0 && Double.compare(origCy, newCy) == 0) {
	        return;
	    }

	    // Special handling if the node is already a CENTER node.
	    if (node.type == NodeType.CENTER) {
	        for (Map.Entry<Quadrant, Entry<T>> e : node.children.entrySet()) {
	            Quadrant newQuad = findInsertQuad(e.getValue().mbr, node.mbr);
	            queue.add(new InsertionItem<>(newQuad, e.getValue()));
	        }
	        node.children.clear();
	        node.type = NodeType.NORMAL;
	        return;
	    }

	    // For the general case, inspect all children.
	    // If any child’s proper quadrant changes, enqueue it for re‐insertion.
	    boolean foundCenterShift = false;
	    // Create a copy because we’ll remove keys during iteration.
	    for (Map.Entry<Quadrant, Entry<T>> e : new EnumMap<>(node.children).entrySet()) {
	        Quadrant correctQuad = findInsertQuad(e.getValue().mbr, node.mbr);
	        if (correctQuad != e.getKey()) {
	            queue.add(new InsertionItem<>(correctQuad, e.getValue()));
	            node.children.remove(e.getKey());
	            if (correctQuad == Quadrant.CENTER) {
	                foundCenterShift = true;
	            }
	        }
	    }
	    // IMPORTANT: if any child must now be inserted in CENTER,
	    // mark this node as a CENTER node so that insertQueue
	    // will handle it appropriately.
	    if (foundCenterShift) {
	        node.type = NodeType.CENTER;
	    }
	}


	/**
	 * Process the insertion queue by inserting each queued item into the proper
	 * quadrant of the node.
	 *
	 * This method implements the pseudocode in Fig. 9.
	 *
	 * @param node  the node into which we are inserting.
	 * @param queue the queue of items to insert.
	 */
	private void insertQueue(Node<T> node, Deque<InsertionItem<T>> queue) {
	    while (!queue.isEmpty()) {
	        InsertionItem<T> item = queue.poll();
	        Quadrant quad = item.quad;
	        Entry<T> entry = item.entry;
	        
	        // If we’re trying to insert into CENTER and the node isn’t marked as CENTER yet,
	        // then promote it now.
	        if (quad == Quadrant.CENTER && node.type != NodeType.CENTER) {
	            node.type = NodeType.CENTER;
	        }
	        
	        // If the node is CENTER, then for simplicity we simply update the CENTER slot.
	        // (A full implementation might use a linked list of center nodes.)
	        if (node.type == NodeType.CENTER && quad == Quadrant.CENTER) {
	            node.children.put(Quadrant.CENTER, entry);
	            continue;
	        }
	        
	        // For a NORMAL node, handle insertion normally.
	        if (node.children.containsKey(quad)) {
	            Entry<T> existing = node.children.get(quad);
	            if (existing.isNode()) {
	                // Recurse if a subtree already exists.
	                Node<T> child = existing.child;
	                child = insert(child, entry);
	                existing.child = child;
	            } else {
	                // There is a collision: create a new child node.
	                Node<T> newChild = new Node<>(node);
	                newChild.mbr = new Envelope(entry.mbr);
	                newChild.mbr.expandToInclude(existing.mbr);
	                // Determine proper quadrants for both entries.
	                Quadrant newEntryQuad = findInsertQuad(entry.mbr, newChild.mbr);
	                Quadrant existingQuad = findInsertQuad(existing.mbr, newChild.mbr);
	                newChild.children.put(newEntryQuad, entry);
	                newChild.children.put(existingQuad, existing);
	                // Replace the original entry with the new child node.
	                Entry<T> newEntry = new Entry<>(newChild.mbr, newChild);
	                newChild.parent = node;
	                newEntry.child = newChild;
	                node.children.put(quad, newEntry);
	            }
	        } else {
	            // If there’s no collision, simply insert.
	            node.children.put(quad, entry);
	        }
	    }
	}



	/**
	 * Adjust (or recalc) the node’s MBR based on its children. If the node has no
	 * children, the MBR is set to an empty envelope.
	 *
	 * @param node the node to adjust.
	 */
	private void adjustNode(Node<T> node) {
		Envelope newMBR = new Envelope();
		for (Entry<T> entry : node.children.values()) {
			newMBR.expandToInclude(entry.mbr);
		}
		node.mbr = newMBR;
	}

	/* ===================== Supporting Classes ==================== */

	/**
	 * Quadrants – note that CENTER corresponds to EQ.
	 */
	public enum Quadrant {
		CENTER, NE, NW, SW, SE
	}

	/**
	 * Node type: NORMAL or CENTER.
	 */
	public enum NodeType {
		NORMAL,
		/**
		 * A CENTER node only references objects whose centroids are the same as the
		 * centroid of the node MBR. In addition, a CENTER node is utilized only when
		 * more than one object exists with overlapping centroids.
		 */
		CENTER
	}

	/**
	 * A tree node. Each node has an MBR, a type (NORMAL or CENTER), and at most
	 * five children (one per quadrant). A child entry may be either a leaf
	 * (referencing an object) or a subtree.
	 *
	 * @param <T> the type of object stored.
	 */
	public static class Node<T> {
		/**
		 * The minimum two-dimensional extent that encompasses all MBRs of both the node
		 * and all of the nodes in its subtrees.
		 */
		Envelope mbr;
		NodeType type;
		EnumMap<Quadrant, Entry<T>> children;
		Node<T> parent;
		int childCount; // number of leaf entries in the subtree

		public Node(Node<T> parent) {
			this.parent = parent;
			this.type = NodeType.NORMAL;
			this.children = new EnumMap<>(Quadrant.class);
			this.mbr = new Envelope();
			this.childCount = 0;
		}

		public boolean isEmpty() {
			return children.isEmpty();
		}
	}

	/**
	 * An entry in the tree – either a leaf that holds an object (and its envelope)
	 * or an internal node reference.
	 *
	 * @param <T> the type of the object.
	 */
	public static class Entry<T> {
		final Envelope mbr; // user-given static mbr that geometrically bounds T
		T obj; // non-null for a leaf entry.
		Node<T> child; // non-null if this entry refers to a subtree.

		// Constructor for a leaf entry.
		public Entry(Envelope mbr, T obj) {
			this.mbr = new Envelope(mbr);
			this.obj = obj;
			this.child = null;
		}

		// Constructor for an internal node entry.
		public Entry(Envelope mbr, Node<T> child) {
			this.mbr = new Envelope(mbr);
			this.child = child;
			this.obj = null;
		}

		public boolean isNode() {
			return child != null;
		}

		@Override
		public String toString() {
			if (isNode()) {
				return "NodeEntry(children="+child.children.size()+"): " + mbr.toString();
			} else {
				return "LeafEntry: " + mbr.toString() + " -> " + obj;
			}
		}
	}

	/**
	 * An item used in the insertion queue.
	 *
	 * @param <T> the type of the stored object.
	 */
	public static class InsertionItem<T> {
		Quadrant quad;
		Entry<T> entry;

		public InsertionItem(Quadrant quad, Entry<T> entry) {
			this.quad = quad;
			this.entry = entry;
		}
	}

	// --- New inner class to represent a candidate for k-NN ---
	private static class SearchCandidate<T> implements Comparable<SearchCandidate<T>> {
		T obj;
		double distance;
		Envelope mbr; // provided for debugging if needed

		public SearchCandidate(T obj, double distance, Envelope mbr) {
			this.obj = obj;
			this.distance = distance;
			this.mbr = new Envelope(mbr);
		}

		@Override
		public int compareTo(SearchCandidate<T> other) {
			return Double.compare(this.distance, other.distance);
		}
	}

	public List<T> search(Envelope query) {
		List<T> results = new ArrayList<>();
		search(root, query, results);
		return results;
	}

	/**
	 * Distance from center of MBR.
	 * 
	 * @param query
	 * @param k
	 * @return
	 */
	public List<T> knnSearch(Coordinate query, int k) {
		// Refresh the counts in the tree (assumes the tree is static or built already)
		if (!counted) {
			updateCounts();
			counted = true;
		}

		// Step 1: Locate the candidate node X.
		// (We require that X.mbr.contains(query) and that its subtree has more than k
		// points.)
		Node<T> X = root;
		while (true) {
			// Only descend if X’s region contains the query and it has more than k points.
			if (X.mbr.contains(query) && X.childCount > k) {
				Envelope queryEnv = new Envelope(query.x, query.x, query.y, query.y);
				Quadrant quad = findInsertQuad(queryEnv, X.mbr);
				if (X.children.containsKey(quad) && X.children.get(quad).isNode()) {
					X = X.children.get(quad).child;
					continue;
				}
			}
			break;
		}

		// Step 2: Traverse node X’s subtree and collect candidates.
		List<SearchCandidate<T>> candidateList = new ArrayList<>();
		collectCandidates(X, query, candidateList);

		// Sort candidate list (ascending by distance).
		Collections.sort(candidateList);

		// If we didn’t collect at least k candidates in this node, fall back and
		// collect from the whole tree.
		if (candidateList.size() < k) {
			candidateList.clear();
			collectCandidates(root, query, candidateList);
			Collections.sort(candidateList);
		}

		// Step 3: Validate the candidate set.
		// (Given that X.mbr contains the query, compute directional distances.)
		double kthDistance = (k > 0 && candidateList.size() >= k) ? candidateList.get(k - 1).distance : Double.POSITIVE_INFINITY;
		Envelope SR = X.mbr;
		double dToWest = query.x - SR.getMinX();
		double dToEast = SR.getMaxX() - query.x;
		double dToSouth = query.y - SR.getMinY();
		double dToNorth = SR.getMaxY() - query.y;
		boolean valid = (kthDistance <= dToWest) && (kthDistance <= dToEast) && (kthDistance <= dToSouth) && (kthDistance <= dToNorth);
		// If not valid, fall back to collecting over the full tree.
		if (!valid) {
			candidateList.clear();
			collectCandidates(root, query, candidateList);
			Collections.sort(candidateList);
		}

		// Step 4: Return only the top k objects from the final candidate list.
		List<T> result = new ArrayList<>();
		for (int i = 0; i < Math.min(k, candidateList.size()); i++) {
			result.add(candidateList.get(i).obj);
		}
		return result;
	}

	// --- New helper method: collectCandidates ---
	// Traverse the subtree rooted at 'node' and add all leaf entries to
	// candidateList.
	// Distance is computed from query point to the center of the leaf's envelope.
	/**
	 * Because we removed the node‐level pruning and now always descend into the
	 * entire subtree, the worst‐case performance of our region search becomes O(N)
	 * (N = total number of leaf entries in the tree). In practice, if your tree is
	 * well balanced and the envelopes (or objects) are not all overlapping, many
	 * subtrees will still be pruned by the envelope tests on the leaf level. Still,
	 * you lose some of the potential efficiency that tightened node MBRs might have
	 * provided. One way to alleviate this is to improve how you calculate and
	 * update the node MBR so that it always really is the full union of its
	 * children. But as a quick fix, the revised search guarantees correctness at
	 * some expense in speed.
	 * 
	 * @param node
	 * @param query
	 * @param candidateList
	 */
	private void collectCandidates(Node<T> node, Coordinate query, List<SearchCandidate<T>> candidateList) {
		if (node == null)
			return;
		// If the node's MBR does not intersect a circle defined by the query point
		// with radius equal to the current kth candidate (if available) we might prune.
		// (A more advanced pruning is possible.)
		for (Entry<T> entry : node.children.values()) {
			if (entry.isNode()) {
				collectCandidates(entry.child, query, candidateList);
			} else {
				// Compute candidate point as the envelope's center.
				double cx = (entry.mbr.getMinX() + entry.mbr.getMaxX()) / 2.0;
				double cy = (entry.mbr.getMinY() + entry.mbr.getMaxY()) / 2.0;
				double dist = query.distance(new Coordinate(cx, cy));
				candidateList.add(new SearchCandidate<>(entry.obj, dist, entry.mbr));
			}
		}
	}

	/**
	 * Helper recursive method for region search.
	 * 
	 * NOTE this approach should work given "A node MBR is defined for every node in
	 * the tree. For a given node, it is the minimum two-dimensional extent that
	 * encompasses all MBRs in the node and all of the nodes in its subtrees." --
	 * indicates there's a problem elsewhere with how the tree is constructed.
	 *
	 * @param node    the current node in the tree (or subtree)
	 * @param query   the search region (Envelope)
	 * @param results a list to store found objects
	 */
	private void searchFast(Node<T> node, Envelope query, List<T> results) {
		if (node == null)
			return;

		// If the node's overall MBR does not intersect the query region, no need to
		// proceed.
		if (!node.mbr.intersects(query)) {
			return;
		}

		// Evaluate each child's MBR against the query region.
		for (Entry<T> entry : node.children.values()) {
			if (entry.mbr.intersects(query)) {
				if (entry.isNode()) {
					// If the entry is a subtree, search it recursively.
					searchFast(entry.child, query, results);
				} else {
					// If it is a leaf entry, add the object.
					results.add(entry.obj);
				}
			}
		}
	}

	// --- Modified method: search ---
	// Always recurse into nodes regardless of parent's MBR intersection.
	private void search(Node<T> node, Envelope query, List<T> results) {
		if (node == null)
			return;

		// Instead of pruning based on node.mbr or entry.mbr for subtree entries,
		// always descend into the children.
		for (Entry<T> entry : node.children.values()) {
			if (entry.isNode()) {
				// Always search the subtree.
				search(entry.child, query, results);
			} else {
				// For leaf entries, check if its envelope intersects the query.
				if (entry.mbr.intersects(query)) {
					results.add(entry.obj);
				}
			}
		}
	}

	/**
	 * Update each node’s childCount in the tree. Call this after building the tree
	 * (or before a knnSearch) to get up‑to‑date counts.
	 */
	private void updateCounts() {
		computeChildCount(root);
	}

	private int computeChildCount(Node<T> node) {
		if (node == null)
			return 0;
		int count = 0;
		for (Entry<T> entry : node.children.values()) {
			if (entry.isNode()) {
				count += computeChildCount(entry.child);
			} else {
				count++;
			}
		}
		node.childCount = count;
		return count;
	}

}
