package at.orsystems.smartmirror.rss.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;

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
public final class RSSFeedDTO {
    private final @NotNull List<String> headLines;
    private final int amount;

    public RSSFeedDTO(@NotNull final List<String> headLines,
                      final int amount) {
        this.headLines = unmodifiableList(requireNonNull(headLines));
        this.amount = amount;
    }

    public @NotNull List<String> headLines() {
        return headLines;
    }

    public int amount() {
        return amount;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (RSSFeedDTO) obj;
        return Objects.equals(this.headLines, that.headLines) &&
                this.amount == that.amount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(headLines, amount);
    }

    @Override
    public String toString() {
        return "RSSFeedDTO[" +
                "headLines=" + headLines + ", " +
                "amount=" + amount + ']';
    }

}
