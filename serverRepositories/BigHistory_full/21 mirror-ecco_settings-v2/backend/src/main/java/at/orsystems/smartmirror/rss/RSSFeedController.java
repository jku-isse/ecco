package at.orsystems.smartmirror.rss;

import at.orsystems.smartmirror.rss.dto.RSSFeedDTO;
import at.orsystems.smartmirror.rss.dto.RSSFeedsDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotNull;

import static java.util.Objects.requireNonNull;

@RestController
public class RSSFeedController {
    @NotNull
    private final RSSFeedService service;

    public RSSFeedController(@NotNull final RSSFeedService service) {
        this.service = requireNonNull(service);
    }

    @GetMapping("/rss")
    public RSSFeedDTO getRSSFeedFor(@RequestParam("id") @NotNull final String id) {
        requireNonNull(id);
        return service.getRSSFeedFor(id);
    }

    @GetMapping("/rss/all")
    public RSSFeedsDTO getRSSFeeds() {
        return service.getAllRSSFeeds();
    }
}
