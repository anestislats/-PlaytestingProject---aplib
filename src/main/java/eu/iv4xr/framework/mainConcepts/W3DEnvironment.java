package eu.iv4xr.framework.mainConcepts;

import eu.iv4xr.framework.exception.Iv4xrError;
import eu.iv4xr.framework.extensions.pathfinding.SurfaceNavGraph;
import eu.iv4xr.framework.spatial.Vec3;
import eu.iv4xr.framework.spatial.meshes.Mesh;
import nl.uu.cs.aplib.mainConcepts.Environment;
import nl.uu.cs.aplib.mainConcepts.Environment.EnvOperation;
import nl.uu.cs.aplib.utils.Pair;

/**
 * An extension of {@link nl.uu.cs.aplib.mainConcepts.Environment}. It
 * adds methods typical for interacting with a 3D-virtual-world 
 * environment. Examples of such an environment are 3D games or simulators.
 * 
 * @author Wish
 *
 */
public class W3DEnvironment extends Environment {
	

	static public String LOADWORLD_CMDNAME  = "LoadWorld" ;
	static public String OBSERVE_CMDNAME    = "Observe" ;
	static public String MOVETOWARD_CMDNAME = "Move" ;
	
	/**
	 * A polygon-mesh describing the navigable surface of the 3D-world represented by
	 * this environment.
	 */
	public Mesh worldNavigableMesh ;
	
	/**
	 * Execute an interaction of the specified type on the given target entity in
	 * the real environment.
	 * 
	 * The parameter interactionType is a string specifying the name of the
	 * interaction to do, e.g. "Open" or "TurnOn". This string should not be the
	 * same command names that are already pre-defined by this class, such as
	 * OBSERVE_CMDNAME.
	 * 
	 * This method has no further argument to send to the real environment.
	 * 
	 * The method should return a new observation by the specified agent, sampled
	 * after the interaction.
	 * 
	 * @param agentId  The id of the agent that does the interaction.
	 * @param targetId The id of the entity that is the target of the interaction.
	 */
	public WorldModel interact(String agentId, String targetId, String interactionType) {
		return (WorldModel) sendCommand(agentId, targetId, interactionType, null, WorldModel.class);
	}
	
	/**
	 * Send a command to the real environment that will cause it to send back what the 
	 * agent of the given id observes in the real environment. The observation will be
	 * parsed into an instance of {@link eu.iv4xr.framework.mainConcepts.WorldModel}.

	 * @param agentId The id of the agent whose observation is requested.
	 * @return An instance of WorldModel representing what the specified agent observes.
	 */
	public WorldModel observe(String agentId) {
		return (WorldModel) sendCommand(agentId,null,OBSERVE_CMDNAME,null,WorldModel.class) ;
	}	
	
	/**
	 * A command to instruct an agent to move a small distance towards the given
	 * target location.
	 * How far the agent actually moves depends on the real environment.
	 * Typically, the calling agent will execute interactions/commands in update
	 * cycles. Then it depends on how fast time proceeds in the real environment as
	 * we advance from one agent's update-cycle to the next. A possible setup is to
	 * make the real environment to run in sync with the agent's cycles and to fix
	 * the simulated time between cycles, e.g. 1/30-th second. In this case, the
	 * agent will then move to some distance of the specified velocity/30.
	 * 
	 * The method should return a new observation, sampled at the end of its
	 * movement.
	 */
	public WorldModel moveToward(String agentId, Vec3 agentLocation, Vec3 targetLocation) {
		return (WorldModel) sendCommand(agentId,null,
				MOVETOWARD_CMDNAME,
				new Pair(agentLocation,targetLocation),
				WorldModel.class) ;
	}
	
	/**
	 * Send a command to the real environment that should cause it to send over
	 * the navigation-mesh of its 3D world. This mesh is assumed to be static
	 * (does not change through out the agents' runs).
	 */
	public void loadWorld() {
		worldNavigableMesh = (Mesh) sendCommand(null,null,LOADWORLD_CMDNAME,null,Mesh.class) ;
		if (worldNavigableMesh==null) 
			throw new Iv4xrError("Fail to load the navgation-graph of the world") ;
	}
	
	/**
	 * You need to implement this method. There are a number of pre-defined command
	 * names, namely:
	 * 
	 *   cmd.command is LOADWORLD_CMDNAME: this should ask the real-environment
	 *   to send back an navigation-mesh. This method should package it as
	 *   an instance of Mesh and return it.
	 *   
	 *   cmd.command is OBSERVE_CMDNAME: this should ask the real-environment to
	 *   send over the observation of agent cmd.invokerId. This method should
	 *   package the result as an instance of WorldModel and return it.
	 *   
	 *   cmd.command is MOVETOWARD_CMDNAME: this should ask the real-environment
	 *   to move its entity/agent cmd.invokerId in the direction specified by
	 *   cmd.arg. This method should also obtain the observation of the said
	 *   agent at the end of the move, and package the observation as an 
	 *   instance of WorldModel and return it.
	 *   
	 *   cmd.command has other values: this should ask the real-environment
	 *   to apply an interaction by the agent cmd.invokerId on the entity
	 *   specified by cmd.targetId, and with interaction type specified by
	 *   cmd.command (e.g. "OPEN" or "CLOSE"). This method should also obtain 
	 *   the observation of the said agent at the end of the interaction, and 
	 *   package the observation as an instance of WorldModel and return it.
	 */
	protected Object sendCommand_(EnvOperation cmd) {
		 throw new UnsupportedOperationException() ;
	}
	
	
}
