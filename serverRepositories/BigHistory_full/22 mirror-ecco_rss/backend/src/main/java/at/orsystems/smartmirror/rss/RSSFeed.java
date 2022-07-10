package at.orsystems.smartmirror.rss;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * This is the class that represents a single RSS-Feed entry in the application properties file. Hence, it is used by
 * Spring for automatic property mapping.
 *
 * @author Michael Ratzenböck
 * @since 2021
 */
public class RSSFeed {
    /**
     * This is the id of the RSS-Feed. It has to be unique among all other ids. It is also needed to query the RSS-Feed
     * entries from the endpoint.
     */
    public final String id;
    /**
     * The title of the RSS-Feed which is mainly used by the frontend.
     */
    public final String title;
    /**
     * The source URL of the RSS-Feed.
     */
    public final String url;
    /**
     * Defines the number of elements of the RSS-Feed the backend sends to the frontend. If the this value is 0, then
     * the backend will return an empty list. If the value is < 0 than the backend will return all elements returned by
     * the source URL.
     * <p>
     * If the given number is greater than the amount of entries provided by the RSS-Feed, the amount of available
     * entries will be returned.
     */
    public final int elements;

    @JsonIgnore
    private final Map<String, Object> additionalProperties = new HashMap<>();

    /**
     * @param id
     *         the unique id of the RSS-Feed
     * @param title
     *         the title that should be displayed along the entries
     * @param url
     *         the source of the RSS-Feed
     * @param elements
     *         limits the amount of returned entries.
     */
    @JsonCreator
    public RSSFeed(@NotNull final String id,
                   @NotNull final String title,
                   @NotNull final String url,
                   final int elements) {
        this.id = requireNonNull(id);
        this.title = requireNonNull(title);
        this.url = requireNonNull(url);
        this.elements = elements;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        requireNonNull(name);
        requireNonNull(value);
        this.additionalProperties.put(name, value);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof RSSFeed rhs)) {
            return false;
        }

        return new EqualsBuilder()
                .append(id, rhs.id)
                .append(title, rhs.title)
                .append(url, rhs.url)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(id)
                .append(title)
                .append(url)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("id", this.id)
                .append("title", this.title)
                .append("url", this.url)
                .toString();
    }
}
