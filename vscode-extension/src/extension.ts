import * as vscode from 'vscode';
import { spawn, ChildProcess } from 'child_process';
import * as path from 'path';
import { LanguageClient, StreamInfo } from 'vscode-languageclient/node';
import * as fs from 'fs';
import os = require('os');

let client: LanguageClient | null = null;
let serverProc: ChildProcess | null = null;

function findProjectRoot(startPath: string): string {
  let cur = startPath;
  while (true) {
    if (fs.existsSync(path.join(cur, 'settings.gradle')) || fs.existsSync(path.join(cur, 'settings.gradle.kts'))) {
      return cur;
    }
    const parent = path.dirname(cur);
    if (parent === cur || parent === '' || parent === os.homedir()) break;
    cur = parent;
  }
  return startPath;
}

function findGradleBinary(workspaceRoot: string | undefined) {
  if (!workspaceRoot) return 'gradle';
  const wrapper = path.join(workspaceRoot, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
  try { if (fs.existsSync(wrapper)) return wrapper; } catch {}
  return 'gradle';
}

function findBuiltServerJar(workspaceRoot: string): string | null {
  try {
    const libsDir = path.join(workspaceRoot, 'lsp', 'build', 'libs');
    if (!fs.existsSync(libsDir)) return null;
    const files = fs.readdirSync(libsDir).filter(f => f.endsWith('.jar'));
    if (files.length === 0) return null;
    // prefer shadow/all jar if present
    const pref = files.find(f => /shadow|all|fat/i.test(f));
    const chosen = pref || files[0];
    return path.join(libsDir, chosen);
  } catch {
    return null;
  }
}

async function startServer(context: vscode.ExtensionContext) {
  if (client) {
    vscode.window.showInformationMessage('KafkaSQL language server already running');
    return;
  }

  const ws = vscode.workspace.workspaceFolders && vscode.workspace.workspaceFolders[0];
  if (!ws) {
    vscode.window.showErrorMessage('Open the workspace root to start the KafkaSQL language server.');
    return;
  }
  const openedFolder = ws.uri.fsPath;
  const projectRoot = findProjectRoot(openedFolder);
  if (projectRoot !== openedFolder) {
    // opened a subfolder (examples). prefer the detected Gradle project root
    vscode.window.showInformationMessage(`Using Gradle project root: ${projectRoot} (opened folder: ${openedFolder})`);
  }
  const workspaceRoot = projectRoot;
  const gradle = findGradleBinary(workspaceRoot);

  // create output channel for server logs
  const output = vscode.window.createOutputChannel('KafkaSQL LSP');
  context.subscriptions.push(output);

  // ensure gradlew is executable (if wrapper is used)
  try {
    if (gradle.endsWith('gradlew') && fs.existsSync(gradle)) {
      try { fs.chmodSync(gradle, 0o755); } catch { /* ignore permission change errors */ }
    }
  } catch { /* ignore */ }

  // prefer the project/toolchain JAVA_HOME if available; fall back to process.env
  const defaultJavaHome = process.env.JAVA_HOME || '/opt/homebrew/Cellar/openjdk@21/21.0.8/libexec/openjdk.jdk/Contents/Home';
  const env = Object.assign({}, process.env, {
    JAVA_HOME: defaultJavaHome,
    // GRADLE_OPTS ensures Gradle itself uses the requested java home when launching daemons
    GRADLE_OPTS: `-Dorg.gradle.java.home=${defaultJavaHome} ${process.env.GRADLE_OPTS || ''}`
  });

  output.appendLine(`Spawning Gradle: ${gradle} :lsp:runLanguageServer`);
  output.appendLine(`Using JAVA_HOME=${env.JAVA_HOME}`);
  output.show(true);

  // spawn Gradle to run the LSP (this will build + run :lsp:runLanguageServer)
  const gradleArgs = ['--project-dir', workspaceRoot, ':lsp:runLanguageServer', '--no-daemon'];
  // Prefer running the built language-server jar directly to avoid Gradle stdout pollution
  const serverJar = findBuiltServerJar(workspaceRoot);
  if (serverJar) {
    output.appendLine(`Launching language server jar: ${serverJar}`);
    const javaHome = env.JAVA_HOME || process.env.JAVA_HOME || '';
    const javaBin = javaHome ? path.join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
    const javaExe = fs.existsSync(javaBin) ? javaBin : 'java';
    serverProc = spawn(javaExe, ['-jar', serverJar], { cwd: workspaceRoot, shell: false, env });
  } else {
    output.appendLine(`Gradle args: ${gradleArgs.join(' ')}`);
    // fallback to Gradle (best-effort quiet console). still risky, but used only when jar missing.
    serverProc = spawn(gradle, ['--quiet', '--console=plain', ...gradleArgs], {
      cwd: workspaceRoot,
      shell: false,
      env
    });
  }

  serverProc.on('error', (err) => {
    output.appendLine('[kafkasql-lsp][error] Failed to spawn language server: ' + err.message);
    vscode.window.showErrorMessage('Failed to spawn language server: ' + err.message);
  });

  serverProc.stderr?.on('data', (chunk) => {
    // surface server stderr to output channel
    output.appendLine('[kafkasql-lsp][stderr] ' + chunk.toString());
    console.error('[kafkasql-lsp] ' + chunk.toString());
  });

  serverProc.stdout?.on('data', (chunk) => {
    output.appendLine('[kafkasql-lsp][stdout] ' + chunk.toString());
  });

  serverProc.on('exit', (code, signal) => {
    output.appendLine(`[kafkasql-lsp] gradle process exited with code=${code} signal=${signal}`);
  });

  // prepare LanguageClient over stdio streams
  const serverOptions = (): Promise<StreamInfo> =>
    Promise.resolve({ reader: serverProc!.stdout as NodeJS.ReadableStream, writer: serverProc!.stdin as NodeJS.WritableStream });

  const clientOptions = {
    documentSelector: [{ scheme: 'file', language: 'kafkasql' }],
    outputChannelName: 'KafkaSQL LSP'
  };

  client = new LanguageClient('kafkasql', 'KafkaSQL Language Server', serverOptions, clientOptions);
  context.subscriptions.push(client);
  client.start();
  vscode.window.showInformationMessage('KafkaSQL language server started');
}

export function activate(context: vscode.ExtensionContext) {
  context.subscriptions.push(
    vscode.commands.registerCommand('kafkasql.startServer', () => startServer(context))
  );

  // attempt auto-start if a workspace is open
  startServer(context).catch(err => {
    console.error('Failed to start KafkaSQL LSP:', err);
  });
}

export function deactivate(): Thenable<void> | undefined {
  if (client) {
    const stopPromise = client.stop();
    client = null;
    if (serverProc) {
      try { serverProc.kill(); } catch { /* ignore */ }
      serverProc = null;
    }
    return stopPromise;
  }
  return undefined;
}