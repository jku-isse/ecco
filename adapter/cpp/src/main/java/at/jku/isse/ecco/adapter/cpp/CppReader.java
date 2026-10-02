package at.jku.isse.ecco.adapter.cpp;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.cpp.data.ScopeArtifactData;
import at.jku.isse.ecco.adapter.cpp.data.SourceLineArtifactData;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.dispatch.TextFileFormat;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.featuretrace.parser.VevosCondition;
import at.jku.isse.ecco.featuretrace.parser.VevosConditionHandler;
import at.jku.isse.ecco.featuretrace.parser.VevosFileConditionContainer;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Location;
import com.google.inject.Inject;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTLinkageSpecification;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTNamespaceDefinition;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTTemplateDeclaration;
import org.eclipse.cdt.core.dom.ast.gnu.cpp.GPPLanguage;
import org.eclipse.cdt.core.model.ILanguage;
import org.eclipse.cdt.core.parser.DefaultLogService;
import org.eclipse.cdt.core.parser.FileContent;
import org.eclipse.cdt.core.parser.IncludeFileContentProvider;
import org.eclipse.cdt.core.parser.ScannerInfo;
import org.eclipse.core.runtime.CoreException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Reads C++ files so that checkout reproduces them byte for byte: every line of a file is an artifact
 * of its own - code, comments, preprocessor directives, and anything the parser does not understand.
 * The CDT parser only groups the lines: a namespace, class/struct/union, enum, extern "C" block or
 * function becomes a {@link ScopeArtifactData} node holding the lines it spans (and the scopes nested
 * in it), so variants are aligned scope by scope. #if/#ifdef directives are kept as lines, like in
 * the C adapter; lines can carry VEVOS presence conditions (pcs.variant.csv) as proactive feature
 * traces.
 */
public class CppReader implements ArtifactReader<Path, Set<Node.Op>> {

    // *.c and *.h one below the top, so they go to the C adapter when both are enabled
    private static final Map<Integer, String[]> prioritizedPatterns = Map.of(
            Integer.MAX_VALUE, new String[]{"**.cpp", "**.hpp"},
            Integer.MAX_VALUE - 1, new String[]{"**.c", "**.h"});

    private final EntityFactory entityFactory;
    private final Collection<ReadListener> listeners = new ArrayList<>();

    @Inject
    public CppReader(EntityFactory entityFactory) {
        this.entityFactory = checkNotNull(entityFactory);
    }

    @Override
    public String getPluginId() {
        return CppPlugin.class.getName();
    }

    @Override
    public Map<Integer, String[]> getPrioritizedPatterns() {
        return prioritizedPatterns;
    }

    @Override
    public Set<Node.Op> read(Path[] input) {
        return this.read(Paths.get("."), input);
    }

    @Override
    public Set<Node.Op> read(Path base, Path[] input) {
        VevosConditionHandler vevosConditionHandler = new VevosConditionHandler(base);
        String configuration = getConfigurationString(base);
        Set<Node.Op> nodes = new HashSet<>();
        for (Path path : input) {
            Path absolutePath = base.resolve(path);
            TextFileFormat.Decoded decoded;
            try {
                // charset/line separator/final newline are recorded so checkout reproduces the file byte
                // for byte - see TextFileFormat
                decoded = TextFileFormat.read(absolutePath);
            } catch (IOException e) {
                // fail the commit rather than committing an empty file (see UnreadableFileCommitTest)
                throw new EccoException("Could not read file: " + absolutePath, e);
            }
            PluginArtifactData pluginArtifactData = new PluginArtifactData(this.getPluginId(), path);
            decoded.recordOn(pluginArtifactData);
            Node.Op pluginNode = this.entityFactory.createOrderedNode(this.entityFactory.createArtifact(pluginArtifactData));

            String[] lines = decoded.lines().toArray(new String[0]);
            List<Scope> scopes = parseScopes(absolutePath, lines);
            new TreeBuilder(lines, vevosConditionHandler.getFileSpecificPresenceConditions(path), path, configuration)
                    .addLines(pluginNode, scopes, 1, lines.length);
            nodes.add(pluginNode);
        }
        return nodes;
    }

    /** A scope and the lines it spans (1-based, inclusive), with the scopes nested in it. */
    record Scope(ScopeArtifactData.Kind kind, String signature, int startLine, int endLine, List<Scope> children) {
    }

    /**
     * The scopes of a file, outermost first, in the order of their lines. The parser sees the lines
     * joined with \n, so its line numbers are the lines' whatever the file's separator. Includes are
     * not resolved and no macros are defined; declarations in inactive #if branches are parsed too.
     */
    static List<Scope> parseScopes(Path path, String[] lines) {
        FileContent content = FileContent.create(path.toString(), String.join("\n", lines).toCharArray());
        IASTTranslationUnit translationUnit;
        try {
            translationUnit = GPPLanguage.getDefault().getASTTranslationUnit(content, new ScannerInfo(),
                    IncludeFileContentProvider.getEmptyFilesProvider(), null,
                    ILanguage.OPTION_PARSE_INACTIVE_CODE | ILanguage.OPTION_IS_SOURCE_UNIT, new DefaultLogService());
        } catch (CoreException e) {
            throw new EccoException("Could not parse file: " + path, e);
        }
        return scopesOf(translationUnit.getDeclarations(true), 1, lines.length);
    }

    /**
     * The scopes among {@code declarations} that lie within lines {@code from}..{@code to}: whole
     * lines only - a scope starting on a line an earlier sibling ends on is left to that sibling.
     */
    private static List<Scope> scopesOf(IASTDeclaration[] declarations, int from, int to) {
        List<Scope> candidates = new ArrayList<>();
        for (IASTDeclaration declaration : declarations) {
            Scope scope = scopeOf(declaration);
            if (scope != null && scope.startLine() >= from && scope.endLine() <= to)
                candidates.add(scope);
        }
        candidates.sort(Comparator.comparingInt(Scope::startLine));
        List<Scope> scopes = new ArrayList<>();
        int lastEnd = from - 1;
        for (Scope scope : candidates) {
            if (scope.startLine() > lastEnd) {
                scopes.add(scope);
                lastEnd = scope.endLine();
            }
        }
        return scopes;
    }

    private static Scope scopeOf(IASTDeclaration declaration) {
        IASTFileLocation location = declaration.getFileLocation();
        if (location == null)
            return null;
        int start = location.getStartingLineNumber();
        int end = location.getEndingLineNumber();
        if (declaration instanceof ICPPASTNamespaceDefinition namespace) {
            return new Scope(ScopeArtifactData.Kind.NAMESPACE, normalize(namespace.getName().toString()), start, end,
                    scopesOf(namespace.getDeclarations(true), start, end));
        } else if (declaration instanceof ICPPASTLinkageSpecification linkage) {
            return new Scope(ScopeArtifactData.Kind.LINKAGE, normalize(linkage.getLiteral()), start, end,
                    scopesOf(linkage.getDeclarations(true), start, end));
        } else if (declaration instanceof ICPPASTTemplateDeclaration template) {
            Scope inner = scopeOf(template.getDeclaration());
            // the scope includes the template<...> line(s)
            return inner == null ? null : new Scope(inner.kind(), "template " + inner.signature(), start, end, inner.children());
        } else if (declaration instanceof IASTFunctionDefinition function) {
            String signature = function.getDeclSpecifier().getRawSignature() + " " + function.getDeclarator().getRawSignature();
            return new Scope(ScopeArtifactData.Kind.FUNCTION, normalize(signature), start, end, List.of());
        } else if (declaration instanceof IASTSimpleDeclaration simple) {
            IASTDeclSpecifier specifier = simple.getDeclSpecifier();
            if (specifier instanceof IASTCompositeTypeSpecifier composite) {
                String key = switch (composite.getKey()) {
                    case IASTCompositeTypeSpecifier.k_struct -> "struct";
                    case IASTCompositeTypeSpecifier.k_union -> "union";
                    default -> "class";
                };
                return new Scope(ScopeArtifactData.Kind.CLASS, normalize(key + " " + composite.getName()), start, end,
                        scopesOf(composite.getDeclarations(true), start, end));
            } else if (specifier instanceof IASTEnumerationSpecifier enumeration) {
                return new Scope(ScopeArtifactData.Kind.ENUM, normalize(enumeration.getName().toString()), start, end, List.of());
            }
        }
        return null;
    }

    private static String normalize(String signature) {
        return signature.replaceAll("\\s+", " ").trim();
    }

    /** Adds every line to the innermost scope that spans it. */
    private final class TreeBuilder {
        private final String[] lines;
        private final VevosFileConditionContainer fileConditionContainer;
        private final Path path;
        private final String configuration;

        TreeBuilder(String[] lines, VevosFileConditionContainer fileConditionContainer, Path path, String configuration) {
            this.lines = lines;
            this.fileConditionContainer = fileConditionContainer;
            this.path = path;
            this.configuration = configuration;
        }

        void addLines(Node.Op parent, List<Scope> scopes, int from, int to) {
            int line = from;
            for (Scope scope : scopes) {
                for (; line < scope.startLine(); line++)
                    this.addLine(parent, line);
                Artifact.Op<ScopeArtifactData> artifact = CppReader.this.entityFactory.createArtifact(new ScopeArtifactData(scope.kind(), scope.signature()));
                Node.Op scopeNode = CppReader.this.entityFactory.createOrderedNode(artifact);
                parent.addChild(scopeNode);
                this.addLines(scopeNode, scope.children(), scope.startLine(), scope.endLine());
                line = scope.endLine() + 1;
            }
            for (; line <= to; line++)
                this.addLine(parent, line);
        }

        private void addLine(Node.Op parent, int line) {
            Artifact.Op<SourceLineArtifactData> artifact = CppReader.this.entityFactory.createArtifact(new SourceLineArtifactData(this.lines[line - 1]));
            Node.Op lineNode = CppReader.this.entityFactory.createNode(artifact);
            lineNode.putProperty("Location", new Location(line, line, this.path, this.configuration));
            if (this.fileConditionContainer != null) {
                for (VevosCondition condition : this.fileConditionContainer.getMatchingPresenceConditions(line, line))
                    lineNode.getFeatureTrace().buildProactiveConditionConjunction(condition.getConditionString());
            }
            parent.addChild(lineNode);
        }
    }

    private static String getConfigurationString(Path base) {
        Path configurationPath = base.resolve(".config");
        if (!Files.exists(configurationPath))
            return "";
        try (Stream<String> stream = Files.lines(configurationPath)) {
            return stream.findFirst().orElse("");
        } catch (IOException e) {
            throw new EccoException("Could not read " + configurationPath, e);
        }
    }

    @Override
    public void addListener(ReadListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(ReadListener listener) {
        this.listeners.remove(listener);
    }
}
