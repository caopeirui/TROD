package ch.ethz.systems.netbench.ext.wcmp.routingweight;

import java.util.*;

public class Path {
		
	private List<Integer> path; // The path is represented as a list of nodes.
		
	private String pathStr;

	public Path(List<Integer> pathList) {
		this.path = pathList;
		this.pathStr = "";
		for (int pathNode : this.path) {
			this.pathStr += ("" + pathNode + ",");
		}
	}

	/**
	 * Returns the length of this path
	 */
	public int getPathLength() {
		return path.size() - 1;
	}

	/**
	 * Returns the source node of this path
	 */
	public int getSrc() {
		return path.get(0);
	}

	/**
	 * Returns the destination node of this path
	 */
	public int getDst() {
		return path.get(path.size() - 1);
	}

	/** 
	 * Returns the list that represents a path.
	 **/
	public List<Integer> getListRepresentation() {
		return this.path;
	}

	/**
	 * The following functions are for
	 **/
	@Override
   	public int hashCode() {
       	return this.pathStr.hashCode();
   	}

   	@Override
   	public boolean equals(Object o) {
   		Path anotherPath = (Path) o;
    	if (path.size() != anotherPath.getPathLength()) {
    		return false;
    	}
    	// Go in and check each and every node along the path
    	List<Integer> anotherPathListRepresentation = anotherPath.getListRepresentation();
    	for (int i = 0; i < anotherPathListRepresentation.size(); ++i) {
    		if (anotherPathListRepresentation.get(i) != this.path.get(i)) {
    			return false;
    		}
    	}
    	return true;
    }
}