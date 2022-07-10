package at.orsystems.smartmirror.config;

import org.springframework.boot.env.YamlPropertySourceLoader;

/**
 * Defines JSON as additional application properties type.
 *
 * @author Michael Ratzenböck
 * @since 2021
 */
public class JSONPropertySourceLoader extends YamlPropertySourceLoader {
    @Override
    public String[] getFileExtensions() {
        return new String[]{"json"};
    }
}
