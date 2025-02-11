# MQR-Tree

[🚧WIP]

This repository contains a Java implementation of the **MQR-Tree**, a 2D spatial index structure that improves upon the classic R-tree and 2DR-tree. The MQR-Tree is designed to efficiently manage and query spatial data by reducing overlap, overcoverage, and tree height while maintaining spatial relationships between objects.

If you're familiar with the R-tree, you'll find the MQR-Tree to be a more optimized and efficient alternative.

---

## What is the MQR-Tree?

The MQR-Tree builds on the ideas of the R-tree and 2DR-tree but introduces several key improvements to make spatial queries faster and more efficient.

### Core Features

1. **Two-Dimensional Node Structure**:
   - All nodes in the MQR-Tree have the same two-dimensional structure.
   - Each node contains **5 locations**: 
     - **Northeast (NE)**
     - **Northwest (NW)**
     - **Southwest (SW)**
     - **Southeast (SE)**
     - **Centre (EQ)**
   - Each location can either point to another node (for internal nodes) or contain an object (for leaf nodes).

2. **Dynamic Node Organization**:
   - The MQR-Tree dynamically adjusts the centroids of nodes and the placement of objects based on their spatial relationships.
   - This ensures that objects are always placed in the correct quadrant relative to the node’s centroid, reducing overlap and overcoverage.

3. **Zero Overlap for Point Data**:
   - One of the coolest features of the MQR-Tree is that it achieves **zero overlap** when indexing point data. This means that point queries can be executed without traversing multiple paths in the tree, making them super efficient.

4. **Improved Insertion Strategy**:
   - The MQR-Tree uses a smarter insertion algorithm compared to the R-tree. When inserting a new object, the tree adjusts the node’s MBR (Minimum Bounding Rectangle) and ensures that objects are placed in the correct quadrant.
   - If an object no longer fits in its quadrant after an insertion, it’s re-inserted in the correct location, maintaining the tree’s spatial integrity.

5. **Reduced Overlap and Overcoverage**:
   - Compared to the R-tree, the MQR-Tree significantly reduces overlap and overcoverage of MBRs. This means fewer unnecessary searches and more efficient query performance.

---

## Getting Started

### Example Code

Here’s a quick example of how to use the MQR-Tree to index and query spatial data:

```java
// TODO
```

---

## Performance

The MQR-Tree has been tested and shown to outperform the R-tree in several key areas:

- **Coverage**: The total area covered by all MBRs is reduced.
- **Overcoverage**: The amount of whitespace within MBRs is significantly lower.
- **Overlap**: The MQR-Tree achieves zero overlap for point data, making point queries extremely efficient.
- **Tree Height**: While the MQR-Tree is not strictly height-balanced, its average height is comparable to the R-tree, and the trade-off is worth it for the reduced overlap and overcoverage.

---

## References

- [mqr-tree: A 2-dimensional Spatial Access
Method ](https://www.cs.uleth.ca/~osborn/pubs/JCSE.pdf). Marc Moreau, Wendy Osborn, and Brad Anderson. 

---

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.