package eu.iv4xr.framework.mainConcepts;

import java.io.Serializable;
import java.util.* ;

import eu.iv4xr.framework.spatial.Vec3;
import nl.uu.cs.aplib.mainConcepts.Environment;



/**
 * This describes a fragment of a virtual world in terms of how it is structurally
 * populated by in-world entities. This fragment can represent what an agent currently
 * sees. We can also use the same representation to represent the agent's belief on
 * how the world is structured; this may incorporate its past knowledge which may no
 * longer be up to date.
 */
public class WorldModel {
	
	/**
	 * The id of the agent that owns this World Model.
	 */
	public String agentId ;
	
	/**
	 * The position of the agent that owns this World Model.
	 */
	public Vec3 position ; // agent's position
	public Vec3 velocity ; // agent's velocity
	public Vec3 extent ; // agent's dimension (x,y,z size/2)
	
	/**
	 * Represent the last time this WorldModel is updated with fresh sampling. Note
	 * that sampling may only update the state of some of the entities, rather than
	 * all, because the agent can only see some part of the world.
	 */
	public long timestamp = -1 ;
	
	/**
	 * In-world entities that populate this World Model.
	 */
	public Map<String,WorldEntity> elements = new HashMap<>() ;
	
	public WorldModel() { }
	
	/**
	 * Increase the time stamp by one unit.
	 */
	public void increaseTimeStamp() { timestamp++ ; }
	
	/**
	 * Search a top-level entity with the given id. Note that this method does NOT search 
	 * recursively in the set of sub-entities.
	 */
	public WorldEntity getElement(String id) {
		return elements.get(id) ;
	}

	/**
	 * This will add or update an entity e in this WorldModel. The entity can be an
	 * entity observed by the agent that owns the WorldModel, or it can also be
	 * information sent by another agent.
	 * 
	 * IMPORTANT: this update may have some side effect on e itself, so e should be
	 * a fresh instance. In particular, if e is sent from another agent, it should
	 * be cloned first before it is sent.
	 * 
	 * Case-1: a version ex of e is already in this WorldModel, its state will be
	 * updated with e if e's timestamp is more recent and if e's state is different.
	 * More specifically, this means that we replace ex with e, and then link ex as
	 * e's previous state.
	 * 
	 * Case-2: e is more recent, but its state is the same as ex. We do not add e to
	 * the world model; we update ex' timestamp to that of e.
	 * 
	 * Case-3: e is older than ex, but more recent than ex.previousState. In this
	 * case we replace ex.previousState with e.
	 * 
	 * Case-4: e has no copy in this WorldModel. It will then be added.
	 * 
	 * The method returns the Entity f which then represents e in the WorldModel.
	 * Note that e reflects some state change in the WorldModel if and only if the
	 * returned f = e (pointer equality).
	 */
	public WorldEntity updateEntity(WorldEntity e) {
		if (e==null) throw new IllegalArgumentException("Cannot update a null entity in a World Model.") ;
		var current = elements.get(e.id) ;
		if (current==null) {
			// e is new:
			elements.put(e.id,e) ;
			return e ;
		}
		else {
			// case (1) e is at least as recent as "current":
			if (e.timestamp >= current.timestamp) {
				// check first if there is a state change
				if (e.hasSameState(current)) {
					var startTimeStutter = current.lastStutterTimestamp ;
					if (startTimeStutter<0) startTimeStutter = current.timestamp ;
					// keep current; just update its timestamp:
					current.assignTimeStamp(e.timestamp);
					// update the stutter-timestamp as well:
					current.lastStutterTimestamp = startTimeStutter ;
					return current ;
				}
				else {
					// the entity has changes its state 
					// (its start-stutter-time should already be initialized to -1)
					elements.put(e.id,e) ;
					e.linkPreviousState(current);
					//System.out.println("%%% updating " + e.id) ;
					return e ;
				}
			}
			else { // case (2): e is older than current:
				var prev =  current.getPreviousState() ;
				//System.out.println(">>> current: " + current.id + ", e: " + e.id
				//		           + ", prev: " + prev) ;
				if (prev==null || e.timestamp > prev.timestamp) {
					if(! e.hasSameState(current)) 
						current.linkPreviousState(e);
				}
				//System.out.println(">>> prev: " + current.getPreviousState()) ;
		return current ;
			}
		}
	}
	
	/**
	 * This will merge a sampled (and more recent) observation (represented as
	 * another WorldModel) made by the agent into WorldModel. 
	 * 
	 * IMPORTANT: do not use this to merge WorldModel from other agents. 
	 * Use instead {@link updateEntity} for this purpose. Also note if an agent
	 * want to send information about an entity it knows to share it with another
	 * agent, it should send a copy of the entity as {@link updateEntity} may
	 * have side effect on the entity.
	 * 
	 * This method will basically
	 * add all entities in the new observation into this WorldModel. More precisely,
	 * if an entity e was not in this WorldModel, it will be added. But if e was
	 * already in the WorldModel, it will not be literally be added anew. Instead,
	 * we update e's state in the WorldModel to reflect the new information. The old
	 * state (so, the state before the update) will still be linked to the new
	 * state, just in case the agent needs it. Only one past-state will be stored
	 * though.
	 * 
	 * The method will check if the given observation is at least as recent as this
	 * WorldModel. It fails if this is not the case. If the check passes, the the
	 * timestamp of this WorldModel will be updated to that of the observation. All
	 * entities that were added (so, all entities in observation) will get this new
	 * timestamp as well to reflect the fact that their states are freshly sampled.
	 * 
	 * The method returns the list of entities that were identified as changing the
	 * state of this WorldModel (e.g. either because they are new or because their
	 * states are different).
	 * 
	 * IMPORTANT: note that the implemented merging algorithm is additive. That is,
	 * it adds entities into the target WorldModel or updates existing ones, but it will 
	 * NEVER REMOVE an entity. In theory this can be handled with some bit of reasoning,
	 * e.g. when the agent has an unobstructed line of sight to the entity, and the
	 * entity is within the agent's visibility range, but it is not present
	 * in the new observation, then we can conclude that the entity is no longer in
	 * the world, and hence can be removed from the WorldModel. Implementing this reasoning
	 * is still TODO.
	 */
	public List<WorldEntity> mergeNewObservation(WorldModel observation) {
        //check if the observation is not null
        if (observation == null) throw new IllegalArgumentException("Null observation received");
        if (observation.timestamp < this.timestamp) 
        	throw new IllegalArgumentException("Cannot merge an older WorldModel into a newer one.");
           
        // update agent's info:
        this.position = observation.position ;
        this.velocity = observation.velocity ;
        this.extent = observation.extent ;
        
        // Add the newly seen entities, or incorporate their change. We will also
        // maintain those entities that induce state change in this WorldModel.
        List<WorldEntity> impactEntities = new LinkedList<>() ;
        for(WorldEntity e : observation.elements.values()) {
        	var f = this.updateEntity(e) ;
        	if (e==f) {
        		//System.out.println("%%% updating " + e.id) ;
        		// if they are equal, then e induces some state change in the WorldModel.
        		impactEntities.add(e) ;
        	}
        }
        
        // now update the time stamp of this WorldMap to that of the received observation:
        this.timestamp = observation.timestamp ;
        
        //System.out.println("%%% #impactEntities =" + impactEntities.size()) ;
        return impactEntities ;
	}
	
    
	/**
	 * This is used to merge an older observation into this one. E.g. it can be an observation
	 * sent by another agent.
	 */
    public void mergeOldObservation(WorldModel observation) {
    	//check if the observation is not null
        if (observation == null) throw new IllegalArgumentException("Null observation received");
        if (observation.timestamp >= this.timestamp) 
        	throw new IllegalArgumentException("This method expect an older WorldModel to be merged into a newer one.");
        
        for (WorldEntity e : observation.elements.values()) {
        	this.updateEntity(e) ;
        }
    }

	
	/**
	 * A query method that returns the set of possible interaction-types(e.g.
	 * "push", "pick-up") that an agent can do on in-world entities. For each
	 * interaction-type I, the method also returns a mapping to the type of entities
	 * that interactions of type I would be applicable, e.g. "push" would be
	 * applicable to "button"s. The set of available interaction types and their
	 * mapping to entities-types depend on the specific game under test, but this
	 * set and mapping should be static.
	 * 
	 * Note that when e.g. "pick-up" is applicable to entities of type "flower", it
	 * only means that the agent can potentially pick up a flower. Whether this is
	 * actually possible still depends on e.g. the agent state, e.g. if it stands
	 * close enough to the flower. The logic of state-dependent applicability can be
	 * queried through the method canInteract(). Note that even then, it is still up
	 * to the game under test to have the final say whether the interaction, when
	 * attempted, would be successful.
	 * 
	 * This method should be implemented by the subclass. So, override this.
	 */
	public Map<String,Set<String>> availableInteractionTypes() {
		throw new UnsupportedOperationException() ;
	}
	
	/**
	 * A query method that checks whether it would be possible for the agent to do
	 * an interaction of type ity (e.g. "push") on the specified target entity.
	 * The method will inspect both the agent state (e.g. its position) and the
	 * target entity's state (as far as these informations are available in this
	 * WorldModel) to infer if the interaction would be possible.
	 * 
	 * Note that the inference done by this method would be based on the information
	 * available in this WorldModel, which may only partially represent the actual
	 * game state. So, even if this method returns a "true", executing the
	 * interaction on the game under test may still be unsuccessful.
	 * 
	 * This method should be implemented by the subclass. So, override this.
	 */
	public boolean canInteract(String interactionType, WorldEntity e) {
		throw new UnsupportedOperationException() ;
	}
	
	/**
	 * A query method that checks whether the given entity can block movement. If
	 * so, it returns true, and else false. This method should be implemented by the
	 * subclass. So, override this.
	 */
	public boolean isBlocking(WorldEntity e) {
		throw new UnsupportedOperationException() ;
	}
	
	/**
	 * Execute an interaction of the specified type on the given target entity.
	 * This means that the command to do the interaction will actually be sent to
	 * the game under test. The method also expects an instance of Environment
	 * because it is needed as the party that will actually send commands to the
	 * game.
	 * 
	 * It returns an observation of the real environment, sampled just after the
	 * interaction.
	 */
	public Object interact(W3DEnvironment env, 
			String interactionType, 
			WorldEntity e) {
		return env.interact(agentId, e.id, interactionType) ;
	}
	
	// some examples of standard actions that the agent can do, other than interacting
	// with in-game entities:
	
	/**
	 * Sample the world around the agent. This will return an instance of
	 * WorldModel, representing the part of the world that the agent currently sees.
	 */
	public WorldModel observe(W3DEnvironment env) {
		return env.observe(agentId) ;
	}

	/**
	 * The agent will move some (small) distance is towards the given target location. This
	 * method will NOT deal with obstacles in-between. Assuming that the space between the
	 * agent and the target location is clear of obstacle, whether the agent can cover
	 * the full distance or only some of it depends on the speed of the agent. E.g. is the
	 * speed is fixed and the distance is more than what this speed can cover in a single
	 * "cycle", then obviously the full distance will not be covered.
	 * 
	 * It returns an observation of the real environment, sampled at the end of the move.
	 */
	public WorldModel moveToward(W3DEnvironment env, Vec3 targetLocation) {
		return env.moveToward(agentId, position, targetLocation)  ;
	}


}
