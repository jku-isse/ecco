package at.jku.isse.ecco.adapter.typescript;

import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.typescript.data.*;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;


public class TypeScriptWriter implements ArtifactWriter<Set<Node>, Path> {

    private static final Logger LOGGER = Logger.getLogger(TypeScriptWriter.class.getName());

    @Override
    public String getPluginId() {
        return TypeScriptPlugin.class.getName();
    }

    @Override
    public Path[] write(Set<Node> input) {
        return new Path[0];
    }


    @Override
    public Path[] write(Path base, Set<Node> input) {

        List<Path> output = new ArrayList<>();

        for (Node fileNode : input) {
            StringBuilder sb = new StringBuilder();
            PluginArtifactData rootData = (PluginArtifactData) fileNode.getArtifact().getData();
            for (Node lineNode : fileNode.getChildren()) {
                writeNodes(sb, lineNode);
            }
            try (BufferedWriter writer = Files.newBufferedWriter(base.resolve(((PluginArtifactData) fileNode.getArtifact().getData()).getFileName()), StandardCharsets.UTF_8)) {
                writer.write(sb.toString());
            } catch (IOException x) {
                LOGGER.severe("IOException: " + x);
            }
            output.add(base.resolve(rootData.getPath()));
        }
        return output.toArray(new Path[0]);
    }

    private void writeNodes(StringBuilder sb, Node node) {
        AbstractArtifactData data = (AbstractArtifactData) node.getArtifact().getData();
        sb.append(data.getLeadingComment());
        if (Objects.requireNonNull(data) instanceof BlockArtifactData b) {
            sb.append(b);
            node.getChildren().forEach(x -> writeNodes(sb, x));
            sb.append(b.getTrailingComment());
        } else if (data instanceof ArrowFunctionArtifactData a) {
            sb.append(a);
            node.getChildren().forEach(x -> writeNodes(sb, x));
        } else if (data instanceof VariableAssignmentData v) {
            sb.append(v);
            sb.append(" ");
            node.getChildren().forEach(x -> {
                writeNodes(sb, x);
                sb.append(",");
            });
            sb.setLength(sb.length() - 1);
            sb.append(v.getTrailingComment());
        } else if (data instanceof EnumArtifactData e) {
            sb.append(e);
            node.getChildren().forEach(x -> {
                writeNodes(sb, x);
                sb.append(",");
            });
            sb.setLength(sb.length() - 1);
            sb.append(e.getTrailingComment());
        } else if (data instanceof SwitchBlockArtifactData sw) {
            sb.append(sw);
            node.getChildren().forEach(x -> writeNodes(sb, x));
            sb.append(sw.getTrailingComment());
        } else if (data instanceof IfBlockArtifactData i) {
            sb.append(i.getBlock());
            writeNodes(sb, node.getChildren().get(0));
            if (node.getChildren().size() > 1) {
                sb.append(" else");
                writeNodes(sb, node.getChildren().get(1));
            }
        } else if (data instanceof LoopArtifactData l) {
            sb.append(l);
            node.getChildren().forEach(x -> writeNodes(sb, x));
        } else if (data instanceof LeafArtifactData l) {
            sb.append(l.getLine());
        } else if (data instanceof ClassArtifactData c) {
            sb.append(c.getClassDecl());
            node.getChildren().forEach(x -> writeNodes(sb, x));
            sb.append("\n}");
        } else if (data instanceof DoBlockArtifactData d) {
            sb.append(d.getLeadingText());
            node.getChildren().forEach(x -> writeNodes(sb, x));
            sb.append(d.getTrailingComment());
        } else if (data instanceof FunctionArtifactData f) {
            sb.append(f.getSignature());
            node.getChildren().forEach(x -> writeNodes(sb, x));
        } else {
            throw new IllegalStateException("Unexpected value: " + node.getArtifact().getData().getClass());
        }
    }


    private Collection<WriteListener> listeners = new ArrayList<>();

    @Override
    public void addListener(WriteListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(WriteListener listener) {
        this.listeners.remove(listener);
    }

}
