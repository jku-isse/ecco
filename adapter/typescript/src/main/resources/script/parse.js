const ts = require('typescript');
const sf = ts.createSourceFile('sf',t,ts.ScriptTarget.Latest);
const generateAst = (node, sourceFile) => {
    const syntaxKind = ts.SyntaxKind[node.kind];
    node.nodeText = node.getText(sourceFile);
    node.forEachChild((child) => generateAst(child, sourceFile));
    node.kind = syntaxKind;
};
(() => generateAst(sf, sf))();