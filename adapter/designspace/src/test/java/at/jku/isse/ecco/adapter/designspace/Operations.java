package at.jku.isse.ecco.adapter.designspace;

import at.jku.isse.designspace.sdk.core.operations.ElementCreate;
import at.jku.isse.designspace.sdk.core.operations.ElementDelete;
import at.jku.isse.designspace.sdk.core.operations.ElementUpdate;
import at.jku.isse.designspace.sdk.core.operations.Operation;

import static org.mockito.Mockito.*;

public class Operations {
    public Operation[] initTestOperations() {
        Operation[] operations = new Operation[100];

        for (int i = 0; i < operations.length; i++) {
            if (i % 3 == 0) {
                operations[i] = new ElementCreate(i, null, 0, "", null);
            } else if (i % 3 == 1) {
                operations[i] = new ElementUpdate(i, null, 0, "", null);
            } else {
                operations[i] = new ElementDelete(i, null, 0);
            }
        }

        return operations;
    }
}
