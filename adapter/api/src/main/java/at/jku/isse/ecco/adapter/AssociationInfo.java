package at.jku.isse.ecco.adapter;

import at.jku.isse.ecco.core.Association;

import java.beans.PropertyChangeListener;

public interface AssociationInfo {

    Association getAssociation();

    void addPropertyChangeListener(PropertyChangeListener listener);

    void removePropertyChangeListener(PropertyChangeListener listener);

    Object getPropertyValue(String propertyName);
}
