package at.orsystems.smartmirror.rss;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

/**
 * Represents the "rss" properties object from the application properties file.
 *
 * @author Michael Ratzenböck
 * @since 2021
 */
@Validated
@ConstructorBinding
@ConfigurationProperties(prefix = "rss")
public class RSSFeedProperties {
    /**
     * Contains all RSS-Feed entries from the application properties file.
     */
    @NotEmpty
    @NotNull
    private final List<RSSFeed> feeds;

    public RSSFeedProperties(@NotNull final List<RSSFeed> feeds) {
        this.feeds = requireNonNull(feeds);
    }

    /**
     * Returns the configured RSS-Feeds.
     */
    @NotNull
    public List<RSSFeed> getRssFeeds() {
        return unmodifiableList(feeds);
    }
}
