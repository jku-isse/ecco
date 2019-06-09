package at.jku.isse.ecco.plugin.emf.util;

import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.emf.ecore.xmi.impl.XMLParserPoolImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by hhoyos on 22/05/2017.
 */
public class EmfPluginUtils {

    private EmfPluginUtils() {

    }


    public static Map<Object, Object> getDefaultLoadOptions() {
        HashMap<Object, Object> loadOptions = new HashMap<Object, Object>();
        loadOptions.put(XMIResource.OPTION_DEFER_IDREF_RESOLUTION, Boolean.TRUE);
        loadOptions.put(XMIResource.OPTION_USE_PARSER_POOL, new XMLParserPoolImpl());
        return loadOptions;
    }

}
