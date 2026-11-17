// The ECCO language server's client: starts ecco-lsp for the files of the workspace. The server
// itself decides which files belong to an ECCO working directory (a folder with .ecco above them)
// and answers nothing for the others. It also colours code by feature: after each trace of a file
// the server sends which lines have which condition (ecco/featureRanges), each with a slot of the
// palette the GUI colours associations with.
const fs = require('fs');
const path = require('path');
const vscode = require('vscode');
const { LanguageClient } = require('vscode-languageclient/node');

let client;

// The GUI's CategoricalColorPalette, in its order: 8 hues, then a second shade of each - chosen to
// stay apart for colour-vision deficiencies. A colour always comes with its condition in the code
// lens above it, never alone.
const PALETTE = [
  '#2a78d6', '#1baf7a', '#eda100', '#008300', '#4a3aa7', '#e34948', '#e87ba4', '#eb6834',
  '#7babe6', '#179568', '#c98900', '#6bb76b', '#8277c2', '#882c2b', '#99516c', '#ee8054'
];
// code not committed yet (the palette's "other")
const NOT_COMMITTED = '#898781';
// translucent, so the text stays readable on light and dark themes
const BACKGROUND_ALPHA = '33';

const decorationTypes = new Map(); // palette slot (-1: not committed) -> TextEditorDecorationType
const rangesByUri = new Map(); // uri -> the FeatureRange list the server sent last

function decorationType(slot) {
  if (!decorationTypes.has(slot)) {
    const colour = slot < 0 ? NOT_COMMITTED : PALETTE[slot % PALETTE.length];
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

function paint(editor) {
  const ranges = coloursOn() ? rangesByUri.get(editor.document.uri.toString()) || [] : [];
  const bySlot = new Map();
  for (const range of ranges) {
    const slot = range.colour < 0 ? -1 : range.colour % PALETTE.length;
    if (!bySlot.has(slot)) bySlot.set(slot, []);
    bySlot.get(slot).push({
      range: new vscode.Range(range.startLine, 0, range.endLine, 0),
      hoverMessage: range.condition ? `ECCO: ${range.condition}` : 'ECCO: not committed'
    });
  }
  // every slot used so far: cleared where the file has none any more
  for (const slot of new Set([...decorationTypes.keys(), ...bySlot.keys()]))
    editor.setDecorations(decorationType(slot), bySlot.get(slot) || []);
}

function paintAll() {
  vscode.window.visibleTextEditors.forEach(paint);
}

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

async function start(context) {
  client = new LanguageClient('ecco', 'ECCO', serverOptions(context), {
    documentSelector: [{ scheme: 'file' }],
    synchronize: {
      // a checkout (.config) or a commit (the repository) elsewhere: the server traces again
      fileEvents: vscode.workspace.createFileSystemWatcher('**/{.config,.ecco/*}')
    }
  });
  client.onNotification('ecco/featureRanges', params => {
    rangesByUri.set(params.uri, params.ranges || []);
    vscode.window.visibleTextEditors
      .filter(editor => editor.document.uri.toString() === params.uri)
      .forEach(paint);
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
  context.subscriptions.push(
    vscode.commands.registerCommand('ecco.restartServer', async () => {
      if (client) await client.stop();
      rangesByUri.clear();
      paintAll();
      await start(context);
    }),
    vscode.commands.registerCommand('ecco.toggleFeatureColours', async () => {
      const config = vscode.workspace.getConfiguration('ecco');
      await config.update('featureColours', !coloursOn(), vscode.ConfigurationTarget.Global);
    }),
    vscode.workspace.onDidChangeConfiguration(event => {
      if (event.affectsConfiguration('ecco.featureColours')) paintAll();
    }),
    vscode.window.onDidChangeVisibleTextEditors(paintAll),
    vscode.workspace.onDidCloseTextDocument(document => rangesByUri.delete(document.uri.toString())),
    { dispose: () => decorationTypes.forEach(type => type.dispose()) }
  );
  await start(context);
}

async function deactivate() {
  if (client) await client.stop();
}

module.exports = { activate, deactivate };
