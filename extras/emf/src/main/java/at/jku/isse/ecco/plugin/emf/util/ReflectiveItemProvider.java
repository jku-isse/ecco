package at.jku.isse.ecco.plugin.emf.util;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.FeatureMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by hhoyos on 29/05/2017.
 */
public class ReflectiveItemProvider {

    public static String capName(String name) {
        return name.length() == 0 ? name : name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    public static EStructuralFeature getLabelFeature(EClass eClass) {
        EAttribute result = eClass.getEIDAttribute();
        if (result == null) {
            for (EAttribute eAttribute : eClass.getEAllAttributes()) {
                if (!eAttribute.isMany() && eAttribute.getEType().getInstanceClass() != FeatureMap.Entry.class) {
                    if ("name".equalsIgnoreCase(eAttribute.getName())) {
                        result = eAttribute;
                        break;
                    } else if (result == null) {
                        result = eAttribute;
                    } else if (eAttribute.getEAttributeType().getInstanceClass() == String.class &&
                            result.getEAttributeType().getInstanceClass() != String.class) {
                        result = eAttribute;
                    }
                }
            }
        }
        return result;
    }

    public static List<String> parseName(String sourceName, char sourceSeparator) {
        List<String> result = new ArrayList<String>();
        StringBuffer currentWord = new StringBuffer();

        int length = sourceName.length();
        boolean lastIsLower = false;

        for (int index = 0; index < length; index++) {
            char curChar = sourceName.charAt(index);
            if (Character.isUpperCase(curChar) || (!lastIsLower && Character.isDigit(curChar)) || curChar == sourceSeparator) {
                if (lastIsLower || curChar == sourceSeparator) {
                    result.add(currentWord.toString());
                    currentWord = new StringBuffer();
                }
                lastIsLower = false;
            }
            else {
                if (!lastIsLower) {
                    int currentWordLength = currentWord.length();
                    if (currentWordLength > 1) {
                        char lastChar = currentWord.charAt(--currentWordLength);
                        currentWord.setLength(currentWordLength);
                        result.add(currentWord.toString());
                        currentWord = new StringBuffer();
                        currentWord.append(lastChar);
                    }
                }
                lastIsLower = true;
            }
            if (curChar != sourceSeparator) {
                currentWord.append(curChar);
            }
        }
        result.add(currentWord.toString());
        return result;
    }

    public static String format(String name, char separator) {
        StringBuffer result = new StringBuffer();

        for (Iterator<String> i = parseName(name, '_').iterator(); i.hasNext(); ) {
            String component = i.next();
            result.append(component);
            if (i.hasNext() && component.length() > 1) {
                result.append(separator);
            }
        }
        return result.toString();
    }

}
