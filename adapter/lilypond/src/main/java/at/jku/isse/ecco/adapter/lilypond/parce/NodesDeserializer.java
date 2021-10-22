package at.jku.isse.ecco.adapter.lilypond.parce;

import at.jku.isse.ecco.adapter.lilypond.LilypondNode;
import at.jku.isse.ecco.adapter.lilypond.LilypondParser;
import at.jku.isse.ecco.adapter.lilypond.LilypondPlugin;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NodesDeserializer implements LilypondParser<ParceToken> {
    private final static Logger LOGGER = Logger.getLogger(LilypondPlugin.class.getName());
    @Override
    public void init() throws IOException {
    }

    @Override
    public LilypondNode<ParceToken> parse(Path path) {
        return parse(path, null);
    }

    @Override
    public LilypondNode<ParceToken> parse(Path path, HashMap<String, Integer> tokenMetric) {
        LOGGER.log(Level.INFO, "deserialize {0}", path);
        LilypondNode<ParceToken> head = new LilypondNode<>("ROOT", null);
        LilypondNode<ParceToken> n = head;
        try {
            try (FileInputStream fis = new FileInputStream(path.toFile());
                 ObjectInputStream ois = new ObjectInputStream(fis)) {
                int nNodes = ois.readInt();
                int cnt = 0;
                while (cnt < nNodes) {
                    LilypondNodeSerializationWrapper wn = (LilypondNodeSerializationWrapper)ois.readObject();
                    n.setNext(wn.getNode());
                    wn.getNode().setPrev(n);
                    n = wn.getNode();

                    if (null != tokenMetric && null != n.getData()) {
                        tokenMetric.put(n.getData().getAction(), tokenMetric.getOrDefault(n.getData().getAction(), 0) + 1);
                    }

                    cnt++;
                }
            }

        } catch (IOException | ClassNotFoundException ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        }

        return head.getNext();
    }

    @Override
    public void shutdown() {

    }
}
