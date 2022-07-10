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

public class RSSFeed {
    public final String id;
    public final String title;
    public final String url;
    /* -1 for all*/
    public final int elements;

    @JsonIgnore
    private final Map<String, Object> additionalProperties = new HashMap<>();

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
