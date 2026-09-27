const ts = require(nodePath);
const sf = ts.createSourceFile('sf',fileContent,ts.ScriptTarget.Latest);
const generateAst = (node, sourceFile) => {
    // map the kind of node from integer to a string.
    const syntaxKind = ts.SyntaxKind[node.kind];
    node.nodeText = node.getText(sourceFile);
    node.start = node.getStart(sourceFile);
    node.fullText = node.getFullText(sourceFile);
    // leading trivia refers to white space and comments. Trivia is associated with the node that's following
    node.triviaWidth = node.getLeadingTriviaWidth(sourceFile);
    // JSDoc nodes (/** ... */) point back to the node they document - a circular structure the
    // conversion to Java rejects, so a file with any JSDoc comment could not be committed. Their
    // text is part of the node's leading trivia anyway.
    delete node.jsDoc;
    delete node.parent;
    node.forEachChild((child) => generateAst(child, sourceFile));
    node.kind = syntaxKind;
};
(() => generateAst(sf, sf))();