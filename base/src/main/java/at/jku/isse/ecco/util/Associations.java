package at.jku.isse.ecco.util;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.module.PresenceCondition;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Associations {

	private Associations() {
	}


	public static void consolidate(Collection<? extends Association> associations) {
		Map<PresenceCondition, Association> pcToAssocMap = new HashMap<>();
		Association emptyAssoc = null;

		Iterator<? extends Association> it = associations.iterator();
		while (it.hasNext()) {
			Association association = it.next();

			// if association contains no artifacts it requires special treatment here
			if (association.getRootNode().getChildren().size() == 0) {
				emptyAssoc = association;
			} else {
				Association equalAssoc = pcToAssocMap.get(association.getPresenceCondition());
				if (equalAssoc == null) {
					pcToAssocMap.put(association.getPresenceCondition(), association);
				} else {
					Trees.merge(equalAssoc.getRootNode(), association.getRootNode());
					it.remove();
				}
			}
		}

		if (emptyAssoc != null) {
			for (Association assoc : associations) {
				if (assoc != emptyAssoc) {
					emptyAssoc.getPresenceCondition().getMinModules().removeAll(assoc.getPresenceCondition().getMinModules());
				}
			}
		}

	}

}
