// The ECCO language server's client: starts ecco-lsp for the files of the workspace. The server
// itself decides which files belong to an ECCO working directory (a folder with .ecco above them)
// and answers nothing for the others.
const fs = require('fs');
const path = require('path');
const vscode = require('vscode');
const { LanguageClient } = require('vscode-languageclient/node');

let client;

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
  try {
    await client.start();
  } catch (e) {
    vscode.window.showErrorMessage(
      `ECCO: could not start the language server (${serverOptions(context).command}): ${e.message}. ` +
      'Set ecco.serverPath to the ecco-lsp launcher, and ecco.javaHome if Java is not found.');
  }
}

async function activate(context) {
  context.subscriptions.push(vscode.commands.registerCommand('ecco.restartServer', async () => {
    if (client) await client.stop();
    await start(context);
  }));
  await start(context);
}

async function deactivate() {
  if (client) await client.stop();
}

module.exports = { activate, deactivate };
