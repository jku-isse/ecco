package at.jku.isse.ecco.storage.neo4j.domain;

import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.session.Session;

import java.beans.Transient;

@NodeEntity
public abstract class NeoEntity {

    @Id @GeneratedValue
    private Long neoId;

    public Long getNeoId() {
        return neoId;
    }

    private Session neoSession;

    @Transient
    public Session getNeoSession() {
        return neoSession;
    }

    @Transient
    public void setNeoSession(Session neoSession) {
        this.neoSession = neoSession;
    }
}
