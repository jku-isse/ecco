// The ECCO language server's client: starts ecco-lsp for the files of the workspace. The server
// itself decides which files belong to an ECCO working directory (a folder with .ecco above them)
// and answers nothing for the others. After each trace of a file the server sends which lines have
// which condition (ecco/featureRanges), each with a slot of the palette the GUI colours associations
// with, and the configuration checked out; this client colours the code with it, lists it in the
// ECCO Features view, shows the configuration in the status bar and folds by feature.
const fs = require('fs');
const path = require('path');
const vscode = require('vscode');
const { LanguageClient } = require('vscode-languageclient/node');

let client;

// The GUI's CategoricalColorPalette, in its order: 8 hues, then a second shade of each - chosen to
// stay apart for colour-vision deficiencies. A colour always comes with its condition in the code
// lens above it and in the ECCO Features view, never alone.
const PALETTE = [
  '#2a78d6', '#1baf7a', '#eda100', '#008300', '#4a3aa7', '#e34948', '#e87ba4', '#eb6834',
  '#7babe6', '#179568', '#c98900', '#6bb76b', '#8277c2', '#882c2b', '#99516c', '#ee8054'
];
// code not committed yet (the palette's "other")
const NOT_COMMITTED = '#898781';
// translucent, so the text stays readable on light and dark themes
const BACKGROUND_ALPHA = '33';

const decorationTypes = new Map(); // palette slot (-1: not committed) -> TextEditorDecorationType
const paramsByUri = new Map(); // uri -> what the server sent last: {uri, configuration, fileCondition, ranges}

function slotOf(range) {
  return range.colour < 0 ? -1 : range.colour % PALETTE.length;
}

function colourOf(slot) {
  return slot < 0 ? NOT_COMMITTED : PALETTE[slot];
}

function labelOf(range) {
  return range.condition || 'not committed';
}

// ---------------------------------------------------------------- colours

function decorationType(slot) {
  if (!decorationTypes.has(slot)) {
    const colour = colourOf(slot);
    decorationTypes.set(slot, vscode.window.createTextEditorDecorationType({
      isWholeLine: true,
      backgroundColor: colour + BACKGROUND_ALPHA,
      overviewRulerColor: colour,
      overviewRulerLane: vscode.OverviewRulerLane.Left,
      // a bar at the left edge too, which a selection or the current-line highlight does not hide
      borderColor: colour,
      borderStyle: 'solid',
      borderWidth: '0 0 0 3px'
    }));
  }
  return decorationTypes.get(slot);
}

function coloursOn() {
  return vscode.workspace.getConfiguration('ecco').get('featureColours', true);
}

function rangesOf(document) {
  const params = paramsByUri.get(document.uri.toString());
  return params ? params.ranges || [] : [];
}

function paint(editor) {
  const ranges = coloursOn() ? rangesOf(editor.document) : [];
  const bySlot = new Map();
  for (const range of ranges) {
    const slot = slotOf(range);
    if (!bySlot.has(slot)) bySlot.set(slot, []);
    bySlot.get(slot).push({
      range: new vscode.Range(range.startLine, 0, range.endLine, 0),
      hoverMessage: `ECCO: ${labelOf(range)}`
    });
  }
  // every slot used so far: cleared where the file has none any more
  for (const slot of new Set([...decorationTypes.keys(), ...bySlot.keys()]))
    editor.setDecorations(decorationType(slot), bySlot.get(slot) || []);
}

function paintAll() {
  vscode.window.visibleTextEditors.forEach(paint);
}

// ---------------------------------------------------------------- ECCO Features view

// a small square of each colour, written once into the extension's storage (a tree item's icon is a file)
function swatch(context, slot) {
  const dir = context.globalStorageUri.fsPath;
  const file = path.join(dir, `swatch-${slot < 0 ? 'none' : slot}.svg`);
  if (!fs.existsSync(file)) {
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(file, `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"><rect x="2" y="2" width="12" height="12" rx="2" fill="${colourOf(slot)}"/></svg>`);
  }
  return vscode.Uri.file(file);
}

function lineCount(ranges) {
  return ranges.reduce((sum, r) => sum + r.endLine - r.startLine + 1, 0);
}

// The conditions of the active editor's file, each with its blocks: [{label, slot, ranges}], in the
// order they first appear going down the file.
function conditionsOf(document) {
  const groups = new Map();
  for (const range of rangesOf(document)) {
    const label = labelOf(range);
    if (!groups.has(label)) groups.set(label, { label, slot: slotOf(range), ranges: [] });
    groups.get(label).ranges.push(range);
  }
  return [...groups.values()];
}

class FeaturesView {
  constructor(context) {
    this.context = context;
    this.changed = new vscode.EventEmitter();
    this.onDidChangeTreeData = this.changed.event;
  }

  refresh() {
    this.changed.fire();
  }

  getTreeItem(element) {
    return element.item;
  }

  getChildren(element) {
    if (element) return element.children || [];
    const editor = vscode.window.activeTextEditor;
    if (!editor) return [];
    const params = paramsByUri.get(editor.document.uri.toString());
    if (!params) return [];
    const uri = editor.document.uri;
    const items = [];
    if (params.fileCondition) {
      const item = new vscode.TreeItem(params.fileCondition, vscode.TreeItemCollapsibleState.None);
      item.description = 'the file - not coloured';
      item.tooltip = `The file's own condition: code with it is present whenever the file is, so it is not coloured.`;
      item.iconPath = new vscode.ThemeIcon('file');
      items.push({ item });
    }
    for (const group of conditionsOf(editor.document)) {
      const lines = lineCount(group.ranges);
      const item = new vscode.TreeItem(group.label, vscode.TreeItemCollapsibleState.Collapsed);
      item.description = `${lines} ${lines === 1 ? 'line' : 'lines'} · ${group.ranges.length} ${group.ranges.length === 1 ? 'block' : 'blocks'}`;
      item.iconPath = swatch(this.context, group.slot);
      item.contextValue = 'eccoCondition';
      item.tooltip = group.label;
      const children = group.ranges.map(range => {
        const block = new vscode.TreeItem(
          range.startLine === range.endLine ? `line ${range.startLine + 1}` : `lines ${range.startLine + 1}–${range.endLine + 1}`,
          vscode.TreeItemCollapsibleState.None);
        block.description = editor.document.lineAt(Math.min(range.startLine, editor.document.lineCount - 1)).text.trim().slice(0, 60);
        block.command = { command: 'ecco.revealLines', title: 'Show', arguments: [uri, range.startLine, range.endLine] };
        return { item: block };
      });
      items.push({ item, children, label: group.label });
    }
    return items;
  }
}

// ---------------------------------------------------------------- status bar

// "A.3f39114, BASE.2815d71" -> "A, BASE": the revisions in the tooltip
function shortConfiguration(configuration) {
  return configuration.split(',').map(s => s.trim().replace(/\.[0-9a-f]{4,}$/, '')).filter(s => s).join(', ');
}

function updateStatus(status) {
  const editor = vscode.window.activeTextEditor;
  const params = editor && paramsByUri.get(editor.document.uri.toString());
  if (!params || !params.configuration) {
    status.hide();
    return;
  }
  const short = shortConfiguration(params.configuration);
  status.text = `$(layers) ${short.length > 50 ? short.slice(0, 49) + '…' : short}`;
  status.tooltip = new vscode.MarkdownString(`**ECCO checkout**\n\n\`${params.configuration}\`\n\nClick to show the features of this file.`);
  status.show();
}

// ---------------------------------------------------------------- folding by feature

async function pickCondition(placeHolder) {
  const editor = vscode.window.activeTextEditor;
  if (!editor) return null;
  const groups = conditionsOf(editor.document);
  if (groups.length === 0) {
    vscode.window.showInformationMessage('ECCO: no feature blocks in this file.');
    return null;
  }
  const picked = await vscode.window.showQuickPick(
    groups.map(g => ({ label: g.label, description: `${lineCount(g.ranges)} lines · ${g.ranges.length} blocks`, group: g })),
    { placeHolder });
  return picked ? picked.group : null;
}

// folds the blocks whose first lines are [lines] (0-based): the server's folding ranges start there
async function fold(editor, lines) {
  if (lines.length === 0) return;
  await vscode.window.showTextDocument(editor.document, editor.viewColumn);
  await vscode.commands.executeCommand('editor.fold', { selectionLines: lines, levels: 1 });
}

async function foldFeature(group) {
  const editor = vscode.window.activeTextEditor;
  group = group && group.ranges ? group : await pickCondition('Fold the code of…');
  if (!editor || !group) return;
  await fold(editor, group.ranges.filter(r => r.endLine > r.startLine).map(r => r.startLine));
}

async function foldOtherFeatures(group) {
  const editor = vscode.window.activeTextEditor;
  group = group && group.ranges ? group : await pickCondition('Fold everything but the code of…');
  if (!editor || !group) return;
  const others = rangesOf(editor.document).filter(r => labelOf(r) !== group.label && r.endLine > r.startLine);
  await fold(editor, others.map(r => r.startLine));
}

async function unfoldFeatures() {
  const editor = vscode.window.activeTextEditor;
  if (!editor) return;
  const lines = rangesOf(editor.document).filter(r => r.endLine > r.startLine).map(r => r.startLine);
  if (lines.length > 0)
    await vscode.commands.executeCommand('editor.unfold', { selectionLines: lines, levels: 1 });
}

// ---------------------------------------------------------------- the server

// run from the repository (lsp/vscode): the launcher ./gradlew :ecco-lsp:installDist builds beside it
function builtLauncher(context) {
  // the real folder: linked into ~/.vscode/extensions, extensionPath is the link
  const launcher = path.join(fs.realpathSync(context.extensionPath), '..', 'build', 'install', 'ecco-lsp', 'bin',
    process.platform === 'win32' ? 'ecco-lsp.bat' : 'ecco-lsp');
  return fs.existsSync(launcher) ? launcher : null;
}

function serverOptions(context) {
  const config = vscode.workspace.getConfiguration('ecco');
  const command = config.get('serverPath') || builtLauncher(context) || 'ecco-lsp';
  const env = { ...process.env };
  const javaHome = config.get('javaHome');
  if (javaHome) env.JAVA_HOME = javaHome;
  return { command, args: [], options: { env } };
}

async function start(context, onRanges) {
  client = new LanguageClient('ecco', 'ECCO', serverOptions(context), {
    documentSelector: [{ scheme: 'file' }],
    synchronize: {
      // a checkout (.config) or a commit (the repository) elsewhere: the server traces again
      fileEvents: vscode.workspace.createFileSystemWatcher('**/{.config,.ecco/*}')
    }
  });
  client.onNotification('ecco/featureRanges', params => {
    paramsByUri.set(params.uri, params);
    vscode.window.visibleTextEditors
      .filter(editor => editor.document.uri.toString() === params.uri)
      .forEach(paint);
    onRanges(params.uri);
  });
  try {
    await client.start();
  } catch (e) {
    vscode.window.showErrorMessage(
      `ECCO: could not start the language server (${serverOptions(context).command}): ${e.message}. ` +
      'Set ecco.serverPath to the ecco-lsp launcher, and ecco.javaHome if Java is not found.');
  }
}

async function activate(context) {
  const view = new FeaturesView(context);
  const status = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 50);
  status.command = 'ecco.features.focus';
  const activeUri = () => vscode.window.activeTextEditor && vscode.window.activeTextEditor.document.uri.toString();
  const onRanges = uri => {
    if (uri === activeUri()) {
      view.refresh();
      updateStatus(status);
    }
  };

  context.subscriptions.push(
    status,
    vscode.window.registerTreeDataProvider('ecco.features', view),
    vscode.commands.registerCommand('ecco.restartServer', async () => {
      if (client) await client.stop();
      paramsByUri.clear();
      paintAll();
      view.refresh();
      updateStatus(status);
      await start(context, onRanges);
    }),
    vscode.commands.registerCommand('ecco.toggleFeatureColours', async () => {
      const config = vscode.workspace.getConfiguration('ecco');
      await config.update('featureColours', !coloursOn(), vscode.ConfigurationTarget.Global);
    }),
    vscode.commands.registerCommand('ecco.revealLines', async (uri, startLine, endLine) => {
      const editor = await vscode.window.showTextDocument(uri, { preserveFocus: false });
      const range = new vscode.Range(startLine, 0, endLine, 0);
      editor.selection = new vscode.Selection(startLine, 0, startLine, 0);
      editor.revealRange(range, vscode.TextEditorRevealType.InCenterIfOutsideViewport);
    }),
    // from the command palette (asks which feature) or a condition in the view (that one)
    vscode.commands.registerCommand('ecco.foldFeature', element => foldFeature(element && conditionsOf(vscode.window.activeTextEditor.document).find(g => g.label === element.label))),
    vscode.commands.registerCommand('ecco.foldOtherFeatures', element => foldOtherFeatures(element && conditionsOf(vscode.window.activeTextEditor.document).find(g => g.label === element.label))),
    vscode.commands.registerCommand('ecco.unfoldFeatures', unfoldFeatures),
    vscode.workspace.onDidChangeConfiguration(event => {
      if (event.affectsConfiguration('ecco.featureColours')) paintAll();
    }),
    vscode.window.onDidChangeVisibleTextEditors(paintAll),
    vscode.window.onDidChangeActiveTextEditor(() => {
      view.refresh();
      updateStatus(status);
    }),
    vscode.workspace.onDidCloseTextDocument(document => paramsByUri.delete(document.uri.toString())),
    { dispose: () => decorationTypes.forEach(type => type.dispose()) }
  );
  await start(context, onRanges);
}

async function deactivate() {
  if (client) await client.stop();
}

module.exports = { activate, deactivate, shortConfiguration };
