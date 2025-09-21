import * as vscode from 'vscode';
import { spawn, ChildProcess } from 'child_process';
import * as path from 'path';
import { LanguageClient, StreamInfo } from 'vscode-languageclient/node';
import * as fs from 'fs';
import os = require('os');

let client: LanguageClient | null = null;
let serverProc: ChildProcess | null = null;
let includeDiagnostics: vscode.DiagnosticCollection;

function findBuiltServerJar(workspaceRoot: string): string | null {
  try {
    // walk upwards from workspaceRoot looking for lsp/build/libs/*.jar
    let cur = workspaceRoot;
    while (true) {
      const libsDir = path.join(cur, 'lsp', 'build', 'libs');
      if (fs.existsSync(libsDir)) {
        const files = fs.readdirSync(libsDir).filter(f => f.endsWith('.jar'));
        if (files.length > 0) {
          // prefer shadow/all/fat jar if present
          const pref = files.find(f => /shadow|all|fat/i.test(f));
          const chosen = pref || files[0];
          return path.join(libsDir, chosen);
        }
      }
      const parent = path.dirname(cur);
      if (parent === cur) break;
      cur = parent;
    }
    return null;
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
  const projectRoot = openedFolder;
  if (projectRoot !== openedFolder) {
    // opened a subfolder (examples). prefer the detected Gradle project root
    vscode.window.showInformationMessage(`Using Gradle project root: ${projectRoot} (opened folder: ${openedFolder})`);
  }
  const dir = path.dirname(openedFolder);
  const wsFolders = vscode.workspace.workspaceFolders;
  const workspaceRoot = wsFolders && wsFolders.length > 0 ? wsFolders[0].uri.fsPath : dir;

  // create output channel for server logs
  const output = vscode.window.createOutputChannel('KafkaSQL LSP');
  context.subscriptions.push(output);

  // prefer the project/toolchain JAVA_HOME if available; fall back to process.env
  const defaultJavaHome = process.env.JAVA_HOME || '/opt/homebrew/Cellar/openjdk@21/21.0.8/libexec/openjdk.jdk/Contents/Home';
  const env = Object.assign({}, process.env, {
    JAVA_HOME: defaultJavaHome
  });

  output.appendLine(`Using JAVA_HOME=${env.JAVA_HOME}`);
  output.show(true);

  // Prefer running the built language-server jar directly. Do not run Gradle/build tasks from the extension.
  const serverJar = findBuiltServerJar(workspaceRoot);
  if (!serverJar) {
    const msg = 'Language server jar not found. Please build the project (produce lsp/build/libs/*.jar) and retry.';
    output.appendLine(`[kafkasql-lsp][error] ${msg}`);
    vscode.window.showErrorMessage(msg);
    return;
  }

  output.appendLine(`Launching language server jar: ${serverJar}`);
  const javaHome = env.JAVA_HOME || process.env.JAVA_HOME || '';
  const javaBin = javaHome ? path.join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
  const javaExe = fs.existsSync(javaBin) ? javaBin : 'java';
  serverProc = spawn(javaExe, ['-jar', serverJar], { cwd: workspaceRoot, shell: false, env });

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
    output.appendLine(`[kafkasql-lsp] language server process exited with code=${code} signal=${signal}`);
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

async function collectAllIncludes(entryPath: string, workspaceRoot: string, seen = new Set<string>()): Promise<string[]> {
  const absPath = path.isAbsolute(entryPath) ? entryPath : path.join(workspaceRoot, entryPath);
  if (seen.has(absPath) || !fs.existsSync(absPath)) return [];
  seen.add(absPath);

  const text = fs.readFileSync(absPath, 'utf8');
  const includeRegex = /^\s*include\s+['"](.+?)['"]/gim;
  let match: RegExpExecArray | null;
  let allFiles = [absPath];

  while ((match = includeRegex.exec(text))) {
    const incPath = match[1];
    const incFiles = await collectAllIncludes(incPath, workspaceRoot, seen);
    allFiles = allFiles.concat(incFiles);
  }
  return allFiles;
}

// ...or KafkaSqlTextDocumentService.java where you map core diagnostics...
// example snippet for the Java LSP server file:
// for (DiagnosticEntry e : coreDiagnostics.errorEntries()) {
//   String src = e.source();
//   Path p = workspace.resolve(src); // resolve relative -> absolute
//   String uri = p.toUri().toString();
//   int ln = Math.max(0, e.line() - 1);
//   int ch = Math.max(0, e.column() - 1);
//   Range r = new Range(new Position(ln, ch), new Position(ln, ch));
//   Diagnostic d = new Diagnostic(r, e.message(), DiagnosticSeverity.Error, "core");
//   byUri.computeIfAbsent(uri, k -> new ArrayList<>()).add(d);
// }

export function activate(context: vscode.ExtensionContext) {
  includeDiagnostics = vscode.languages.createDiagnosticCollection('kafkasql-includes');
  context.subscriptions.push(includeDiagnostics);

  context.subscriptions.push(
    vscode.commands.registerCommand('kafkasql.startServer', () => startServer(context))
  );

  const out = vscode.window.createOutputChannel('KafkaSQL Debug');
  context.subscriptions.push(out);

  // log diagnostics that VS Code receives (helpful to verify message matching)
  const diagListener = vscode.languages.onDidChangeDiagnostics((e) => {
    for (const uri of e.uris ? e.uris : [/*no uris provided in older APIs*/]) {
      const ds = vscode.languages.getDiagnostics(uri);
      out.appendLine(`[ext] diagnostics changed for ${uri.toString()} -> ${ds.length} entries`);
      for (const d of ds) {
        out.appendLine(`[ext] ${uri.toString()}: ${d.range.start.line + 1}:${d.range.start.character + 1} ${d.severity} ${d.source} ${d.message}`);
      }
    }
    // also log currently active editor URI for quick compare
    const active = vscode.window.activeTextEditor;
    if (active) out.appendLine(`[ext] active editor: ${active.document.uri.toString()}`);
  });
  context.subscriptions.push(diagListener);

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
