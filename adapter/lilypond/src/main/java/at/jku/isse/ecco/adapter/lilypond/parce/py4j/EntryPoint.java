package at.jku.isse.ecco.adapter.lilypond.parce.py4j;

import at.jku.isse.ecco.adapter.lilypond.LilypondPlugin;
import at.jku.isse.ecco.adapter.lilypond.LilypondReader;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

public class EntryPoint {
    private static final Logger LOGGER = Logger.getLogger(LilypondPlugin.class.getName());

    private ConcurrentLinkedQueue<ParseEvent> eventBuffer;
    private ParceToken lastToken;

    public EntryPoint() {
        eventBuffer = new ConcurrentLinkedQueue<>();
    }

    private ParseEvent event;

    public void openEvent() {
        event = new ParseEvent();
    }

    public void addContext(String name) {
        assert event != null;

        event.addContext(name);
    }

    public void addToken(int pos, String text, String action) {
        assert event != null;

        ParceToken lpt = new ParceToken(pos, text, action);
        event.addToken(lpt);
        lastToken = lpt;
    }

    public void addWhitespace(int pos, String text) {
        if (null == text) { return; }

        boolean isEventClosed = event == null;
        if (isEventClosed) {
            openEvent();
        }

        String[] lines = text.split("\\n", -1);
        int i = 1;
        while (i < lines.length) {
            pos += lines[i-1].length();
            event.addToken(new ParceToken(pos, lines[i], LilypondReader.PARSER_ACTION_LINEBREAK));
            i++;
        }

        if (isEventClosed) { closeEvent(0); };
    }

    public void closeEvent(int popContext) {
        assert event != null;

        event.setPopContext(popContext);
        if (!eventBuffer.offer(event)) {
            LOGGER.severe("could not add parse event to buffer");
        }
        event = null;
    }

    ConcurrentLinkedQueue<ParseEvent> getBuffer() {
        return eventBuffer;
    }
}
