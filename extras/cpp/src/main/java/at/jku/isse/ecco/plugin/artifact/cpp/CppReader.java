package at.jku.isse.ecco.plugin.artifact.cpp;

import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.tree.Node;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.cpp.*;
import org.eclipse.cdt.core.dom.ast.gnu.cpp.GPPLanguage;
import org.eclipse.cdt.core.model.ILanguage;
import org.eclipse.cdt.core.parser.*;
import org.eclipse.cdt.internal.core.parser.IMacroDictionary;
import org.eclipse.cdt.internal.core.parser.SavedFilesProvider;
import org.eclipse.cdt.internal.core.parser.scanner.InternalFileContent;
import org.eclipse.core.runtime.CoreException;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;

public class CppReader implements ArtifactReader<Path, Set<Node.Op>> {

	public static final String NODE_OFFSET = "offset";

	class Pair {
		protected IBinding binding;
		protected CDTArtifactData artifact;

		public Pair(IBinding binding, CDTArtifactData artifact) {
			this.binding = binding;
			this.artifact = artifact;
		}

		public Pair(CDTArtifactData artifact, IBinding binding) {
			this.binding = binding;
			this.artifact = artifact;
		}
	}

	private List<Pair> referencing = new LinkedList<Pair>();
	private IdentityHashMap<IBinding, CDTArtifactData> referenced = new IdentityHashMap<IBinding, CDTArtifactData>();


	@Override
	public HashSet<Node> parseArtifacts(File dir) {
		return parseFromSource(dir, null, false);
	}


	public HashSet<Node> parseFromSource(File dir, Map<String, IASTPreprocessorStatement[]> preprocessorStatements, final boolean saveLocationInfromtation) {
		// Get the valid source files as a list
		List<File> files = new ArrayList<File>();
		getFilesRecursively(files, dir);

		return parseFromSource(files, preprocessorStatements, saveLocationInfromtation);
	}

	public HashSet<Node> parseFromSource(List<File> files, Map<String, IASTPreprocessorStatement[]> preprocessorStatements, final boolean saveLocationInfromtation) {

		HashSet<Node> ns = new HashSet<Node>();

//		String headersString = "";
//		String sourcesString = "";

		final List<String> headerFiles = new ArrayList<String>();
		for (File file : files) {
			try {
				if (file.getName().endsWith(".h")) {
					headerFiles.add(file.getCanonicalPath());

//					headersString += getFileContentWithoutIfdefs(file, new IASTPreprocessorStatement[0]);
				} else {
//					sourcesString += getFileContentWithoutIfdefs(file, new IASTPreprocessorStatement[0]);
				}

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		//everthing in a single file
//		{
//			FileContent fileContent = FileContent.create("tmp", (headersString + sourcesString).toCharArray());
//
//			Map<String, String> definedSymbols = new HashMap<String, String>();
//			String[] includePaths = headerFiles.toArray(new String[headerFiles.size()]);
//			IScannerInfo info = new ScannerInfo(definedSymbols, includePaths);
//			IParserLogService log = new DefaultLogService();
//
//			IncludeFileContentProvider emptyIncludes = IncludeFileContentProvider.getEmptyFilesProvider();
//
//			int opts = 8;
//			try {
//				IASTTranslationUnit translationUnit = GPPLanguage.getDefault().getASTTranslationUnit(fileContent, info, emptyIncludes, null, opts, log);
//
//				print(translationUnit, new PrintStream("CDTTree.txt"));
//
//			} catch (CoreException ex){
//
//			} catch (FileNotFoundException e) {
//				e.printStackTrace();
//			}
//		}


		Map<String, IASTPreprocessorIncludeStatement[]> includes = new HashMap<String, IASTPreprocessorIncludeStatement[]>();

		PrintStream out;
		try {
			out = new PrintStream("AST.txt");
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
			return null;
		}

		int i = 1;

		for (File file : files) {
			try {

//				System.out.println(i++ + " / " + files.size() + " : " + file.getAbsolutePath());

				FileContent fileContent = FileContent.createForExternalFileLocation(file.getAbsolutePath());


				Map<String, String> definedSymbols = new HashMap<String, String>();
				String[] includePaths = headerFiles.toArray(new String[headerFiles.size()]);
				IScannerInfo info = new ScannerInfo(definedSymbols, includePaths);
				IParserLogService log = new DefaultLogService();

//				IncludeFileContentProvider emptyIncludes = IncludeFileContentProvider.getEmptyFilesProvider();

				IncludeFileContentProvider emptyIncludes = new SavedFilesProvider() {
					@Override
					public InternalFileContent getContentForInclusion(String path, IMacroDictionary macroDictionary) {
						if (!getInclusionExists(path)) {
//						if(!headerFiles.contains(path)){
							return null;
						}
//						System.out.println(path);
						return (InternalFileContent) FileContent.createForExternalFileLocation(path);
					}
				};

//				int opts = 8;
				int opts = ILanguage.OPTION_PARSE_INACTIVE_CODE | ILanguage.OPTION_IS_SOURCE_UNIT;
				IASTTranslationUnit translationUnit = GPPLanguage.getDefault().getASTTranslationUnit(fileContent, info, emptyIncludes, null, opts, log);


				IASTPreprocessorStatement[] ppStatements = translationUnit.getAllPreprocessorStatements();

				if (preprocessorStatements != null) {
					List<IASTPreprocessorStatement> ppStatementsInFile = new LinkedList<IASTPreprocessorStatement>();
					for (IASTPreprocessorStatement preprocessorStatement : ppStatements) {
						if (preprocessorStatement.getContainingFilename().equals(translationUnit.getContainingFilename())) {
							ppStatementsInFile.add(preprocessorStatement);
						}
					}
					preprocessorStatements.put(translationUnit.getContainingFilename(), ppStatementsInFile.toArray(new IASTPreprocessorStatement[ppStatementsInFile.size()]));
				}

//				System.out.println(file.getName());
//				for (IASTPreprocessorStatement preprocessorStatement : ppStatements) {
//					System.out.println(preprocessorStatement.getContainingFilename());
//					System.out.println("pp: " + preprocessorStatement.getRawSignature() + " @: " + preprocessorStatement.getFileLocation().getStartingLineNumber()
//																						+ " : " + preprocessorStatement.getFileLocation().getEndingLineNumber()
//																						+ " : " + preprocessorStatement.getFileLocation().getNodeOffset()
//																						+ " : " + preprocessorStatement.getFileLocation().getNodeLength()
//																						+ " is: " + preprocessorStatement.getClass().getCanonicalName());
//
//				}

//				for(IASTComment comm : translationUnit.getComments()){
//					System.out.println(comm);
//				}

				includes.put(translationUnit.getContainingFilename(), translationUnit.getIncludeDirectives());

//				IASTPreprocessorIncludeStatement[] includeStatements = translationUnit.getIncludeDirectives();
//
//				for (IASTPreprocessorIncludeStatement include : includeStatements) {
//					System.out.println("include - " + include.getName());
//					System.out.println(include.getPath() + " : " + include.createsAST());
//				}

				//comment out preprocessor directives
				try {
					String content = getFileContentWithoutIfdefs(file, ppStatements);

					fileContent = FileContent.create(file.getCanonicalPath(), content.toCharArray());

					//parse again
					translationUnit = GPPLanguage.getDefault().getASTTranslationUnit(fileContent, info, emptyIncludes, null, opts, log);

				} catch (IOException e) {
					e.printStackTrace();
				}


				String ident = getIdentifier(translationUnit);
				CDTArtifactData transArtifact = new CDTArtifactData(ident, ident, translationUnit.getClass().getName(), translationUnit.getPropertyInParent());
				int offset = translationUnit.getFileLocation().getNodeOffset();
				transArtifact.putProperty(new ArtifactProperty<Integer>(NODE_OFFSET, offset));
				transArtifact.setSource(translationUnit.getContainingFilename());
				try {
					transArtifact.setSourceType(file.getName().substring(file.getName().lastIndexOf('.')));
				} catch (StringIndexOutOfBoundsException e) {
					transArtifact.setSourceType(file.getName());
				}

				Node transNode = new OrderedNode(transArtifact);

				print(translationUnit, out);

				traverseAST(translationUnit, transNode, saveLocationInfromtation, "");

				ns.add(transNode);

			} catch (CoreException e) {
				e.printStackTrace();
			}

		}

		resolveReverences(includes);

		//if h file is included make reference from translationunit c to translationunit h
		for (Node n : ns) {
			if (n.getArtifact().getType().equals("org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTTranslationUnit")) {
				IASTPreprocessorIncludeStatement[] curIncludes = includes.get(n.getArtifact().getIdentifier());

//				System.out.println(n.getArtifact());
				if (curIncludes != null) {

					for (IASTPreprocessorIncludeStatement include : curIncludes) {
						try {
							String includePath = new File(new File(n.getArtifact().getIdentifier()).getParent(), include.getName().toString()).getCanonicalPath();

							Node hNode = getNode(includePath, ns);
							if (hNode != null) {
//								System.out.println(hNode.getArtifact());

								ArtifactReference reference = new ArtifactReference(n.getArtifact(), hNode.getArtifact());
								//TODO check if reference already exists

								n.getArtifact().usesArtifact(reference);
								hNode.getArtifact().isUsedByArtifact(reference);
							}
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}

		return ns;
	}

	private Node getNode(String id, Collection<Node> nodes) {
		for (Node n : nodes) {
			if (n.getArtifact().getIdentifier().equals(id)) {
				return n;
			}
		}
		return null;
	}

	private void traverseAST(IASTNode astNode, Node parent, final boolean saveLocationInfromtation, String indent) {

		for (IASTNode child : astNode.getChildren()) {
			if (child != null) {
				String ident = getIdentifier(child);
				String type = child.getClass().getCanonicalName();
				CDTArtifactData artifact = new CDTArtifactData(child.getRawSignature(), ident, type, child.getPropertyInParent());

				Node node;
				if (isOrdered(child)) {
					node = new OrderedNode(artifact);
				} else {
					node = new UnorderedNode(artifact);
				}
				parent.addChild(node);

				addProperties(artifact, child, saveLocationInfromtation);
				artifact.setSource(parent.getArtifact().getSource());
				artifact.setSourceType(parent.getArtifact().getSourceType());
				checkForReferences(artifact, child);

//				System.out.println(indent + artifact);

				traverseAST(child, node, saveLocationInfromtation, indent + "\t");
			}
		}
	}

	private void print(IASTNode node, PrintStream out) {
		CDTAstIterator it = new CDTAstIterator(node);
		while (it.hasNext()) {
			IASTNode next = it.next();
			int depth = it.getRelativeDepth();
			String line = "";
			for (int i = 0; i < depth; i++) {
				line += "\t";
			}

//			System.out.println(next.getRawSignature());
			line += getIdentifier(next) + " [" + next.getClass().getName() + "] {" + next.getPropertyInParent() + "}";
//			if(next instanceof IASTName){
//				line += " <" + ((IASTName) next).resolveBinding().getClass().getCanonicalName() + "(" + System.identityHashCode(((IASTName) next).resolveBinding()) + ")" + ">";
//			}
			out.println(line);
		}
	}


	private void addProperties(CDTArtifactData artifact, final IASTNode astNode, final boolean saveLocationInfromtation) {
		if (saveLocationInfromtation) {
			int offset;
			if (astNode.getFileLocation() == null) {
				offset = (int) artifact.getContainingNode().getParent().getArtifact().getProperty(NODE_OFFSET).getValue();
			} else {
				offset = astNode.getFileLocation().getNodeOffset();
			}
			artifact.putProperty(new ArtifactProperty<Integer>(NODE_OFFSET, offset));
		}
	}

	private void checkForReferences(CDTArtifactData artifact, IASTNode node) {

		//check if type has bindings
		Map<Class<?>, List<String>> coveredBindineg = new HashMap<Class<?>, List<String>>();
		coveredBindineg.put(IASTName.class, Arrays.asList(new String[]{"getBinding", "getPreBinding", "resolveBinding", "resolvePreBinding"}));

		Class<?> clazz = null;
		try {
			clazz = node.getClass();
			Method[] methods = clazz.getMethods();
			for (Method m : methods) {
				Class<?> retType = m.getReturnType();

//				if(retType.isAssignableFrom(IScope.class)){
//					System.out.println("SCOPE: " + clazz.getCanonicalName() + " := " + m.getName());
//				}

				if (retType.getName().equals("org.eclipse.cdt.core.dom.ast.IBinding") ||
						retType.getName().equals("org.eclipse.cdt.core.dom.ast.ICompositeType") ||
						retType.getName().equals("org.eclipse.cdt.core.dom.ast.IEnumeration") ||
						retType.getName().equals("org.eclipse.cdt.core.dom.ast.IEnumerator") ||
						retType.getName().equals("org.eclipse.cdt.core.dom.ast.IField") ||
						retType.getName().equals("org.eclipse.cdt.core.dom.ast.IFunction") ||
						retType.getName().equals("org.eclipse.cdt.core.dom.ast.ILabel") ||
						retType.getName().equals("org.eclipse.cdt.core.dom.ast.IMacroBinding") ||
						retType.getName().equals("org.eclipse.cdt.core.dom.ast.IParameter") ||
						retType.getName().equals("org.eclipse.cdt.core.dom.ast.IProblemBinding") ||
						retType.getName().equals("org.eclipse.cdt.core.dom.ast.ITypedef") ||
						retType.getName().equals("org.eclipse.cdt.core.dom.ast.IVariable")) {

					//if(IBinding.class.isInstance(retType)){
					//if(retType.isInstance(IBinding.class)){

					boolean isCovered = false;
					for (Map.Entry<Class<?>, List<String>> entry : coveredBindineg.entrySet()) {
						if (entry.getKey().isAssignableFrom(clazz) &&
								entry.getValue().contains(m.getName())) {

							isCovered = true;
							break;
						}
					}

					if (!isCovered) {
						System.out.println("BINDING UNRESOLVED: " + clazz.getCanonicalName() + " := " + m.getName() + " : " + retType.getName() + " in: " + m.getDeclaringClass().getCanonicalName());
					}
				}
			}
		} catch (NoClassDefFoundError e) {
			if (clazz != null) {
				System.out.println("ERROR: " + clazz.getCanonicalName());
			}
		}


		//referenced node types

		//referencing node type
		if (node instanceof IASTName) {
			IBinding bind = null;
			try {
				bind = ((IASTName) node).resolveBinding();
			} catch (Exception e) {
			}
			if (bind != null && isReferencing(node)) {
				referencing.add(new Pair(artifact, bind));
			} else {
				referenced.put(bind, artifact);
			}
//			System.out.println(node.getRawSignature());
//			System.out.println(System.identityHashCode(((IASTName) node).resolveBinding()) + " : " + ((IASTName) node).resolveBinding().getClass());
//			System.out.println(isReferencing(node));
//			System.out.println(System.identityHashCode(((IASTName) node).getPreBinding()));
//			System.out.println(System.identityHashCode(((IASTName) node).getBinding()));
//			System.out.println(System.identityHashCode(((IASTName) node).resolvePreBinding()));
		}

	}

	private boolean isReferencing(IASTNode astNode) {
		IASTNode parent = astNode;
		while (parent != null) {
			if (parent instanceof IASTStatement) {
				if (parent instanceof IASTDeclarationStatement) {
					return false;
				} else {
					return true;
				}
			}
			parent = parent.getParent();
		}
		return false;
	}

	private void resolveReverences(Map<String, IASTPreprocessorIncludeStatement[]> includes) {
		for (Pair pair : referencing) {
			CDTArtifactData ref = referenced.get(pair.binding);
			if (ref != null) {
				ArtifactReference reference = new ArtifactReference(pair.artifact, ref);
				//TODO check if reference already exists

				pair.artifact.usesArtifact(reference);
				ref.isUsedByArtifact(reference);
			} else {
				//TODO check in includes
//				System.out.println(pair.artifact + " : " + pair.binding.getClass().getCanonicalName());
//				String artifactSource = pair.artifact.getSource();
//				File containedIn = new File(artifactSource).getParentFile();
//				for(Entry<IBinding, CDTArtifactData> entry : referenced.entrySet()){
//					CDTArtifactData refArtifact = entry.getValue();
//
//					IASTPreprocessorIncludeStatement[] curIncludes = includes.get(artifactSource);
//					if(curIncludes != null && curIncludes.length > 0){
//						if(isIncluded(refArtifact.getSource(), containedIn, curIncludes)){
//							if(refArtifact.getIdentifier().equals(pair.artifact.getIdentifier())){
//								System.out.println("found in: " + refArtifact.getSource());
//								System.out.println(System.identityHashCode(pair.binding) + " -> " + System.identityHashCode(entry.getKey()));
//								break;
//							}
//						}
//
//
//					}
//				}
			}
		}

		//find function definitions in header file
//		for(Entry<IBinding, CDTArtifactData> source : referenced.entrySet()){
//			CDTArtifactData sourceArtifact = source.getValue();
//			String artifactSource = sourceArtifact.getSource();
//			File containedIn = new File(artifactSource).getParentFile();
//			IASTPreprocessorIncludeStatement[] curIncludes = includes.get(artifactSource);
//
//			for(Entry<IBinding, CDTArtifactData> header : referenced.entrySet()){
//				CDTArtifactData headerArtifact = header.getValue();
//				if(isIncluded(headerArtifact.getSource(), containedIn, curIncludes) || //is included header
//						headerArtifact.getSource().equals(artifactSource)){ //or is same file
//
//					if(sourceArtifact.getIdentifier().equals(headerArtifact.getIdentifier())){
//
//						//TODO you have to check the parents
//						Node sourceParent = sourceArtifact.getContainingNode().getParent();
//						Node headerParent = headerArtifact.getContainingNode().getParent();
//
//						//sourceArtifact is of type IASTFunctionDefinition
//						if(sourceArtifact.getPropertyInParent().toString().equals("IASTDeclarator.DECLARATOR_NAME - IASTName for IASTDeclarator") &&
//								((CDTArtifactData)sourceParent.getArtifact()).getPropertyInParent().toString().equals("IASTFunctionDefinition.DECLARATOR - IASTFunctionDeclarator for IASTFunctionDefinition")){
//
//							//headerArtifact is of type IASTSimpleDeclaration
//							if(headerArtifact.getPropertyInParent().toString().equals("IASTDeclarator.DECLARATOR_NAME - IASTName for IASTDeclarator") &&
//									((CDTArtifactData)headerParent.getArtifact()).getPropertyInParent().toString().equals("IASTSimpleDeclaration.DECLARATOR - IASTDeclarator for IASTSimpleDeclaration")){
//
//
//								if(sourceParent.getArtifact().getIdentifier().equals(headerParent.getArtifact().getIdentifier())){
//
//									ArtifactReference reference = new ArtifactReference(sourceArtifact, headerArtifact);
//
//									sourceArtifact.usesArtifact(reference);
//									headerArtifact.isUsedByArtifact(reference);
//
////									System.out.println("found: " + sourceArtifact + " -> " + headerArtifact);
////									System.out.println(System.identityHashCode(source.getKey()) + " -> " + System.identityHashCode(header.getKey()));
//								}
//								headerParent = headerParent.getParent();
//							}
//						}
//					}
//				}
//			}
//		}
	}

	private boolean isIncluded(String source, File containedIn, IASTPreprocessorIncludeStatement[] includes) {
		for (IASTPreprocessorIncludeStatement include : includes) {
			File headerFile = new File(containedIn, include.getName().toString());
			if (headerFile.getAbsolutePath().equals(source)) {
				return true;
			}
		}

		return false;
	}

	public static String getIdentifier(IASTNode node) {
		//TODO create identifiers for nodes
		if (node instanceof IASTTranslationUnit) {
			return node.getContainingFilename();
		} else if (node instanceof IASTCompoundStatement) {
			return "BLOCK";
		} else if (node instanceof IASTCompositeTypeSpecifier) {
			return "struct " + ((IASTCompositeTypeSpecifier) node).getName().getRawSignature();
		} else if (node instanceof IASTIfStatement) {
			if (((IASTIfStatement) node).getConditionExpression() != null) {
				return "if(" + ((IASTIfStatement) node).getConditionExpression().getRawSignature() + ")";
			} else {
				return "if(" + ((ICPPASTIfStatement) node).getConditionDeclaration().getRawSignature() + ")";
			}
		} else if (node instanceof IASTSwitchStatement) {
			if (((IASTSwitchStatement) node).getControllerExpression() != null) {
				return "switch(" + ((IASTSwitchStatement) node).getControllerExpression().getRawSignature() + ")";
			} else {
				return "switch(" + ((ICPPASTSwitchStatement) node).getControllerDeclaration().getRawSignature() + ")";
			}
		} else if (node instanceof IASTForStatement) {
			String init = ";";
			if (((IASTForStatement) node).getInitializerStatement() != null) {
				init = ((IASTForStatement) node).getInitializerStatement().getRawSignature();
			}
			String condition = "";
			if (((IASTForStatement) node).getConditionExpression() != null) {
				condition = ((IASTForStatement) node).getConditionExpression().getRawSignature();
			} else if (node instanceof ICPPASTForStatement && ((ICPPASTForStatement) node).getConditionDeclaration() != null) {
				condition = ((ICPPASTForStatement) node).getConditionDeclaration().getRawSignature();
			}
			String iteration = "";
			if (((IASTForStatement) node).getIterationExpression() != null) {
				iteration = ((IASTForStatement) node).getIterationExpression().getRawSignature();
			}
			return "for(" + init + " "
					+ condition + "; "
					+ iteration + ")";
		} else if (node instanceof IASTWhileStatement) {
			if (((IASTWhileStatement) node).getCondition() != null) {
				return "while(" + ((IASTWhileStatement) node).getCondition().getRawSignature() + ")";
			} else {
				return "while(" + ((ICPPASTWhileStatement) node).getConditionDeclaration().getRawSignature() + ")";
			}
		} else if (node instanceof IASTDoStatement) {
			return "do while(" + ((IASTDoStatement) node).getCondition().getRawSignature() + ")";
		} else if (node instanceof IASTSimpleDeclaration) {
			if (((IASTSimpleDeclaration) node).getDeclSpecifier() instanceof IASTCompositeTypeSpecifier) {
				return getIdentifier(((IASTSimpleDeclaration) node).getDeclSpecifier());
			}
			if (((IASTSimpleDeclaration) node).getDeclarators().length == 1) {
				return getIdentifier(((IASTSimpleDeclaration) node).getDeclarators()[0]);
			}
		} else if (node instanceof IASTFunctionDefinition) {
			return getIdentifier(((IASTFunctionDefinition) node).getDeclarator());
		} else if (node instanceof ICPPASTFunctionDeclarator) {
			String name = ((IASTFunctionDeclarator) node).getName().getRawSignature();
			String parameters = "";
			boolean first = true;
			for (ICPPASTParameterDeclaration para : ((ICPPASTFunctionDeclarator) node).getParameters()) {
				if (!first) {
					parameters += ", ";
				}
				parameters += para.getDeclSpecifier().getRawSignature();
				first = false;
			}
			//TODO insert parameter types into string
			return name + "(" + parameters + ")";
		} else if (node instanceof IASTFunctionDeclarator) {
			String name = ((IASTFunctionDeclarator) node).getName().getRawSignature();
			String parameters = "";
			//TODO insert parameter types into string
			return name + "(" + parameters + ")";
		} else if (node instanceof ICPPASTLinkageSpecification) {
			//TODO could be more extern "C" -> not a unique identifier
			return "extern " + ((ICPPASTLinkageSpecification) node).getLiteral() + "";
		}
		return node.getRawSignature();
	}

	private static boolean isOrdered(IASTNode node) {
		//TODO define nodes that do not have to be ordered and return false for them
		return true;
	}

	private static String getFileContentWithoutIfdefs(File f, IASTPreprocessorStatement[] ppStatements) throws IOException {
		StringBuffer content = new StringBuffer();
		BufferedReader reader = new BufferedReader(new FileReader(f));

		String line = reader.readLine();
		while (line != null) {

			for (IASTPreprocessorStatement ppStatement : ppStatements) {
				if (ppStatement instanceof IASTPreprocessorIfdefStatement ||
						ppStatement instanceof IASTPreprocessorIfndefStatement ||
						ppStatement instanceof IASTPreprocessorIfStatement ||
						ppStatement instanceof IASTPreprocessorElseStatement ||
						ppStatement instanceof IASTPreprocessorElifStatement ||
						ppStatement instanceof IASTPreprocessorEndifStatement) {

					if (line.contains(ppStatement.getRawSignature())) {
						line = line.replace(ppStatement.getRawSignature(), "//" + ppStatement.getRawSignature().substring(2));
						break;
					}
				}
			}
			content.append(line + "\n");

			line = reader.readLine();
		}

		reader.close();

		return content.toString();
	}

	public static void getFilesRecursively(List<File> files, File dir) {
		if (dir.isFile()) {
			if (dir.getName().endsWith(".c") ||
					dir.getName().endsWith(".h") ||
					dir.getName().endsWith(".cpp")) {

				files.add(dir);
			}
			return;
		}
		File[] filelist = dir.listFiles();

		for (File f : filelist) {
			if (!(f.isDirectory() && f.getName().contains(".svn"))) {
				getFilesRecursively(files, f);
			}
		}
	}
}
