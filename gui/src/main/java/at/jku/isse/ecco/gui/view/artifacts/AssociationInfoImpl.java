package at.jku.isse.ecco.gui.view.artifacts;

import at.jku.isse.ecco.adapter.AssociationInfo;
import at.jku.isse.ecco.core.Association;
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

    private final LinkedList<PropertyChangeListener> listeners;

    public AssociationInfoImpl(Association association) {
        this.association = association;
        this.listeners = new LinkedList<>();
        this.selected = new SimpleBooleanProperty(false);
        this.selected.addListener((observable, oldValue, newValue) ->
                onPropertyChanged("selected", oldValue, newValue));
        this.numArtifacts = new SimpleIntegerProperty(association.getRootNode().countArtifacts());
        this.numArtifacts.addListener((observable, oldValue, newValue) ->
                onPropertyChanged("numArtifacts", oldValue, newValue));
        this.color = new SimpleObjectProperty<>(Color.TRANSPARENT);
        this.color.addListener((observable, oldValue, newValue) ->
                onPropertyChanged("color", oldValue, newValue));
    }

    @Override
    public final Association getAssociation() {
        return this.association;
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

    private void onPropertyChanged(String propertyName, Object oldValue, Object newValue) {
        listeners.forEach(pcl -> pcl.propertyChange(
                new PropertyChangeEvent(this, propertyName, oldValue, newValue)));
    }
}
