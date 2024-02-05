const ts = require(nodePath);
const sf = ts.createSourceFile('sf',fileContent,ts.ScriptTarget.Latest);
const generateAst = (node, sourceFile) => {
    const syntaxKind = ts.SyntaxKind[node.kind];
    node.nodeText = node.getText(sourceFile);
    node.start = node.getStart(sourceFile);
    node.fullText = node.getFullText(sourceFile);
    node.triviaWidth = node.getLeadingTriviaWidth(sourceFile);
    node.forEachChild((child) => generateAst(child, sourceFile));
    node.kind = syntaxKind;
};
(() => generateAst(sf, sf))();