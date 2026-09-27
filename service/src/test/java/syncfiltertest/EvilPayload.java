package syncfiltertest;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

/**
 * Stand-in for a deserialization gadget: a serializable class outside ECCO's packages whose
 * readObject() has a side effect. Deliberately NOT under at.jku.isse.ecco, so the sync allow-list
 * must reject it before readObject() ever runs. See RemoteSyncServerHardeningTest.
 */
public class EvilPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    public static volatile boolean deserialized = false;

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        deserialized = true;
    }
}
