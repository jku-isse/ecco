package at.orsystems.smartmirror.startup;

import at.orsystems.smartmirror.rss.RSSFeedProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        OpenWeatherMapProperties.class,
        SmartMirrorProperties.class,
        RSSFeedProperties.class
})
public class SmartMirrorConfiguration {
}
