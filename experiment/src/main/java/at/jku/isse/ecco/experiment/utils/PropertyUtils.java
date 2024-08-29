package at.jku.isse.ecco.experiment.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class PropertyUtils {

    public static Properties loadProperties(String path){
        try {
            Properties props = new Properties();
            props.load(new FileInputStream(path));
            return props;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static int loadInteger(Properties properties, String propertyName){
        return Integer.parseInt(properties.getProperty(propertyName));
    }

    public static Path loadPath(Properties properties, String propertyName){
        return Paths.get(properties.getProperty(propertyName));
    }

    public static List<String> loadStringList(Properties properties, String propertyName){
        String stringArray = properties.getProperty(propertyName);
        return Arrays.stream(stringArray.split(","))
                .collect(Collectors.toList());
    }

    public static List<Integer> loadIntegerList(Properties properties, String propertyName){
        String intArrayString = properties.getProperty(propertyName);
        return Arrays.stream(intArrayString.split(","))
                .map(Integer::valueOf)
                .collect(Collectors.toList());
    }
}
