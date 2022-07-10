package at.orsystems.smartmirror.config;

import org.springframework.boot.env.YamlPropertySourceLoader;

public class JSONPropertySourceLoader extends YamlPropertySourceLoader {
    @Override
    public String[] getFileExtensions() {
        return new String[]{"json"};
    }
}
