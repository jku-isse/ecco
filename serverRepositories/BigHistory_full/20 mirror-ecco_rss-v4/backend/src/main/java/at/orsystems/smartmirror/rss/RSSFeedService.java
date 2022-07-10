package at.orsystems.smartmirror.rss;

import at.orsystems.smartmirror.rss.dto.RSSFeedDTO;
import at.orsystems.smartmirror.rss.dto.RSSFeedsDTO;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.constraints.NotNull;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

@Service
public class RSSFeedService {
    @NotNull
    private final RestTemplate restTemplate;
    @NotNull
    private final RSSFeedProperties properties;

    public RSSFeedService(@NotNull final RestTemplateBuilder restTemplateBuilder,
                          @NotNull final RSSFeedProperties properties) {
        requireNonNull(restTemplateBuilder);

        this.properties = requireNonNull(properties);
        this.restTemplate = restTemplateBuilder.build();
    }

    @NotNull
    public RSSFeedDTO getRSSFeedFor(@NotNull final String id) {
        requireNonNull(id);
        final var rssFeed = findRSSFeedFor(id);

        var titles = readRSSFeedTitles(rssFeed.url);

        if (rssFeed.elements >= 0) {
            titles = titles.subList(0, rssFeed.elements);
        }

        return new RSSFeedDTO(titles, titles.size());
    }

    private RSSFeed findRSSFeedFor(@NotNull final String id) {
        return properties.getRssFeeds()
                .stream()
                .filter(rssFeed -> rssFeed.id.equals(id))
                .findFirst()
                .orElseThrow(() -> {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, String.format("The id [%s] is unknown", id));
                });
    }

    @NotNull
    private List<String> readRSSFeedTitles(@NotNull final String url) {
        requireNonNull(url);

        SyndFeed syndFeed = restTemplate.execute(url, HttpMethod.GET, null, response -> {
            SyndFeedInput input = new SyndFeedInput();
            try {
                return input.build(new XmlReader(response.getBody()));
            } catch (FeedException e) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE
                        , "Could not read RSS Feed because of: " + e.getMessage()
                        , e);
            }
        });

        if (syndFeed == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    String.format("Something during reading the RSS Feed from %s went wrong...", url));
        }

        return syndFeed.getEntries()
                .stream()
                .map(SyndEntry::getTitle)
                .collect(toList());
    }

    @NotNull
    public RSSFeedsDTO getAllRSSFeeds() {
        final var ret = new RSSFeedsDTO();

        properties.feeds.forEach(ret::addRSSFeed);

        return ret;
    }
}
