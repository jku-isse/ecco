package at.orsystems.smartmirror.rss.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import javax.validation.constraints.NotNull;
import java.util.List;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

/**
 * This is the object that gets transferred to the frontend, which contains the actual RSS-Feed title entries and the
 * number of those entries.
 *
 * @author Michael Ratzenböck
 * @since 2021
 */
/* Workaround because Jackson does not support records yet. */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record RSSFeedDTO(@NotNull List<String> headLines, int amount) {
    public RSSFeedDTO(@NotNull final List<String> headLines,
                      final int amount) {
        this.headLines = unmodifiableList(requireNonNull(headLines));
        this.amount = amount;
    }
}
