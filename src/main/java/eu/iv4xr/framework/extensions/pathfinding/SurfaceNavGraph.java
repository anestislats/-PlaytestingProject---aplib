package eu.iv4xr.framework.extensions.pathfinding;

import java.util.*;

import eu.iv4xr.framework.spatial.Line;
import eu.iv4xr.framework.spatial.Obstacle;
import eu.iv4xr.framework.spatial.Vec3;
import eu.iv4xr.framework.spatial.meshes.*;
import nl.uu.cs.aplib.utils.Pair;

/**
 * A navigation-graph over a 3D-surface. The surface is described by
 * a set of (convex) polygons/Faces. The navigation-graph over this
 * surface is formed by the corners and centers of the Faces. Corners
 * are connected to each other, as they are specified by each Face.
 * Additionally, each corner of a Face f is connected to f's center
 * point. For each two Faces f1 and f2 that are connected (having a
 * common edge), we also connect their center-points.
 * 
 * This navigation-graph also supports few additional features:
 * 
 *   (1) It includes a PathFinder to calculate a path from one vertex
 *   in the nav-graph to another. By default, an A* PathFinder is used,
 *   though you can change that.
 *   
 *   (2) An option to do memory-based navigation. This option is by 
 *   default turned-on. Under this navigation mode, all vertices 
 *   are initially marked as unseen. An agent can then incrementally
 *   mark vertices as "seen". Navigation is only possible over the 
 *   part of the graph that were previously seen. 
 *   
 *   (3) An option to prefer travel through the faces' center points,
 *   or to travel along border edges. This is done my increasing the
 *   cost/distance of going to unpreferred vertices.
 * 
 *   (4) This navigation-graph additionally marks vertices which are on
 *   the border-edges. A border-edge is an edge that is not shared by
 *   multiple Faces. The type of a vertex can asked via  
 *   verticesType.get(i), which will return the type of vertex i. This
 *   returns either BORDER (if i is a border vertex), CENTER (if it is
 *   the center of a Face), or OTHER.
 * 
 * @author Wish
 *
 */
public class SurfaceNavGraph extends SimpleNavGraph {
	
	/**
	 * A vertex is a BORDER-vertex if it lies in a border-edge. An edge is a
	 * border-egde if it is not shared by two or more Faces.
	 * 
	 * A vertex is a CENTRE-vertex if it is the centre of a Face.
	 * 
	 * Other vertices have type OTHER.
	 */
	enum VertexType { BORDER, CENTRE, OTHER } 

	/**
	 * The convex-polygons/faces that overlay the vertices of this navigation graph.
	 * Each Face is defined by corners, which are vertices of this navigation graph.
	 * A Face describes a (small) surface that is physically navigatable. This
	 * navigation graph itself does not specify how to navigate to an arbitrary
	 * point in a Face (except when it is one of its corners), but knowing the
	 * surface the user of this class at least infer alternate locations to navigate
	 * to. It is up to the user how to use such information.
	 */
    public ArrayList<Face> faces ;
    
    /**
     * verticesType.get(i) specifies the type of vertex i. It is either CENTER, BORDER,
     * or OTHER.
     */
    public ArrayList<VertexType> verticesType ;
    
    /**
     * If seenVertices.get(i) is true it means that vertex i is considered to have been
     * "seen", for the purpose of memory-based navigation.
     */
    public ArrayList<Boolean> seenVertices ;
    
    
    /**
     * This maps the Faces to their corresponding center-points. The center-point
     * is represented by their id/index in the vertices array.
     */
    HashMap<Face,Integer> faceToCenterIdMap ;
    
    /**
     * The number of vertices that are corners of the Faces (so, vertices which
     * are not centers). These vertices will also have id between 0 and 
     * numberOfBaseVertices-1.
     */
    public int numberOfBaseVertices ;
    
    
    public static final int PREFER_CENTER = 0 ;
    public static final int PREFER_BORDER = -1 ;
    public static final int NO_PREFERENCE = 1 ;
    
    /**
	 * Specify preference when trying to find a path over the nav-graph. When this
	 * preference is PREFER_CENTER, we prefer a path that goes through the centers
	 * of the faces. This is done by applying penalty to the cost of traversal to
	 * non-center vertices. When this preference is PREFER_BORDER, we prefer a path
	 * that goes through border-vertices.
	 * 
	 * PREFER_CENTER is the default.
	 */
    public int travelPreferrence = PREFER_CENTER ;
    
    /**
     * A threshold for the minimum area of a face to be considered when adding
     * center points to the navigation graph. Only faces whose area is at least this
     * threshold will have a center-point added as an extra navigation node.
     */
    float faceAreaThresholdToAddCenterNode ;
    
    
    Pathfinder pathfinder ;
    
    /**
     * Create an intance of SurfaceNavGraph from a given mesh. Note that for each
     * face in the mesh, this constructor will also add the center-point of the face
     * in the created navigation graph. This is done if the face's area is large enough;
     * that is, if it exceeds the threshold faceAreaThresholdToAddCenterNode.
     */
    public SurfaceNavGraph(Mesh mesh, float faceAreaThresholdToAddCenterNode) {
        super();
        this.faceAreaThresholdToAddCenterNode = faceAreaThresholdToAddCenterNode ;
        // copying the vertices and faces:
        vertices.addAll(mesh.vertices) ;
        numberOfBaseVertices = vertices.size() ;
        faces = new ArrayList<>() ;
        faces.addAll(mesh.faces) ;
        // copying the edges, and as we do build the reachability relation as well:
        for (Edge e : mesh.edges) {
        	this.edges.put(e);
        }
        
        // calculating the types of the current vertices:
        verticesType = new ArrayList<>() ;
        for(int k=0; k<numberOfBaseVertices; k++) {
        	verticesType.add(VertexType.OTHER) ;
        }
        Set<Integer> borderVertices ;
        for (Edge e : mesh.edges) {
        	int countMembership = 0 ;
        	for (Face face : mesh.faces) {
        		if (face.containsEdge(e)) countMembership++ ;
        		if (countMembership >= 2) {
        			// ok so the edge e is shared, and hence not a border edge
        			break ;
        		}
        	}
        	if (countMembership == 1) {
        		// e is a border edge:
        		verticesType.set(e.i, VertexType.BORDER) ;
        		verticesType.set(e.j, VertexType.BORDER) ;	
        	}
        }
        
        // Add the center points of the Faces to the nav-Graph, and connect
        // their corners to the corresponding center-point:
        faceToCenterIdMap = new HashMap<>() ;
        for (Face face : faces) {
        	
        	// don't add a center-point if the face is too small:
        	if (face.area(mesh.vertices) < faceAreaThresholdToAddCenterNode) continue ;
        	
        	Vec3 center = face.center(mesh.vertices) ;
        	int center_id = vertices.size() ;
        	// add the center to the set of vertices:
        	vertices.add(center) ;
        	// mark its type:
        	verticesType.add(VertexType.CENTRE) ;
        	// remember what the id of this center:
        	faceToCenterIdMap.put(face,center_id) ;
        	// add edges that connect the corners of the Face to this center
        	for (Integer corner : face) {
        		edges.put(new Edge(corner,center_id));
        	}	
        }
        
        // We will also connect the centers of connected Faces:
        for (int i = 0; i < faces.size(); i++) {
            for (int j = 0; j < i; j++) {
            	var face1 = faces.get(i) ;
            	var face2 = faces.get(j) ;
                if (Face.isConnected(face1,face2)) {
                	var center1 = faceToCenterIdMap.get(face1) ;
                	var center2 = faceToCenterIdMap.get(face2) ;
                    if (center1 != null && center2 != null) edges.put(new Edge(center1,center2));
                }
            }
        }
        
        // By default we will mark all vertices as "unseen"
        seenVertices = new ArrayList<>() ;
        wipeOutMemory() ;
        
        // setting A* as the default pathfinder:
        pathfinder = new AStar() ;
    }
    
    
    
    /**
     * Mark all vertices as "unseen".
     */
    public void wipeOutMemory() {
    	seenVertices.clear();
    	int N = vertices.size() ;
    	for(int k = 0; k<N; k++) {
        	seenVertices.add(false) ;
        }
    }
   
    
    public void setPathFinder(Pathfinder pf) {
    	pathfinder = pf ;
    }
    
    /**
     * Mark the given vertices as "seen" for the purpose of memory-based navigation.
     * Since Faces' center-points were added artificially (they were not explicitly
     * present in the mesh-data used to build this nav-graph), the agent that calls
     * this method may not check if it also saw center-points. This method will
     * therefore take the heuristic to mark a center-point as seen if one of its
     * non-center neighbor is marked as seen.
     */
    public void markAsSeen(List<Integer> seen) {
    	for (Integer v : seen) {
    		// mark v as seen:
    		seenVertices.set(v,true) ;
    		// additionally, if v is not a center, then mark the center connected to
    		// it as "seen".
    		if (verticesType.get(v) != VertexType.CENTRE) {
    			var neighbors = edges.neighbours(v) ;
    			var vloc = vertices.get(v) ;
    			for (Integer z : neighbors) {
    				var zloc = vertices.get(z) ;
    				// the 2nd cond is a HACK!
    				if (verticesType.get(z) == VertexType.CENTRE
    						//|| Vec3.dist(vloc,zloc) <= 0.4
    						)
    					seenVertices.set(z,true) ;	
    			}
    		}
    	}
    }
    
    public void markAsSeen(int ... seen) {
    	List<Integer> seen_ = new LinkedList<>() ;
    	for (int k : seen) {
    		seen_.add(k) ;
    	}
    	markAsSeen(seen_) ;
    }
    
    public int numberOfSeen() {
    	return (int) seenVertices.stream().filter(b -> b==true).count() ;
    }
    
    /**
     * Check if the line from x to y is blocked by any of the
     * blocking obstacles.
     */
    boolean isBlocked(Vec3 x, Vec3 y) {
    	for (var obs : obstacles) {
			if (obs.isBlocking && obs.obstacle.intersects(new Line(x,y)))  {
				return true ;
			}
		}
    	return false ;
    }
    
    
    /**
	 * This method returns a vertex v in the navgraph which is 'nearest' to the given
	 * 3D location, such that the straight line between the location and this vertex v
	 * is unobstructed.
	 * 
	 * To find v, the method first searches for a face F, whose distance to the given
	 * location is below the threshold faceDistThreshold. Then v is searched among the vertices
	 * on this face F. The method returns one with the least distance to the location,
	 * where a straight line between them is unobstructed by any of the obstacles.
	 * 
	 * If no such F nor v can be found, the method returns null.
	 * 
	 * The method ignores whether the vertices of F has been seen/explored or not.
	 */
    public Integer getNearestUnblockedVertex(Vec3 location, float faceDistThreshold) {
    	// first find a face that contains the location:
    	//System.out.println(">> anchor location: " + location) ;
    	Face face = null ;
    	//float bestDistanceSofar = Float.MAX_VALUE ;
    	int k = 0 ;
    	for (Face f : faces) {
    		var dist = f.distFromPoint(location, vertices) ;
    		
    		//System.out.println(">>    Face " + k + "," + f + ", distance: " + dist) ;
    		/*
    		for (var corner : f.vertices) {
    			System.out.println("    " + vertices.get(corner)) ;
    		}
    		*/
        	if (dist <= faceDistThreshold) {
    			// found one ... we'll grab it:
        		face = f ;
        		break ;
    		}
    		k++ ;
    	}
    	if (face == null) {
    		// well... then the location is not even in the mesh:
    		System.out.println(">> cannot find any face close enough!") ;
    		return null ;
    	}
    	
    	// Find the closest vertex on the Face;
    	// start with calculating the distance to the face center, if we keep track of its center:
    	Integer best = null ;
    	float best_distsq = Float.POSITIVE_INFINITY ;
    	Integer v = faceToCenterIdMap.get(face) ; 
    	Vec3 v_loc = null ;
    	if (v!=null) {
    		v_loc = vertices.get(v) ;
        	if (! isBlocked(location,v_loc)) {
        		best = v ;
        		best_distsq = Vec3.sub(location,v_loc).lengthSq() ;
        	}
    	}
    	for(int w : face.vertices)
    	{
    		//System.out.println("=== " + w) ;
    		v = w ;
    		v_loc = vertices.get(v) ;
    		//System.out.println(">>   " + location + " --> "  + v + " " + v_loc + ": blocked " + isBlocked(location,v_loc)) ;
    		if (isBlocked(location,v_loc)) continue ;
    		var distsq = Vec3.sub(location,v_loc).lengthSq() ;
    		if (distsq < best_distsq) {
    			best = v ;
    			best_distsq = distsq ;
    		}
    	}
    	return best ;
    }
    
    
    /**
     * If true, the pathfinder will assume that the whole navgraph has been "seen",
     * so no vertex would count as unreacahble because it is still unseen.
     * This essentially turns off memory-based path finding.
     * The default of this flag is false.
     */
    public boolean perfect_memory_pathfinding = false ;
    
    /**
     * Return the neighboring vertices of id. Only neighbors marked as "seen"
     * will be returned (so as to support the memory-based navigation).
     */
    @Override
    public Iterable<Integer> neighbours(int id) {
    	// clone the neigbor-set to prevent side effect when we next filter it:
    	HashSet<Integer>neighbors = new HashSet<>() ;
    	for (var v : edges.neighbours(id)) {
    		neighbors.add(v) ;
    	}
    	if (perfect_memory_pathfinding) return neighbors ;
    	// only let "seen" neighbors through:
    	neighbors.removeIf(v -> ! seenVertices.get(v));
        return neighbors ;
    }
    
    
    /**
     * Defining the distance between two NEIGHBORING vertices.
     */
    @Override
    public float heuristic(int from, int to) {
    	float distance = super.heuristic(from,to) ;
    	//System.out.println("----" + from + " --> " + to + " dist = " + distance) ; 
    	if (distance == Float.POSITIVE_INFINITY) return distance ;
    	//System.out.println(">>>>") ;
        // apply penalty to the distanced according to the travel preference:
        switch(travelPreferrence) {
           case PREFER_CENTER : 
        	   if (verticesType.get(from) == VertexType.CENTRE) {
        		   distance = 0.8f * distance ; 
        	    }
        	   //System.out.println(">> " + from + "->" + to + ", dist: " + distance) ; 
        	   break ; 
           case PREFER_BORDER : 
        	   if (verticesType.get(to) == VertexType.BORDER) {
            	   distance = 0.8f * distance ;
        	   }
               break ; 
           default :
        }
        //System.out.println(">> distance: " + distance) ;
        return distance ;
    }
    
    /**
     * Return a path from the given start-vertex to the goal-vertex. If the goal-vertex
     * is reachable, a path will be returned. A* is used as the default path-finder,
     * which should return the optimal path to get to the goal.
     * 
     * The path-finding will be memory-based. That is, only navigation over vertices
     * marked as seen will be possible.
     */
    public ArrayList<Integer> findPath(int start, int goal) {
    	return pathfinder.findPath(this, start, goal) ;
    }
    
    /**
     * The same as the other findPath. This will return a path from a vertex v0 closest
     * to the given start position, to a vertex closest to the given goal location.
     * It is up to the agent to figure out how to get from its own physical start
     * location to the starting vertex, and to get from the goal vertex vn to its
     * actual goal location.
     * 
     * The method calculates the start and goal-nodes such that the straight line
     * between them and the corresponding start and goal locations are not blocked
     * by any of the blocking obstacles.
     * 
     * The parameter faceDistThreshold is a threshold defining how far the Face F0 on which
     * v0 is from the start location is allowed to be. And similarly how far the
     * face Fn on which vn is, from the goal location is allowed to be.
     */
    public ArrayList<Integer> findPath(Vec3 start, Vec3 goal, float faceDistThreshold) {
    	Integer startNode = getNearestUnblockedVertex(start, faceDistThreshold) ;
    	if (startNode == null) return null ;
    	//System.out.println("**  start-node: " + startNode) ;
    	Integer goalNode = getNearestUnblockedVertex(goal, faceDistThreshold) ;
    	if (goalNode == null) return null ;
    	//System.out.println("**  goal-node: " + goalNode) ;
    	return findPath(startNode,goalNode) ;
    }
    
    /**
     * This returns the set of frontier-vertices. A vertex is a frontier vertex if
     * it is a seen/explored vertex and it has at least one unexplored and unblocked
     * neighbor.
     * 
     * The method returns pairs of (v,z) where v is a frontier and z is one of its
     * unexplored and unblocked neighbor.
     */
    List<Pair<Integer,Integer>> getFrontierVertices() {
    	var frontiers = new LinkedList<Pair<Integer,Integer>>() ;
    	if (perfect_memory_pathfinding) {
    		// when perfect memory is assumed, all nodes are considered as explored.
    		// so, there is no frontiers either.
    		return frontiers ;
    	}
    	int N = vertices.size() ;
    	for (int v = 0 ; v<N ; v++) {
    		Vec3 vloc = vertices.get(v) ;
    		if (seenVertices.get(v))  {
    			for (Integer z : edges.neighbours(v)) {
    				if (! seenVertices.get(z)
    					&& ! isBlocked(vloc, vertices.get(z))) {
    					frontiers.add(new Pair(v,z)) ;
    					break ;
    				}
    			}
    		}
    	}
    	return frontiers ;
    }
    
    
    /**
	 * Find a path to an unexplored and unblocked vertex w which is the geometrically
	 * 'closest' to the given the given start location. Note that the path ends in that
	 * unexplored vertex.
	 * 
	 * More precisely, we first look an explored vertex v in the vicinity of the given start-location,
	 * that can be reached by a straight line from the start location, without being blocked.
	 * The w meant above is the closest to this intermediate v. The face F on which this v
	 * is located must be of distance at most faceDistThreshold from the start-location.
	 * 
	 * If no no such path nor intermediate v can be found, the method returns null.
	 */
    public List<Integer> explore(Vec3 startLocation, float faceDistThreshold) {
    	var startVertex = getNearestUnblockedVertex(startLocation, faceDistThreshold) ;
    	if (startVertex == null) return null ;
    	return explore(startVertex) ;
    }
    
    /**
	 * Find a path to an unexplored and unblocked vertex which is the geometrically
	 * closest to the given starting vertex. Note that the path ends in that
	 * unexplored vertex.
	 * 
	 * If no no such path can be found, the method returns null.
	 */
    public List<Integer> explore(int startVertex) {
    	
    	var frontiers = getFrontierVertices() ;
    	if (frontiers.isEmpty()) return null ;
    	// sort the frontiers ascendingly, by their geometric distance to the start-vertex:
    	Vec3 startLocation = vertices.get(startVertex) ;
    	frontiers.sort((p1,p2) -> Float.compare(
    			Vec3.dist(vertices.get(p1.fst), startLocation), 
    			Vec3.dist(vertices.get(p2.fst), startLocation))) ;
    	
    	for (var front : frontiers) {
    		var path = findPath(startVertex,front.fst) ;
    		if (path != null) {
    			// ok, so reaching the frontier front.fst is possible;
    			// we will also add the unexplored and unblocked neighbor of
    			// front.fst to the path:
    			path.add(front.snd) ;
    			return path ;
    		}
    	}
    	return null ;
    }

}
