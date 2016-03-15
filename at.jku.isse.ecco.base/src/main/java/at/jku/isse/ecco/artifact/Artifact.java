package at.jku.isse.ecco.artifact;

import at.jku.isse.ecco.plugin.artifact.ArtifactData;
import at.jku.isse.ecco.sg.SequenceGraph;
import at.jku.isse.ecco.tree.Node;

import java.util.List;
import java.util.Optional;

/**
 * A generic artifact that stores the actual data and references to other artifacts.
 *
 * @author JKU, ISSE
 * @author Hannes Thaller
 * @version 1.0
 */
public interface Artifact<DataType extends ArtifactData> {

	public static final String PROPERTY_REPLACING_ARTIFACT = "replacingArtifact";

	public static final int UNASSIGNED_SEQUENCE_NUMBER = -1;

	public static final String MARKED_FOR_EXTRACTION = "marked";


	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);

	@Override
	public String toString();


	public DataType getData();


	public boolean useReferencesInEquals();

	public void setUseReferencesInEquals(boolean useReferenesInEquals);

	public boolean isAtomic();

	public void setAtomic(boolean atomic);

	public boolean isOrdered();

	public void setOrdered(boolean ordered);

	public SequenceGraph getSequenceGraph();

	public void setSequenceGraph(SequenceGraph sequenceGraph);

	public int getSequenceNumber();

	public void setSequenceNumber(int sequenceNumber);

	public boolean isSequenced();


	public SequenceGraph createSequenceGraph();


	/**
	 * Returns the containing node from the artifact tree.
	 *
	 * @return The containing node.
	 */
	Node getContainingNode();

	/**
	 * Sets the containing node from the artifact tree.
	 *
	 * @param node that this artifact contains (@Nullable)
	 */
	void setContainingNode(Node node);

	// uses and usedBy

	/**
	 * Returns the references to the artifacts that this object uses. A used by association describes from which other artifact this artifact is used in some manner.
	 *
	 * @return References to artifacts that this object uses.
	 */
	List<ArtifactReference> getUsedBy();

	/**
	 * Returns the references to the artifacts that this object uses. A uses by association describes which other artifact this artifact uses in some manner.
	 *
	 * @return The references to the used artifacts
	 */
	List<ArtifactReference> getUses();

	/**
	 * Sets the references to the artifact which this object is used by.
	 *
	 * @param references to the object that uses this object
	 */
	void setUsedBy(List<ArtifactReference> references);

	/**
	 * Sets a references to an artifact this object uses.
	 *
	 * @param references to the used object
	 */
	void setUses(List<ArtifactReference> references);

	/**
	 * Adds a used by reference to the artifact. A used by association describes from which other artifact this artifact is used in some manner.
	 *
	 * @param reference to the artifact that uses this artifact
	 */
	void addUsedBy(ArtifactReference reference);

	/**
	 * Adds a uses reference to the artifact. A uses by association describes which other artifact this artifact uses in some manner.
	 *
	 * @param reference to the artifact that uses this artifact
	 */
	void addUses(ArtifactReference reference);

	// properties

	/**
	 * Returns the property with the given name in form of an optional. The optional will only contain a result if the name and the type are correct. It is not possible to store
	 * different types with the same name as the name is the main criterion. Thus using the same name overrides old properties.
	 *
	 * @param name of the property that should be retrieved
	 * @return An optional which contains the actual property or nothing.
	 */
	<T> Optional<T> getProperty(String name);

	/**
	 * Adds a new property to this artifact. It is not possible to store different types with the same name as the name is the main criterion. Thus using the same name overrides
	 * old properties.
	 *
	 * @param property that should be added
	 */
	<T> void putProperty(String name, T property);

	/**
	 * Removes the property with the given name. If the name could not be found in the map it does nothing.
	 *
	 * @param name of the property that should be removed
	 */
	void removeProperty(String name);

}
