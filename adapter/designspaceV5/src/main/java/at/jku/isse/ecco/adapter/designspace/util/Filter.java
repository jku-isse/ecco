package at.jku.isse.ecco.adapter.designspace.util;

import at.jku.isse.designspace.core.model.WorkspacePropertyType;

public class Filter {



    public static boolean shouldProcessProperty(WorkspacePropertyType propType) {
        String name = propType.getQualifiedName();

        if (name.endsWith("::owner") || name.endsWith("::try") ) {
            return false;
        }
        if (name.contains("@") || name.endsWith("::pre")) {
            return false;
        }
        return !name.endsWith("::importUsages") &&
                !name.endsWith("::typeUsages") &&
                !name.endsWith("::subClasses") &&
                !name.endsWith("::implementations") &&
                !name.endsWith("::fieldTypes") &&
                !name.endsWith("::methodReturnTypes") &&
                !name.endsWith("::callers") &&
                !name.endsWith("::callees") &&
                !name.endsWith("::accesses") &&
                !name.endsWith("::accessedBy") &&
                !name.endsWith("::uses") &&
                !name.endsWith("::methodUsages") &&
                !name.endsWith("::classReference") &&
                !name.endsWith("::packageReference") &&
                !name.endsWith("::classReferences") &&
                !name.endsWith("::parameterTypes");
    }


}
