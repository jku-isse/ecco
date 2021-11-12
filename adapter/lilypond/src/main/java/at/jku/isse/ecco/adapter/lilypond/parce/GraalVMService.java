package at.jku.isse.ecco.adapter.lilypond.parce;

import org.graalvm.polyglot.HostAccess;

import java.util.LinkedList;
import java.util.List;

public class GraalVMService {
    private ParseEvent event;
    private ParceToken lastToken;
    private final List<ParseEvent> events = new LinkedList<>();

    @HostAccess.Export
    public void addContext(String name) {
        if (event == null) {
            event = new ParseEvent();
            events.add(event);
        }
        event.addContext(name);
    }

    @HostAccess.Export
    public void addToken(int pos, String text, String action, String precedingWhitespace) {
        if (lastToken != null) {
            lastToken.setPostWhitespace(precedingWhitespace);
        }

        ParceToken t = new ParceToken(pos, text, action);
        event.addToken(t);
        lastToken = t;
    }

    @HostAccess.Export
    public void closeEvent(int popContext) {
        event.setPopContext(popContext);
    }

    public List<ParseEvent> getEvents() {
        return events;
    }

    static class ParseEvent {
        private int popContext;
        private final List<String> contexts;
        private final List<ParceToken> tokens;

        public int getPopContext() { return popContext; }
        public List<String> getContexts() { return contexts; }
        public List<ParceToken> getTokens() {
            return tokens;
        }

        public ParseEvent() {
            contexts = new LinkedList<>();
            tokens = new LinkedList<>();
        }

        public void addContext(String name) {
            contexts.add(name);
        }

        public void addToken(ParceToken token) {
            tokens.add(token);
        }

        public void setPopContext(int pop) {
            popContext = pop;
        }
    }
}
