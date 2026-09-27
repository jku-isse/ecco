package at.jku.isse.ecco.adapter.typescript;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.typescript.data.*;
import at.jku.isse.ecco.adapter.view.RenderedSource;
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
            String text = render(fileNode).getText();
            PluginArtifactData rootData = (PluginArtifactData) fileNode.getArtifact().getData();
            // the file's full relative path - resolving just getFileName() used to put every file at
            // the checkout root, whatever folder it belonged to (see TypeScriptWriterTest)
            Path outputFile = base.resolve(rootData.getPath());
            try {
                if (outputFile.getParent() != null)
                    Files.createDirectories(outputFile.getParent());
                try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
                    writer.write(text);
                }
            } catch (IOException x) {
                throw new EccoException("Could not write file: " + outputFile, x);
            }
            output.add(outputFile);
        }
        return output.toArray(new Path[0]);
    }

    /**
     * The file's text as it is written, with the part each node produced - also what the association
     * preview shows (TypeScriptViewer).
     */
    public static RenderedSource render(Node fileNode) {
        StringBuilder sb = new StringBuilder();
        List<RenderedSource.Span> spans = new ArrayList<>();
        for (Node lineNode : fileNode.getChildren()) {
            writeNodes(sb, lineNode, spans);
        }
        // a span recorded before a separator was trimmed (setLength below) may reach past the end
        List<RenderedSource.Span> clamped = new ArrayList<>(spans.size());
        for (RenderedSource.Span span : spans) {
            int end = Math.min(span.end(), sb.length());
            clamped.add(new RenderedSource.Span(span.node(), Math.min(span.start(), end), end));
        }
        return new RenderedSource(sb.toString(), clamped);
    }

    private static void writeNodes(StringBuilder sb, Node node, List<RenderedSource.Span> spans) {
        int start = sb.length();
        writeNode(sb, node, spans);
        spans.add(new RenderedSource.Span(node, start, sb.length()));
    }

    private static void writeNode(StringBuilder sb, Node node, List<RenderedSource.Span> spans) {
        AbstractArtifactData data = (AbstractArtifactData) node.getArtifact().getData();
        sb.append(data.getLeadingComment());
        if (Objects.requireNonNull(data) instanceof BlockArtifactData b) {
            sb.append(b);
            node.getChildren().forEach(x -> writeNodes(sb, x, spans));
            sb.append(b.getTrailingComment());
        } else if (data instanceof ArrowFunctionArtifactData a) {
            sb.append(a);
            node.getChildren().forEach(x -> writeNodes(sb, x, spans));
        } else if (data instanceof VariableAssignmentData v) {
            sb.append(v);
            sb.append(" ");
            node.getChildren().forEach(x -> {
                writeNodes(sb, x, spans);
                sb.append(",");
            });
            sb.setLength(sb.length() - 1);
            sb.append(v.getTrailingComment());
        } else if (data instanceof EnumArtifactData e) {
            sb.append(e);
            node.getChildren().forEach(x -> {
                writeNodes(sb, x, spans);
                sb.append(",");
            });
            sb.setLength(sb.length() - 1);
            sb.append(e.getTrailingComment());
        } else if (data instanceof SwitchBlockArtifactData sw) {
            sb.append(sw);
            node.getChildren().forEach(x -> writeNodes(sb, x, spans));
            sb.append(sw.getTrailingComment());
        } else if (data instanceof IfBlockArtifactData i) {
            sb.append(i.getBlock());
            writeNodes(sb, node.getChildren().get(0), spans);
            if (node.getChildren().size() > 1) {
                sb.append(" else");
                writeNodes(sb, node.getChildren().get(1), spans);
            }
        } else if (data instanceof LoopArtifactData l) {
            sb.append(l);
            node.getChildren().forEach(x -> writeNodes(sb, x, spans));
        } else if (data instanceof LeafArtifactData l) {
            sb.append(l.getLine());
        } else if (data instanceof ClassArtifactData c) {
            sb.append(c.getClassDecl());
            node.getChildren().forEach(x -> writeNodes(sb, x, spans));
            sb.append("\n}");
        } else if (data instanceof DoBlockArtifactData d) {
            sb.append(d.getLeadingText());
            node.getChildren().forEach(x -> writeNodes(sb, x, spans));
            sb.append(d.getTrailingComment());
        } else if (data instanceof FunctionArtifactData f) {
            sb.append(f.getSignature());
            node.getChildren().forEach(x -> writeNodes(sb, x, spans));
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
