package at.jku.isse.ecco.gui.view.artifacts;

import at.jku.isse.ecco.adapter.AssociationInfo;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.module.Condition;
import javafx.beans.property.*;
import javafx.scene.paint.Color;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedList;

public class AssociationInfoImpl implements AssociationInfo {
    private final Association association;

    private final BooleanProperty selected;

    private final ObjectProperty<Color> color;

    private final IntegerProperty numArtifacts;

    /**
     * Empty until a "Minimize Presence Conditions" run (started from the toolbar, or by a review; see
     * {@link at.jku.isse.ecco.gui.MinimizationResults}) fills it in -- kept live in sync by a
     * listener on the shared model, not recomputed per row build.
     */
    private final StringProperty minimizedCondition;

    private final LinkedList<PropertyChangeListener> listeners;

    /**
     * The full condition as shown, computed once in the background - the Condition column computed
     * it on the FX thread for every cell, and for both rows of every comparison when sorted: 7.8 s
     * for tinycc's 5,262 associations. Null: computed when asked.
     */
    private String fullCondition;

    public AssociationInfoImpl(Association association) {
        this(association, null);
    }

    public AssociationInfoImpl(Association association, String minimizedCondition) {
        this(association, minimizedCondition, association.getRootNode().countArtifacts(), null);
    }

    /** With what the background refresh already computed: the artifact count and the full condition as shown. */
    public AssociationInfoImpl(Association association, String minimizedCondition, int numArtifacts, String fullCondition) {
        this.association = association;
        this.fullCondition = fullCondition;
        this.listeners = new LinkedList<>();
        this.selected = new SimpleBooleanProperty(false);
        this.selected.addListener((observable, oldValue, newValue) ->
                onPropertyChanged("selected", oldValue, newValue));
        this.numArtifacts = new SimpleIntegerProperty(numArtifacts);
        this.numArtifacts.addListener((observable, oldValue, newValue) ->
                onPropertyChanged("numArtifacts", oldValue, newValue));
        this.color = new SimpleObjectProperty<>(Color.TRANSPARENT);
        this.color.addListener((observable, oldValue, newValue) ->
                onPropertyChanged("color", oldValue, newValue));
        this.minimizedCondition = new SimpleStringProperty(minimizedCondition == null ? "" : minimizedCondition);
    }

    @Override
    public final Association getAssociation() {
        return this.association;
    }

    /** The full condition as shown - computed once, here if the refresh did not. */
    public String getFullCondition() {
        if (this.fullCondition == null)
            this.fullCondition = this.association.computeCondition().toDisplayString();
        return this.fullCondition;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        listeners.remove(listener);
    }

    @Override
    public Object getPropertyValue(String propertyName) {
        switch (propertyName) {
            case "color":
                return colorProperty().getValue();
            case "selected":
                return selectedProperty().getValue();
            case "numArtifacts":
                return numArtifactsProperty().getValue();
            default:
                return null;
        }
    }

    public boolean isSelected() {
        return this.selected.get();
    }

    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }

    public BooleanProperty selectedProperty() {
        return this.selected;
    }

    public ObjectProperty<Color> colorProperty() {
        return this.color;
    }

    public int getNumArtifacts() {
        return this.numArtifacts.get();
    }

    public void setNumArtifacts(int numArtifacts) {
        this.numArtifacts.set(numArtifacts);
    }

    public IntegerProperty numArtifactsProperty() {
        return this.numArtifacts;
    }

    public String getMinimizedCondition() {
        return this.minimizedCondition.get();
    }

    public void setMinimizedCondition(String minimizedCondition) {
        // the minimizer writes an empty condition as "false": shown, it read as never present
        this.minimizedCondition.set(minimizedCondition == null ? "" : minimizedCondition.trim().equals("false") ? Condition.NOT_TRACED : minimizedCondition);
    }

    public StringProperty minimizedConditionProperty() {
        return this.minimizedCondition;
    }

    private void onPropertyChanged(String propertyName, Object oldValue, Object newValue) {
        listeners.forEach(pcl -> pcl.propertyChange(
                new PropertyChangeEvent(this, propertyName, oldValue, newValue)));
    }
}
