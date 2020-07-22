package eu.iv4xr.framework.mainConcepts;

import nl.uu.cs.aplib.agents.State ; 

public class W3DAgentState extends State {
	
	WorldModel wom ;
	
	/**
	 * The vertices in the wom.worldNavigationGraph which are known/seen by
	 * the agent that owns this state. Vertex i is known iff knownVertices[i]
	 * is true.
	 */
	boolean[] knownVertices;
	
	/**
	 * The timestamp of when a vertex becomes known to the agent. So, if vertex
	 * i becomes known to the agent at time t, then knownVerticesTimestamps[i]= t.
	 */
    long[] knownVerticesTimestamps ;
	
	public W3DAgentState setEnvironment(W3DEnvironment env) {
		super.setEnvironment(env) ;
		return this ;
	}
	
	public W3DEnvironment env() {
		return(W3DEnvironment) super.env() ;
	}

}