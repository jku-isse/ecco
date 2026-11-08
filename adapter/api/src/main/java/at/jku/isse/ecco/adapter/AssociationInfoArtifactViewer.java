package at.jku.isse.ecco.adapter;


import java.util.Collection;

public interface AssociationInfoArtifactViewer extends ArtifactViewer {

    void setAssociationInfos(Collection<AssociationInfo> associationInfos);

    /**
     * Whether to show this viewer's per-line association-details panel (populated by hovering a
     * line within the code view itself) - on by default. A passive, non-interactive embedding (e.g.
     * a hover preview elsewhere in the GUI, where the user isn't going to mouse over individual
     * lines inside a small preview) should turn it off rather than reserve space for a panel that
     * can never usefully populate there. No-op by default so implementations with no such panel
     * (e.g. the text adapter's line-only viewer) don't need to override this.
     */
    default void setShowDetailsPanel(boolean show) {
    }

    /**
     * Whether {@link #showTree} marks the node it was given (yellow), or only scrolls to it - on by
     * default. The knowledge graph's association previews turn it off: there the node is only where
     * the association starts, not something the user selected. No-op by default for viewers that
     * mark nothing.
     */
    default void setHighlightSelection(boolean highlight) {
    }

}
