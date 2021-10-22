package at.jku.isse.ecco.adapter.lilypond.parce;

import at.jku.isse.ecco.adapter.lilypond.LilypondNode;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class LilypondNodeSerializationWrapper implements Serializable {
    private static final long serialVersionUID = 1L;
    private transient LilypondNode<ParceToken> node;

    public LilypondNodeSerializationWrapper(LilypondNode<ParceToken> node) {
        this.node = node;
    }

    public LilypondNode<ParceToken> getNode() {
        return node;
    }

    private void writeObject(ObjectOutputStream oos)
            throws IOException {
        oos.defaultWriteObject();
        oos.writeUTF(node.getName());
        oos.writeInt(node.getLevel());
        oos.writeBoolean(node.getData() != null);
        if (node.getData() != null) {
            oos.writeInt(node.getData().getPos());
            oos.writeUTF(node.getData().getAction());
            oos.writeUTF(node.getData().getText());
            oos.writeUTF(node.getData().getPostWhitespace());
        }
    }

    private void readObject(ObjectInputStream ois)
            throws ClassNotFoundException, IOException {
        ois.defaultReadObject();
        String name = ois.readUTF();
        int level = ois.readInt();
        boolean hasData = ois.readBoolean();
        ParceToken t = null;
        if (hasData) {
            int pos = ois.readInt();
            String action = ois.readUTF();
            String text = ois.readUTF();
            String whitespace = ois.readUTF();
            t = new ParceToken(pos, text, action);
            t.setPostWhitespace(whitespace);
        }
        node = new LilypondNode<>(name, t);
        node.setLevel(level);
    }
}
