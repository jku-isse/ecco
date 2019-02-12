package at.jku.isse.ecco.storage.neo4j.domain;

import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;

@NodeEntity
public abstract class NeoEntity {

    @Id
    @GeneratedValue
    private long neoId;

    public Long getNeoId() {
        return neoId;
    }
}
