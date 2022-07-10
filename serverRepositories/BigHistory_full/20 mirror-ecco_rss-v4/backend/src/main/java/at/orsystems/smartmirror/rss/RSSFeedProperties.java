package at.orsystems.smartmirror.rss;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

@Validated
@ConstructorBinding
@ConfigurationProperties(prefix = "rss")
public class RSSFeedProperties {
    @NotEmpty
    @NotNull
    public final List<RSSFeed> feeds;

    public RSSFeedProperties(@NotNull final List<RSSFeed> feeds) {
        this.feeds = requireNonNull(feeds);
    }

    @NotNull
    public List<RSSFeed> getRssFeeds() {
        return unmodifiableList(feeds);
    }
}
