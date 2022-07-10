package at.orsystems.smartmirror.rss.dto;

import at.orsystems.smartmirror.rss.RSSFeed;

import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class RSSFeedsDTO {
    @NotNull
    private final Map<String, String> rssFeeds;

    public RSSFeedsDTO() {
        this(new LinkedHashMap<>());
    }

    public RSSFeedsDTO(@NotNull final Map<String, String> rssFeeds) {
        this.rssFeeds = new LinkedHashMap<>(requireNonNull(rssFeeds));
    }

    @NotNull
    public Map<String, String> getRssFeeds() {
        return Collections.unmodifiableMap(rssFeeds);
    }

    public void addRSSFeed(@NotNull final String id,
                           @NotNull final String rssFeedName) {
        requireNonNull(id);
        requireNonNull(rssFeedName);
        this.rssFeeds.put(id, rssFeedName);
    }

    public void addRSSFeed(@NotNull final RSSFeed rssFeed) {
        requireNonNull(rssFeed);
        addRSSFeed(rssFeed.id, rssFeed.title);
    }
}
