### Structure

All nodes in the mqr-tree have the same two-dimensional structure. A node contains 5 locations - northeast (NE), northwest (NW), southwest (SW), southeast (SE) and centre (EQ). Each location contains either: `(MBR,obj_ ptr)` where `obj_ptr` is a pointer that references an object and MBR is the approximation of the object, or: `(MBR,node_ ptr)` where `node_ptr` is a pointer to a subtree and MBR is the MBR that encompasses all MBRs in the subtree. 

Every node in the mqr-tree must have at least two locations, and not more than five locations, that are referencing either an object or a subtree. It is possible to have a node contains pointers to both objects and subtrees.

### Node Organization and Validity

In every node of the mqr-tree, we determine the relative placement of both objects and subregions by using the centroids of their MBRs. We define the origin of each node as its centre location. The objects that are referenced from the centre location have the same centroid as the centroid of the node MBR for the node. All other objects and subregions that are referenced from the other locations (NW,SE,SW,NE) are placed with respect to the centroid of the node MBR. Fig. 2 depicts the spatial relationships. The orientations (NE, SE, SW, NW) include centroids that fall on the axes (E, S, W, N, respectively).


#### Fig 2. Table of orientation of A (a new object) with respect to B (the centroid of the node MBR)

| Ax = Bx | Ax > Bx | Ay = By | Ay > By | Placement |
|---------|---------|---------|---------|-----------|
|    0    |    0    |    0    |    0    |    SW     |
|    0    |    0    |    1    |    0    |    SW     |
|    0    |    0    |    0    |    1    |    NW     |
|    1    |    0    |    0    |    1    |    NW     |
|    0    |    1    |    0    |    0    |    SE     |
|    1    |    0    |    0    |    0    |    SE     |
|    0    |    1    |    0    |    1    |    NE     |
|    0    |    1    |    1    |    0    |    NE     |
|    1    |    0    |    1    |    0    |    EQ     |

A node is classified as either `NORMAL` or `CENTER`. In a `NORMAL` node, the locations are organized based on the orientations defined above (see Fig. 1). A `NORMAL` node is valid when: - The node MBR encloses all the minimum bounding rectangles in the objects or subtrees that the node references, and - All objects or subtrees pointed to by a location are in the proper quadrant relative to the node centroid. 

In a `CENTER` node, the locations are organized linearly. A `CENTER` node only references objects whose centroids are the same as the centroid of the node MBR. In addition, a `CENTER` node is utilized only when more than one object exists with overlapping centroids.

### Insertion Strategy 

The insertion strategy works as follows. Beginning at the root node, the node MBR is adjusted to include the new object. Then, the appropriate location, relative to the centroid of the node MBR, is identified for inserting a reference to the new object. If the location is empty, the reference to the object is inserted. Otherwise, the subtree is traversed in the same manner, until either: 1) an appropriate location is found that is empty and the object reference can be inserted, or 2) a leaf node is reached, and no proper location is available for the new object reference. If the object reference cannot be inserted in the proper location of the leaf node, then a new leaf node is created. 

In addition, for each node on the insertion path, node validity is maintained by removing and reinserting objects that have changed orientation relative to the centroid of the node MBR during the insertion process. When inserting an object from a node, one of four things will happen to the node MBR:

- The centroid of the node will not change and therefore all objects remain in their proper orientation
- The node is a `CENTER` node, and the new object to be inserted has a different centroid than the existing objects.
- The centroid of the node moves as the region of the node MBR increases in size
- The centroid of the node moves as the region of the node MBR decreases in size.

In the second case, a `CENTER` node contains existing points or objects that all have the same centroid, but the new object has a centroid that differs from the other objects or points. Therefore, all existing objects must be moved. In the latter two cases, some objects may have shifted to a different quadrant due to the movement of the centroid of the resulting node MBR. If so, these objects are no longer in their proper relative node location. Any objects in this situation must be located and moved. In all of cases 2-4 above, the objects are moved by reinserting them beginning at the current level of the tree so that they are placed in a proper relative location.

### Insertion Implementation Details

Here, we present the implementation details for our insertion strategy. As mentioned earlier, at each level of the insertion path, one or more of the following actions take place when the MBR that represents a new object is inserted into a node: 

1) Prepare the new object for insertion into the current node
    - increase the size of the node MBR of the current node in order to enclose the new object,
    - determine which quadrant that the reference to the new object will be potentially inserted into,
    - add the MBR reference of the new object to the insertion queue.
2) Locate the MBRs of objects or subtrees whose existing location in the node is no longer valid because the centroid of the node MBR has changed. Add these MBRs to the insertion queue.
3) (Re)insert the MBRs on the insertion queue into the current node.

Fig. 5 presents the pseudocode for the main insertion
strategy, which depicts an overview of the above sequence of events. Fig. 6 and Fig. 7 present a sketch of the
implementation for identifying MBRs of object or subregions that are no longer in the proper quadrant with respect to the centroid of the node MBR. It handles all four
cases mentioned above: 

1) identifying that no changes
have occurred,
2) a `CENTER` node situation exists,
3) the new node MBR is larger than the previous one before a new object was added, and
4) the new node MBR is smaller than the previous one before a new node was added. 

Fig. 6 depicts the first two situations. First, to determine if no change to the proper locations of all existing references (plus the new object to be inserted) has occurred, the relative location of the centroid for the new node MBR with respect to the centroid of the original MBR is determined. If the centroids overlap, this means that, although an increase of then node MBR could have taken place, the centroid of the node MBR has not changed and all objects in the node are still in their proper quadrants. The search for shifted objects ends here and the reference for the new object can be inserted (via the `insert_queue function` - see Fig. 9).

If the centroids do not overlap, then the other cases must be considered. The next case is determining if the existing node is a `CENTER` node, and the new object to be inserted has an MBR with a centroid that is not equal to the centroids of the existing MBRs in the node. We chose to handle this situation simply - we identify which quadrant they will be placed in, remove all MBRs from the `CENTER` node and place them on the insertion queue (via `remove_and_q_objects`, see Fig. 8), and change the node type to `NORMAL`.

Fig. 7 depicts a portion of the **last two cases** of node MBR expansion and contraction. Here, we show how a NE expansion is detected and handled. All other cases for both expansion and contraction are handled similarly. The expansion type is determined using the relative location of the new node MBR with respect to the original node MBR (this was calculated earlier). After the expansion type is identified, the corresponding subregions that are affected by the node MBR expansion are also identified. There are seven subregions that have shifted from one quadrant to another - NE to SE, NE to SW, NE to NW, NW to SW, SE to SW, NE to EQ (not in the figure), and EQ to SW (also, not in the figure). Any MBRs in these subregions will need to be removed and reinserted into the proper quadrant. Therefore, each subregion is identified using the original and new node MBRs, and any MBRs that reside in these regions are removed and added to the insertion queue using `remove_and_q_objects`.

Fig. 8 depicts the pseudocode for the function `remove_and_q_objects`. This function takes as input the node quadrant that potentially contains MBRs the must be relocated, the subregion that is affected, the destination quadrant for any MBRs that must be moved, and the insertion queue that the affected MBRs must be placed on. Its goal is to remove all MBRs that are accessible from input quadrant and that overlap the given subregion, and place them on the insert queue. Three situations exist: 
1) the input quadrant references nothing, in which case the function terminates,
2) the input quadrant contains an MBR of an object, and 
3) the input quadrant contains an MBR of a subtree.

If the input quadrant contains an MBR for an object, then a test is performed to see the centroid of the MBR falls within the shifted region. If so, it is removed from the node and added to the insertion queue.

If the input quadrant contains an MBR for a subtree, then two steps must be carried out. The first is to recursively call the function on each quadrant in the subtree, to identify objects that must be removed and reinserted. The second is to adjust the node MBR or delete the node if all MBRs have been removed during this process.

Finally, Fig. 9 depicts the function for inserting the MBRs on the insert queue into the current node (recall that this current node is the node that we started with at the beginning of the sequence of events above). This will attempt to insert the new object, as well as re-insert any removed MBRs, into the quadrant that is specified for each MBR. Again, for each object or MBR being (re)- inserted, several situations exist:
1) the specified quadrant in the node is empty,
2) the node is a `CENTER` node
3) the specified quadrant contains MBR for a node, and
4) the specified quadrant contains an MBR for an object.

If the quadrant specified for an object or MBR is empty, then the MBR and corresponding reference can be inserted, and insertion is finished for the current object or MBR. If the node is a `CENTER` node, then the next available location for the MBR and reference is located and is inserted. If necessary, an additional node is added to form a linked list of `CENTER` nodes. If the quadrant that is specified for the new object or MBR is referencing a subtree, then the insert function (see Fig. 5) is called on the object or MBR, and the root of the subtree. Finally, if the quadrant contains an MBR for an object, a new child node is created, and the insert function is called on both MBRs.

#### Fig 5. Main Insertion Strategy

```
Algorithm: insert
Input:
 n: node - node in which to insert newobj
 newobj: object - pointer to new object
Variables:
 objs: queue - queue of objects to be inserted
 orig_mbr: mbr before new object is inserted in n
 item: object to be placed on objs queue
=== Begin ===
# if node is empty, insert newobj in center
if number_childen(n) == 0
 n->mbr = newobj->mbr
 n->loc[EQ] = newobj
else
 # copy the original node MBR
 orig_mbr = n->mbr
 # merge newobj's MBR into the node's MBR
 merge_mbrs( n->mbr, newobj->mbr )
 # Prep newobj for insertion
 item.quad =
 find_insert_quad( newobj->mbr, n->mbr )
 item.obj = newobj
 # Add newobj to the insertion queue
 enqueue(objs, item)
 # Find other objects that are no longer in a
 # valid quadrant
 # Add them to the insertion queue
 find_shifted_objs(objs, n, orig_mbr)
 # (Re)insert objects in the current node
 insert_queue(n, objs)
return
```

#### Fig 6&7. Finding Shifted Objects
```
Algorithm: find_shifted_objs
Input:
 q: queue - queue in which to place objects that need
 to be moved
 n: node - node to be updated
 orig_mbr: mbr - MBR of node n before merge
Variables:
 q: quadrant - relative location
 area_diff: int region: mbr
=== Begin ===
#first, find if any objects have shifted quads
quad = find_insert_quad( n->mbr, orig_mbr )
if quad = EQ
 # nothing to do, MBR may have changed but all
 # objects are already in their proper location
 return
if n->type = CENTER
 # The node is a center node and all objects in this
 # node need to be removed so they can be placed in
 # the appropriate quadrant
 # Find where the objects will be inserted
 quad = find_insert_quad( orig_mbr, n->mbr)
 # remove all objects and place them on the queue
 for each node location 'tmploc'
 remove_and_q_objects(q, quad, tmploc, n->mbr)
 done
 n->type = NORMAL
 return
# Get objects that belong in the EQ location
adjust_region(n->mbr.cx, n->mbr.cx,
 n->mbr.cy, n->mbr.cy)
remove_and_q_objects(q, EQ, n->loc[quad], region)
area_diff = area_change( n->mbr, orig_mbr)
#new node MBR larger than original
if area_diff > 0
 if quad = NE
 # to SE
 adjust_region(n->mbr.cx, orig_mbr->hx,
 orig_mbr->cy, n->mbr.cy -1)
 remove_and_q_objects(q, SE, n->loc[NE], region)

 # to SW, part NE
 adjust_region(orig_mbr->cx +1, n->mbr.cx -1,
 orig_mbr->cy, n->mbr.cy)
 remove_and_q_objects(q, SW, n->loc[NE], region)
 # to SW, part SE
 adjust_region(orig_mbr->cx, n->mbr.cx -1,
 orig_mbr->ly, orig_mbr->cy -1)
 remove_and_q_objects(q, SW, n->loc[SE], region)
 # to SW, part NW
 adjust_region(orig_mbr->lx, orig_mbr->cx,
 orig_mbr->cy +1, n->mbr.cy)
 remove_and_q_objects(q, SW, n->loc[NW], region)
 # to SW part EQ
 remove_and_q_objects(q, SW, n->loc[EQ], orig_mbr)
 # to NW
 adjust_region(orig_mbr->cx +1, n->mbr.cx,
 n->mbr.cy +1, orig_mbr->hy)
 remove_and_q_objects(q, NW, n->loc[NE], region)
 # also have cases for SE, NW and SW,
 # and are handled similarly to NE

 else #new node MBR smaller than original

 # contraction cases for NE, SE, SW
 # and NW handled similar to above
return
```

#### Fig 8. Queuing Misplaced Objects
```
Algorithm: remove_and_q_objects
Input:
 q: queue - insertion queue
 quad: int - destination quad for relocated objects
 loc: node quadrant containing objects for relocation
 region: mbr - region containing objects for relocation
Variables:
 item: queue item
 loctmp: location - location iterator
=== Begin ===
if loc is undefined
 return
# We are always going to quad
item.quad = quad
if loc references a node
 n = loc
 if ( overlaps( n->mbr, region ) )
 for each node location 'tmploc'
 do
 remove_and_q_objects( q, quad, tmploc, region)
 done

 # if any objects are recursively removed here the
 # node MBR must be adjusted, or the node deleted
 adjust_node(loc->parent)

else
# location references an object
 if centroid_within(loc->mbr, region)
 item.obj = loc
 enqueue( q, item )
 loc = undefined
 ```

 #### Fig 9. (Re)-inserting Misplaced Objects
 ```
 Algorithm: insert_queue
Input:
 n: node -- node to insert queue objects
 q: queue -- queue containing objects
Variables:
 item: item from queue to insert obj: object
 quad: int -- location index
 ntmp: node -- temporary node
=== Begin ===
while queue is not empty do
 item = dequeue(q)
 quad = item.quad
 obj = item.obj
 if n->type = CENTER
 # see if a location if available
 quad = next_ctr_loc(n)
 if quad is defined
 n->loc[quad] = obj
 sort_ctr(n)
 continue;
 # else, new node is added to the CENTER
 # node list for object
 if n->loc[quad]
 if n->loc[quad] is a node
 insert( n->loc[quad], obj)
 continue
 else
 # Create a new child and insert
 if quad = EQ and num_child(n) = 1
 # convert node to a CENTER node
 n->type = CENTER
 enqueue( q, item )
 else
 ntmp = new_node()
 insert( ntmp, obj )
 insert( ntmp, n->loc[quad] )
 ntmp->parent = n
 n->loc[quad] = ntmp
 else
 # insert at n->loc[quad]
 n->loc[quad]
done
```

### knn search

This strategy utilizes the mqr-tree to quickly locate a set of k or more candidate points to satisfy a k-NN query. A node that will serve as s “starting point” in the mqr-tree is located first, before a candidate set of points that (hopefully) will contain the k nearest neighbours to the query point are fetched. Although as we see, the strategy may need to “backtrack” towards the root in order to find a proper set of k nearest neighbours, the advantage of the mqr-tree of not having overlap of any regions at the same level, means that only one path in the tree needs to be utilized for locating the required nearest neighbours. In addition, results from experiments show that backtracking is more the exception than the rule. Figure 10 presents the pseudocode for the proposed approach. After obtaining the query point and the number of nearest neighbours being sought (k), the search begins at the root node for a node that will be the starting point for fetching candidate k nearest neighbours. The query point is first evaluated against the root node and its corresponding nodeMBR. One of the following situations will occur:

1) The number of points that are reachable from this node, npoints, is <= k. If this occurs, then the search for a starting point stops here. If npoints < k, then the search goes back to the parent node for the starting point.
2) The query point is within the node’s nodeMBR, but in a location not covered by one of the quadrants. the search for a starting point also stops here. 
3) The query point will reside in one of the 4 quadrants - NW, NE, SE or SW (in other words, the point resides in the space that is covered by one of these quadrants). The search will continue in the subtree of the encompassing quadrant, and terminate when one of the two above conditions are met.

Once the node that will serve as a starting point is found, its nodeMBR is fetched. This is the superMBR, or the MBR that will encompass the candidate set of points from which the k nearest neighbours will be found. The mqr-tree will then be traversed from this node to fetch all points in the node’s subtree. The fetched points are sorted by increasing distance, before the kth furthest point from the query point will be evaluated against the superMBR to determine if the set of k nearest neighbours is valid. A set of k nearest neighbours is valid only if the distance of the kth nearest neighbour from the query point is less than or equal to the distances from the query point to all sides of the superMBR. Any distances that are less than the distance to the kth nearest neighbour may mean that another closer point may reside elsewhere in the tree. Figures 3a and b depict the valid and invalid situations respectively for the 1-nearest neighbour case. As we can see, in Fig. 3a, the distance from the query point to the closest point is less than the distances to all four sides of the superMBR. So we know that this is a valid nearest neighbour. In Fig. 3b, notice that the distance between the query point and the closest point to it is greater than the distance to the north side of the superMBR. This means that there may be a closer nearest neighbour that resides outside of the north side and within another node’s nodeMBR, so this candidate is not guaranteed to be the nearest neighbour.

If the set of candidate k nearest neighbours is valid, then these are sent to the user. Otherwise, another set of candidate points will be obtained from the parent of the chosen starting point node. If necessary, this process will be repeated by proceeding up the path one parent node at a time, until either: (1) a set of valid k nearest neighbours is found that passes the validity test, or (2) the root node is reached, which means that the candidate set contains all points in the tree and therefore the closest k of them must be the k nearest neighbours.

#### Fig. 10 Pseudocode for mqr-tree-Based k-Nearest Neighbour Strategy
```
point P = obtain_search_point();
int k = obtain_knn.value();
node X;
node.region SR;
bool found = 0;
point Q[k]; //dynamic, adjusted if need be

//first , locate the first node with the number of points 
//equal to k
while(!found && X—>npoints > k)
    if(P in X[NW])
        X = X[NW]—>child;
    else if (P in X[NE])
        X = X[NE]—>child;
    else if (P in X[SE])
        X = X[SE]—>child;
    else if (P in X[SW])
        X = X[SW]—>child;
    else
        found = 1;
    end if
end while

//if not enough points in subtree go back up one level
if(X—>npoints < k)
    X = X—>parent;

//if set of fetched points contains valid k nearest neighbours
//then finished. Otherwise, go back up the tree and repeat
while(1)
    //obtain the points from X, and corresponding superMBR
    traverse.tree(X, Q);
    sort.by-distance(Q);
    SR = obtain.node.region(X)

    if(X—>parent == NULL)
        return Q; //at the root
    else if(dist(P,Q[k]) <= dist(P.cy,SR.hy) &&
            dist(P,Q[k]) <= dist(P.cy,SR.ly) &&
            dist(P,Q[k]) <= dist(P.cx,SR.hx) &&
            dist(P,Q[k]) <= dist(P.lx,SR.lx))
        return Q; //valid result
    else
        X = X—>parent;
end while
```
```
point P = obtain_search_point();
int k = obtain_knn.value();
node X;
node.region SR;
bool found = 0;
point Q[k]; //dynamic, adjusted if need be

//first , locate the first node with the number of points 
//equal to k
while(!found && X—>npoints > k)
    if(P in X[NW])
        X = X[NW]—>child;
    else if (P in X[NE])
        X = X[NE]—>child;
    else if (P in X[SE])
        X = X[SE]—>child;
    else if (P in X[SW])
        X = X[SW]—>child;
    else
        found = 1;
    end if
end while

//if not enough points in subtree go back up one level
if(X—>npoints < k)
    X = X—>parent;

//if set of fetched points contains valid k nearest neighbours
//then finished. Otherwise, go back up the tree and repeat
while(1)
    //obtain the points from X, and corresponding superMBR
    traverse.tree(X, Q);
    sort.by-distance(Q);
    SR = obtain.node.region(X)

    if(X—>parent == NULL)
        return Q; //at the root
    else if(dist(P,Q[k]) <= dist(P.cy,SR.hy) &&
            dist(P,Q[k]) <= dist(P.cy,SR.ly) &&
            dist(P,Q[k]) <= dist(P.cx,SR.hx) &&
            dist(P,Q[k]) <= dist(P.lx,SR.lx))
        return Q; //valid result
    else
        X = X—>parent;
end while
```