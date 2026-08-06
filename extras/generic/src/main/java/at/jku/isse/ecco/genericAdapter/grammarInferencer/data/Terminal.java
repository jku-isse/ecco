package at.jku.isse.ecco.genericAdapter.grammarInferencer.data;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Collections;
import java.util.List;

/**
 * Represents a terminal symbol
 *
 * @author Michael Jahn
 */
public class Terminal extends Symbol {

    private final String value;

    public Terminal(String name, String value) {
        super(name);
        this.value = value;
    }

    @Override
    public boolean isTerminal() {
        return true;
    }

    @Override
    public boolean isNonTerminal() {
        return false;
    }

    @Override
    public List<Terminal> getTerminalBeginningSymbols() {
        return Collections.singletonList(this);
    }

    @Override
    public List<String> getTerminalBeginningValues() {
        return Collections.singletonList(this.getValue());
    }

    @Override
    public Symbol getDeepCopyForSnapshot() {
        return new Terminal(this.getName(), this.getValue());
    }

    @Override
    public boolean isEndingSymbol(List<String> sampleSeperator) {
        for (String s : sampleSeperator) {
            if(s.equals(value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isOptionalSymbol() {
        return false;
    }

    @Override
    public String toString(){
        return name;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Terminal terminal = (Terminal) o;

        return new EqualsBuilder()
                .append(value, terminal.value)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(value)
                .toHashCode();
    }
}
