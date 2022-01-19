package at.jku.isse.ecco.adapter.lilypond.parce.py4j;

import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;

import java.util.ArrayList;
import java.util.List;

public class ParseEvent {
    private int popContext;
    private final List<String> contexts;
    private final List<ParceToken> tokens;

    public int getPopContext() { return popContext; }
    public List<String> getContexts() { return contexts; }
    public List<ParceToken> getTokens() {
        return tokens;
    }

    public ParseEvent() {
        contexts = new ArrayList<>();
        tokens = new ArrayList<>();
    }

    public void setPopContext(int pop) {
        popContext = pop;
    }

    public void addContext(String context) {
        contexts.add(context);
    }

    public void addToken(ParceToken token) {
        tokens.add(token);
    }
}
