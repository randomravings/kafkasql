import * as vscode from 'vscode';
import * as path from 'path';
import { LanguageClient, LanguageClientOptions, ServerOptions, TransportKind, ErrorAction, CloseAction, RevealOutputChannelOn } from 'vscode-languageclient/node';
import * as fs from 'fs';
import { KafkaSqlProjectExplorer, ConnectionNode, ProjectNode } from './kafkaSqlProjectExplorer';

let client: LanguageClient | null = null;
let lspOutput: vscode.OutputChannel;   // module-level so startServer + commands can share it
let resultsChannel: vscode.OutputChannel;
let includeDiagnostics: vscode.DiagnosticCollection;
let diffDiagnostics: vscode.DiagnosticCollection;
let clusterDiffDiagnostics: vscode.DiagnosticCollection;

// URI stored by "Select for Compare"
let compareBaseUri: vscode.Uri | undefined;

// ── Comparison mode state ────────────────────────────────────────────────────
type ComparisonMode = 'none' | 'git';
let currentMode: ComparisonMode = 'git';
let modeStatusBar: vscode.StatusBarItem;

// ── File mode status bar ────────────────────────────────────────────────────
let fileModeStatusBar: vscode.StatusBarItem;
let executionStatusBar: vscode.StatusBarItem;

// Per-file mode overrides set by the user: uri.toString() → 'file' | 'interactive'
// Absent = auto-detect from file path.
const fileModeOverrides = new Map<string, 'file' | 'interactive'>();

// Connections pinned to a specific document (opened via "New Query" from the sidebar).
interface PinnedConnection { connectionName: string; projectFile: string; bootstrapServers: string; }
const pinnedConnections = new Map<string, PinnedConnection>();

const TOML_UNSAFE_CHARS = /["\\\r\n]/;

function validateTomlSafe(fieldName: string, value: string, required = true): string | null {
  const trimmed = value.trim();
  if (required && !trimmed) return 'Required';
  if (trimmed && TOML_UNSAFE_CHARS.test(trimmed)) {
    return `${fieldName} cannot contain quotes, backslashes, or newlines`;
  }
  return null;
}

function maybeWarnOnStoredCredentials(fields: ConnectionFormFields): void {
  if (fields.username && fields.password) {
    vscode.window.showWarningMessage(
      'KafkaSQL: credentials are stored in plaintext in connections.toml. Use local-only files and keep them out of source control.');
  }
}

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

async function startServer(context: vscode.ExtensionContext, onReady?: () => void) {
  if (client) {
    lspOutput.appendLine('[kafkasql-lsp] server already running');
    return;
  }

  const ws = vscode.workspace.workspaceFolders?.[0];
  if (!ws) {
    lspOutput.appendLine('[kafkasql-lsp][error] No workspace folder open — cannot start language server.');
    return;
  }
  const workspaceRoot = ws.uri.fsPath;

  const serverJar = findBuiltServerJar(workspaceRoot);
  if (!serverJar) {
    const msg = 'Language server jar not found. Please build the project (produce lsp/build/libs/*.jar) and retry.';
    lspOutput.appendLine(`[kafkasql-lsp][error] ${msg}`);
    vscode.window.showErrorMessage(msg);
    return;
  }

  const defaultJavaHome = process.env.JAVA_HOME || '/opt/homebrew/Cellar/openjdk@21/21.0.8/libexec/openjdk.jdk/Contents/Home';
  const env = { ...process.env, JAVA_HOME: defaultJavaHome };
  const javaHome = env.JAVA_HOME || '';
  const javaBin  = javaHome ? path.join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
  const javaExe  = fs.existsSync(javaBin) ? javaBin : 'java';

  lspOutput.appendLine(`[kafkasql-lsp] Using JAVA_HOME=${env.JAVA_HOME}`);
  lspOutput.appendLine(`[kafkasql-lsp] Launching jar: ${serverJar}`);

  // Let vscode-languageclient own the process lifecycle — this avoids the
  // manual-spawn race conditions and routes server stderr to lspOutput.
  const serverOptions: ServerOptions = {
    run:   { command: javaExe, args: ['-jar', serverJar], transport: TransportKind.stdio, options: { cwd: workspaceRoot, env } },
    debug: { command: javaExe, args: ['-jar', serverJar], transport: TransportKind.stdio, options: { cwd: workspaceRoot, env } },
  };

  const clientOptions: LanguageClientOptions = {
    documentSelector: [{ scheme: 'file', language: 'kafkasql' }],
    outputChannel: lspOutput,
    revealOutputChannelOn: RevealOutputChannelOn.Never,
    errorHandler: {
      error(error, _message, _count) {
        lspOutput.appendLine(`[kafkasql-lsp][error] ${error?.message ?? String(error)}`);
        return { action: ErrorAction.Continue, handled: true };
      },
      closed() {
        lspOutput.appendLine('[kafkasql-lsp] Server connection closed.');
        return { action: CloseAction.DoNotRestart, handled: true };
      },
    },
  };

  client = new LanguageClient('kafkasql', 'KafkaSQL Language Server', serverOptions, clientOptions);
  context.subscriptions.push(client);

  client.start().then(() => {
    lspOutput.appendLine('[kafkasql-lsp] Server ready.');
    sendModeToLsp(currentMode);

    // Stream individual records to the output channel as they arrive from the server.
    client!.onNotification('kafkasql/record', (row: { typeName: string; fields: Record<string, unknown> }) => {
      const fieldStr = Object.entries(row.fields)
        .map(([k, v]) => `${k}: ${JSON.stringify(v)}`)
        .join(', ');
      resultsChannel.appendLine(`  ${row.typeName} { ${fieldStr} }`);
    });

    onReady?.();
  }).catch((err: unknown) => {
    lspOutput.appendLine(`[kafkasql-lsp][error] Client start failed: ${err}`);
    client = null;
  });
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

// ── Semantic diff types (mirror of Java DiffEntry serialized by Gson) ────────

interface DiffPos   { ln: number; ch: number; }
interface DiffRange { source: string; from: DiffPos; to: DiffPos; }
interface DiffEntryResponse {
  kind: string;       // LEFT_ONLY | RIGHT_ONLY | MODIFIED | UNCHANGED
  severity: string;   // BREAKING | WARNING | SAFE | INFO
  leftRange:  DiffRange | null;
  rightRange: DiffRange | null;
  aspect: string;
  message: string;
}

/** Call the LSP semanticDiff command and show results as diagnostics on rightUri. */
async function runSemanticDiff(leftUri: vscode.Uri, rightUri: vscode.Uri): Promise<void> {
  if (!client) return;
  diffDiagnostics.delete(rightUri);
  try {
    const result = await client.sendRequest('workspace/executeCommand', {
      command: 'kafkasql.semanticDiff',
      arguments: [leftUri.fsPath, rightUri.fsPath]
    });

    if (!Array.isArray(result)) {
      if (result && typeof result === 'object' && 'error' in (result as object)) {
        vscode.window.showErrorMessage(`KafkaSQL diff error: ${(result as { error: string }).error}`);
      }
      return;
    }

    const entries = result as DiffEntryResponse[];
    const diags: vscode.Diagnostic[] = [];

    for (const entry of entries) {
      // For removed items (LEFT_ONLY) there is no rightRange; show at top of right file
      const rng = entry.rightRange ?? entry.leftRange;
      if (!rng) continue;

      // Java Pos uses 1-based lines; VS Code uses 0-based
      const start = new vscode.Position(Math.max(0, rng.from.ln - 1), Math.max(0, rng.from.ch));
      const end   = new vscode.Position(Math.max(0, rng.to.ln - 1),   Math.max(0, rng.to.ch));
      const diag  = new vscode.Diagnostic(new vscode.Range(start, end), entry.message, mapSeverity(entry.severity));
      diag.source = 'kafkasql-diff';
      diag.code   = entry.aspect;
      diags.push(diag);
    }

    diffDiagnostics.set(rightUri, diags);

    if (diags.length === 0) {
      vscode.window.showInformationMessage('KafkaSQL: no compatibility issues detected');
    } else {
      const breaking = diags.filter(d => d.severity === vscode.DiagnosticSeverity.Error).length;
      const msg = breaking > 0
        ? `KafkaSQL: ${breaking} breaking change(s), ${diags.length} total — see Problems panel`
        : `KafkaSQL: ${diags.length} change(s) — see Problems panel`;
      vscode.window.showInformationMessage(msg);
    }
  } catch (err: unknown) {
    console.error('KafkaSQL semanticDiff failed:', err);
  }
}

function mapSeverity(s: string): vscode.DiagnosticSeverity {
  switch (s) {
    case 'BREAKING': return vscode.DiagnosticSeverity.Error;
    case 'WARNING':  return vscode.DiagnosticSeverity.Warning;
    case 'SAFE':     return vscode.DiagnosticSeverity.Information;
    default:         return vscode.DiagnosticSeverity.Hint;
  }
}

function modeLabel(mode: ComparisonMode): string {
  switch (mode) {
    case 'none': return '$(circle-slash) Compat: Off';
    case 'git':  return '$(git-compare) Compat: Git HEAD';
  }
}

async function sendModeToLsp(mode: ComparisonMode): Promise<void> {
  if (!client) return;
  try {
    await client.sendRequest('workspace/executeCommand', {
      command: 'kafkasql.setComparisonMode',
      arguments: [mode.toUpperCase(), null]
    });
  } catch (err) {
    lspOutput?.appendLine(`[kafkasql-lsp] setComparisonMode failed: ${err}`);
  }
}

// Quick-pick items carry the mode in the description tag so we can avoid
// depending on QuickPickItem.detail which was missing in older vscode API types.
interface ModeItem extends vscode.QuickPickItem {
  mode: ComparisonMode;
}

async function pickComparisonMode(): Promise<void> {
  const items: ModeItem[] = [
    { label: '$(git-compare) Git HEAD',    description: 'Compare against the last committed version', mode: 'git'  },
    { label: '$(circle-slash) Off',        description: 'Disable compatibility checking',             mode: 'none' },
  ];

  const picked = await vscode.window.showQuickPick<ModeItem>(items, {
    placeHolder: 'KafkaSQL: select compatibility check mode'
  });
  if (!picked) return;

  const mode = picked.mode;

  currentMode = mode;
  modeStatusBar.text = modeLabel(currentMode);
  await sendModeToLsp(currentMode);

  vscode.window.showInformationMessage(
    `KafkaSQL compatibility mode: ${modeStatusBar.text.replace(/\$\([^)]+\) /, '')}`
  );
}

/** Walks upward from `startDir` looking for a `connections.toml` file. */
function findConnectionsToml(startDir: string): string | null {
  let dir = startDir;
  while (true) {
    const candidate = path.join(dir, 'connections.toml');
    if (fs.existsSync(candidate)) return candidate;
    const parent = path.dirname(dir);
    if (parent === dir) return null;
    dir = parent;
  }
}

interface ConnectionEntry { name: string; bootstrapServers: string; topic: string; username?: string; password?: string; }

/** Parses the TOML subset used by connections.toml. */
function parseConnectionsToml(tomlPath: string): ConnectionEntry[] {
  const lines = fs.readFileSync(tomlPath, 'utf8').split('\n');
  const result: ConnectionEntry[] = [];
  let current: Partial<ConnectionEntry> | null = null;

  const flush = () => {
    if (current?.name && current.bootstrapServers && current.topic) {
      result.push(current as ConnectionEntry);
    }
    current = null;
  };

  for (const raw of lines) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const headerMatch = line.match(/^\[connection\.(.+)\]$/);
    if (headerMatch) { flush(); current = { name: headerMatch[1] }; continue; }
    if (!current) continue;
    const kvMatch = line.match(/^(\w+)\s*=\s*"([^"]*)"/)
                 || line.match(/^(\w+)\s*=\s*([^#\s]+)/);
    if (!kvMatch) continue;
    const [, k, v] = kvMatch;
    if (k === 'bootstrap') current.bootstrapServers = v;
    if (k === 'topic')     current.topic = v;
    if (k === 'username')  current.username = v;
    if (k === 'password')  current.password = v;
  }
  flush();
  return result;
}

// ── connections.toml write helpers ───────────────────────────────────────────

/** Escape a string value for use inside a TOML basic (double-quoted) string. */
function tomlEscape(value: string): string {
  return value
    .replace(/\\/g, '\\\\')
    .replace(/"/g, '\\"')
    .replace(/\n/g, '\\n')
    .replace(/\r/g, '\\r')
    .replace(/\t/g, '\\t')
    .replace(/[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]/g, c =>
      `\\u${c.codePointAt(0)!.toString(16).padStart(4, '0')}`
    );
}

/** Serialize a single [connection.*] block (no trailing newline). */
function serializeConnectionBlock(
  name: string, bootstrap: string, topic: string,
  username?: string, password?: string,
): string {
  const lines = [`[connection.${name}]`];
  lines.push(`bootstrap = "${tomlEscape(bootstrap)}"`);
  lines.push(`topic     = "${tomlEscape(topic)}"`);
  if (username) { lines.push(`username  = "${tomlEscape(username)}"`); }
  if (password) { lines.push(`password  = "${tomlEscape(password)}"`); }
  return lines.join('\n');
}

/**
 * Remove a [connection.<name>] section (and any blank lines directly before it)
 * from raw TOML text. Returns the original text unchanged if the section is not found.
 */
function removeConnectionBlock(tomlText: string, name: string): string {
  const lines = tomlText.split('\n');
  const header = `[connection.${name}]`;
  let start = -1;
  let end = lines.length;
  for (let i = 0; i < lines.length; i++) {
    const t = lines[i].trim();
    if (t === header) {
      start = i;
    } else if (start >= 0 && i > start && t.startsWith('[')) {
      end = i;
      break;
    }
  }
  if (start < 0) return tomlText;
  // Eat blank lines that immediately precede the section header
  let removeFrom = start;
  while (removeFrom > 0 && lines[removeFrom - 1].trim() === '') { removeFrom--; }
  lines.splice(removeFrom, end - removeFrom);
  return lines.join('\n');
}

/** Replace an existing [connection.<name>] block, or append it if not present. */
function upsertConnectionBlock(tomlText: string, name: string, block: string): string {
  const cleaned = removeConnectionBlock(tomlText, name);
  const base = cleaned.trimEnd();
  if (!base) return block + '\n';
  return base + '\n\n' + block + '\n';
}

interface ConnectionFormFields {
  name: string; bootstrap: string; topic: string; username: string; password: string;
}

/** Multi-step input box sequence to collect / edit connection fields. */
async function promptConnectionFields(
  defaults?: Partial<ConnectionFormFields>,
  nameReadonly = false,
): Promise<ConnectionFormFields | undefined> {
  let name: string;
  if (nameReadonly) {
    name = defaults?.name ?? '';
  } else {
    const v = await vscode.window.showInputBox({
      title: 'KafkaSQL — Connection name',
      prompt: 'Unique identifier (letters, digits, hyphens, dots, underscores)',
      value: defaults?.name ?? '',
      validateInput: s => /^[a-zA-Z0-9_.-]+$/.test(s.trim()) ? null : 'Use letters, digits, hyphens, dots, or underscores',
    });
    if (v === undefined) return undefined;
    name = v.trim();
  }

  const bootstrap = await vscode.window.showInputBox({
    title: 'KafkaSQL — Bootstrap servers',
    prompt: 'Kafka bootstrap server(s), comma-separated (e.g. localhost:9092)',
    value: defaults?.bootstrap ?? 'localhost:9092',
    validateInput: s => validateTomlSafe('Bootstrap servers', s),
  });
  if (bootstrap === undefined) return undefined;

  const topic = await vscode.window.showInputBox({
    title: 'KafkaSQL — Event-log topic',
    prompt: 'KafkaSQL event-log topic name',
    value: defaults?.topic ?? '_kafkasql_log',
    validateInput: s => validateTomlSafe('Topic', s),
  });
  if (topic === undefined) return undefined;

  const username = await vscode.window.showInputBox({
    title: 'KafkaSQL — Username (optional)',
    prompt: 'SASL/SCRAM-SHA-256 username — leave empty for PLAINTEXT',
    value: defaults?.username ?? '',
    validateInput: s => validateTomlSafe('Username', s, false),
  });
  if (username === undefined) return undefined;

  let password = '';
  if (username.trim()) {
    const pw = await vscode.window.showInputBox({
      title: 'KafkaSQL — Password',
      prompt: 'SASL/SCRAM-SHA-256 password',
      value: defaults?.password ?? '',
      password: true,
      validateInput: s => validateTomlSafe('Password', s),
    });
    if (pw === undefined) return undefined;
    password = pw;
  }

  return { name, bootstrap: bootstrap.trim(), topic: topic.trim(), username: username.trim(), password };
}

/** Walks upward from `startDir` looking for the first `*.proj.toml` file. */
function findProjToml(startDir: string): string | null {
  let dir = startDir;
  while (true) {
    try {
      const entries = fs.readdirSync(dir);
      const found = entries.find(e => e.endsWith('.proj.toml'));
      if (found) return path.join(dir, found);
    } catch { /* unreadable dir */ }
    const parent = path.dirname(dir);
    if (parent === dir) return null;
    dir = parent;
  }
}

/** Reads the `kafka` key from the `[project]` section of a `.proj.toml` file. */
function readKafkaDirFromProjToml(tomlPath: string): string {
  const lines = fs.readFileSync(tomlPath, 'utf8').split('\n');
  let kafkaDir = 'model'; // project default
  let inProjectSection = false;
  for (const raw of lines) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    if (line.startsWith('[')) {
      const section = line.replace(/^\[/, '').replace(/\].*$/, '').trim();
      inProjectSection = section === 'project';
      continue;
    }
    if (!inProjectSection) continue;
    const eq = line.indexOf('=');
    if (eq < 0) continue;
    const key = line.substring(0, eq).trim().toLowerCase();
    if (key === 'kafka') {
      let val = line.substring(eq + 1).trim();
      if (val.startsWith('"')) {
        const close = val.indexOf('"', 1);
        val = close >= 0 ? val.substring(1, close) : val.substring(1);
      } else {
        const ci = val.indexOf('#');
        if (ci >= 0) val = val.substring(0, ci).trim();
      }
      kafkaDir = val;
    }
  }
  return kafkaDir;
}

/**
 * Returns `true` when the file should be treated as interactive mode.
 * Priority: explicit per-file override → path-based auto-detection.
 */
function isInteractiveMode(filePath: string, fileDir: string, uriKey?: string): boolean {
  if (uriKey && fileModeOverrides.has(uriKey)) {
    return fileModeOverrides.get(uriKey) === 'interactive';
  }
  const projTomlPath = findProjToml(fileDir);
  if (!projTomlPath) return false; // no project → file mode
  const projDir  = path.dirname(projTomlPath);
  const kafkaDir = readKafkaDirFromProjToml(projTomlPath);
  const modelRoot = path.normalize(path.join(projDir, kafkaDir));
  const normalized = path.normalize(filePath);
  return !normalized.startsWith(modelRoot + path.sep) && normalized !== modelRoot;
}

export function activate(context: vscode.ExtensionContext) {
  // Create the output channel first — used by startServer and command handlers.
  lspOutput = vscode.window.createOutputChannel('KafkaSQL LSP');
  context.subscriptions.push(lspOutput);

  resultsChannel = vscode.window.createOutputChannel('KafkaSQL');
  context.subscriptions.push(resultsChannel);

  includeDiagnostics = vscode.languages.createDiagnosticCollection('kafkasql-includes');
  context.subscriptions.push(includeDiagnostics);

  diffDiagnostics = vscode.languages.createDiagnosticCollection('kafkasql-diff');
  context.subscriptions.push(diffDiagnostics);

  clusterDiffDiagnostics = vscode.languages.createDiagnosticCollection('kafkasql-cluster-diff');
  context.subscriptions.push(clusterDiffDiagnostics);

  // ── Register commands (idempotent — safe to call again after unclean host restart) ─
  // vscode.commands.registerCommand throws if a command id is already registered.
  // That can happen when the extension host crashes without calling deactivate().
  // We catch the error so the rest of activate() proceeds normally.
  function reg(id: string, handler: (...args: any[]) => any): void {
    try {
      context.subscriptions.push(vscode.commands.registerCommand(id, handler));
    } catch {
      lspOutput.appendLine(`[kafkasql-ext] Note: command '${id}' already registered (stale from previous session); handler not updated until next full reload.`);
    }
  }

  reg('kafkasql.startServer', () => startServer(context));

  // ── Compare commands ──────────────────────────────────────────────────────

  // Step 1: right-click a .kafka file → remember it as the left side
  reg('kafkasql.selectForCompare', (uri: vscode.Uri) => {
      compareBaseUri = uri;
      // Expose the context key so the "Compare with Selected" menu item becomes visible
      vscode.commands.executeCommand('setContext', 'kafkasql.compareBase', true);
      vscode.window.showInformationMessage(
        `KafkaSQL: selected ${path.basename(uri.fsPath)} as compare base`
      );
  });

  // Step 2: right-click another .kafka file → open native diff editor
  reg('kafkasql.compareWithSelected', (uri: vscode.Uri) => {
      if (!compareBaseUri) {
        vscode.window.showErrorMessage('KafkaSQL: no base file selected — use "Select for Compare" first');
        return;
      }
      const left  = compareBaseUri;
      const right = uri;
      const title = `${path.basename(left.fsPath)} ↔ ${path.basename(right.fsPath)}`;
      vscode.commands.executeCommand('vscode.diff', left, right, title);
      runSemanticDiff(left, right);
  });

  // Bonus: from the active editor, open a file picker to choose the right side
  reg('kafkasql.compareWithActive', async (uri?: vscode.Uri) => {
      const leftUri = uri ?? vscode.window.activeTextEditor?.document.uri;
      if (!leftUri) {
        vscode.window.showErrorMessage('KafkaSQL: no active file to compare');
        return;
      }
      const picks = await vscode.window.showOpenDialog({
        canSelectMany: false,
        filters: { 'KafkaSQL files': ['kafka', 'kafkasql'] }
      });
      if (!picks || picks.length === 0) return;
      const rightUri = picks[0];
      const title = `${path.basename(leftUri.fsPath)} ↔ ${path.basename(rightUri.fsPath)}`;
      vscode.commands.executeCommand('vscode.diff', leftUri, rightUri, title);
      runSemanticDiff(leftUri, rightUri);
  });

  // ── Comparison mode status bar ─────────────────────────────────────────────
  modeStatusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
  modeStatusBar.command = 'kafkasql.changeComparisonMode';
  modeStatusBar.tooltip  = 'KafkaSQL: click to change compatibility check mode';
  modeStatusBar.text     = modeLabel(currentMode);
  modeStatusBar.show();
  context.subscriptions.push(modeStatusBar);

  reg('kafkasql.changeComparisonMode', () => pickComparisonMode());
  // ── LSP command helper ──────────────────────────────────────────────────
  const sendLspCommand = (cmd: string, args: any[]): Promise<any> => {
    if (!client) return Promise.reject(new Error('LSP not ready'));
    return client.sendRequest('workspace/executeCommand', { command: cmd, arguments: args });
  };
  // ── File mode status bar ────────────────────────────────────────────────────
  // Shows whether the active .kafka file is in File mode or Interactive mode.
  // Click to override the auto-detected mode for this file.
  fileModeStatusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 101);
  fileModeStatusBar.command = 'kafkasql.pickFileMode';
  context.subscriptions.push(fileModeStatusBar);

  // ── Execution stop status bar ──────────────────────────────────────────────
  executionStatusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 102);
  executionStatusBar.command = 'kafkasql.cancelExecution';
  executionStatusBar.text = '$(stop-circle) Stop';
  executionStatusBar.tooltip = 'KafkaSQL: cancel the running query';
  executionStatusBar.backgroundColor = new vscode.ThemeColor('statusBarItem.warningBackground');
  context.subscriptions.push(executionStatusBar);

  function updateFileModeStatusBar(editor: vscode.TextEditor | undefined): void {
    const doc = editor?.document;
    const isKafka = doc && (doc.languageId === 'kafkasql'
      || doc.fileName.endsWith('.kafka')
      || doc.fileName.endsWith('.kafkasql'));
    if (!isKafka) {
      fileModeStatusBar.hide();
      return;
    }
    const uriKey = doc.uri.toString();
    const fileDir = path.dirname(doc.fileName);
    const overridden = fileModeOverrides.has(uriKey);
    const interactive = isInteractiveMode(doc.fileName, fileDir, uriKey);
    const pin = overridden ? ' $(pinned)' : '';
    if (interactive) {
      fileModeStatusBar.text    = `$(symbol-event) Interactive${pin}`;
      fileModeStatusBar.tooltip = `KafkaSQL: Interactive mode${overridden ? ' (manually set)' : ' (auto-detected)'}\n`
        + 'INCLUDE statements are ignored; types are resolved from the live Kafka cluster.\n'
        + 'Click to change.';
    } else {
      fileModeStatusBar.text    = `$(file-code) File mode${pin}`;
      fileModeStatusBar.tooltip = `KafkaSQL: File mode${overridden ? ' (manually set)' : ' (auto-detected)'}\n`
        + 'Types are resolved from local model sources.\n'
        + 'Click to change.';
    }
    fileModeStatusBar.show();
  }

  reg('kafkasql.pickFileMode', async () => {
    const editor = vscode.window.activeTextEditor;
    if (!editor) return;
    const doc = editor.document;
    const isKafka = doc.languageId === 'kafkasql'
      || doc.fileName.endsWith('.kafka')
      || doc.fileName.endsWith('.kafkasql');
    if (!isKafka) return;

    const uriKey = doc.uri.toString();
    const fileDir = path.dirname(doc.fileName);
    const currentInteractive = isInteractiveMode(doc.fileName, fileDir, uriKey);
    const isOverridden = fileModeOverrides.has(uriKey);

    interface ModeItem extends vscode.QuickPickItem { value: 'auto' | 'file' | 'interactive'; }
    const items: ModeItem[] = [
      {
        label: '$(symbol-event) Interactive mode',
        description: 'Semantic model backed by the live Kafka cluster',
        detail: currentInteractive && !isOverridden ? '$(check) current (auto-detected)' : (currentInteractive && isOverridden ? '$(check) current (manually set)' : undefined),
        value: 'interactive',
      },
      {
        label: '$(file-code) File mode',
        description: 'Types resolved from local model sources',
        detail: !currentInteractive && !isOverridden ? '$(check) current (auto-detected)' : (!currentInteractive && isOverridden ? '$(check) current (manually set)' : undefined),
        value: 'file',
      },
      {
        label: '$(sync) Auto-detect',
        description: 'Infer from file location (model root = File, misc/ = Interactive)',
        detail: !isOverridden ? '$(check) current' : undefined,
        value: 'auto',
      },
    ];

    const picked = await vscode.window.showQuickPick<ModeItem>(items, {
      placeHolder: `KafkaSQL mode for ${path.basename(doc.fileName)}`,
    });
    if (!picked) return;

    if (picked.value === 'auto') {
      fileModeOverrides.delete(uriKey);
    } else {
      fileModeOverrides.set(uriKey, picked.value);
    }
    updateFileModeStatusBar(editor);

    // Also update the execute-statement mode so it matches what diagnostics are using.
    // Tell the LSP to re-run diagnostics with the new mode.
    if (client) {
      try {
        await sendLspCommand('kafkasql.setFileMode', [uriKey, picked.value]);
      } catch (err) {
        lspOutput?.appendLine(`[kafkasql-ext] setFileMode failed: ${err}`);
      }
    }
  });

  updateFileModeStatusBar(vscode.window.activeTextEditor);
  context.subscriptions.push(
    vscode.window.onDidChangeActiveTextEditor(updateFileModeStatusBar)
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

  // ── Project explorer tree view ───────────────────────────────────────────────────
  // sendLspCommand is defined above (before the file mode status bar) so that
  // the pickFileMode command handler can use it.
  const explorer = new KafkaSqlProjectExplorer(context, sendLspCommand);
  const treeView = vscode.window.createTreeView('kafkasqlProjectExplorer', {
    treeDataProvider: explorer,
    showCollapseAll: false,
  });
  context.subscriptions.push(treeView);

  // Watch connections.toml files and refresh the explorer
  const connectionsWatcher = vscode.workspace.createFileSystemWatcher('**/connections.toml');
  const doExplorerRefresh = () => explorer.refresh().catch(console.error);
  connectionsWatcher.onDidChange(doExplorerRefresh);
  connectionsWatcher.onDidCreate(doExplorerRefresh);
  connectionsWatcher.onDidDelete(doExplorerRefresh);
  context.subscriptions.push(connectionsWatcher);

  reg('kafkasql.explorer.refresh', () => explorer.refresh());
  reg('kafkasql.explorer.toggleFlatContexts', () => explorer.toggleFlatContexts());
  reg('kafkasql.explorer.expandAll', async () => {
    for (const node of explorer.getProjectNodes()) {
      await treeView.reveal(node, { expand: 5 });
    }
  });
  reg('kafkasql.explorer.collapseAll', () => {
    vscode.commands.executeCommand('workbench.actions.treeView.kafkasqlProjectExplorer.collapseAll');
  });
  reg('kafkasql.explorer.openFile', (filePath: string, line: number) => {
    vscode.window.showTextDocument(vscode.Uri.file(filePath), {
      selection: new vscode.Range(line, 0, line, 0),
      preview: false,
    });
  });

  // ── Cluster diff / deploy commands ────────────────────────────────────────

  reg('kafkasql.diffWithCluster', async (node: ConnectionNode) => {
      if (!node) return;
      const picks = await vscode.window.showOpenDialog({
        canSelectMany: false,
        filters: { 'KafkaSQL files': ['kafka', 'kafkasql'] },
        title: `Pick local schema file to diff against cluster "${node.label}"`
      });
      if (!picks || picks.length === 0) return;
      const localUri = picks[0];
      clusterDiffDiagnostics.delete(localUri);

      let result: unknown;
      try {
        result = await sendLspCommand('kafkasql.diffWithCluster',
            [node.projectFile, node.label, localUri.fsPath]);
      } catch (err) {
        vscode.window.showErrorMessage(`Diff with cluster failed: ${err}`);
        return;
      }

      if (!Array.isArray(result)) {
        if (result && typeof result === 'object' && 'error' in (result as object)) {
          vscode.window.showErrorMessage(
              `Diff with cluster failed: ${(result as { error: string }).error}`);
        }
        return;
      }

      const entries = result as DiffEntryResponse[];
      const diags: vscode.Diagnostic[] = [];
      for (const entry of entries) {
        const rng = entry.rightRange ?? entry.leftRange;
        if (!rng) continue;
        const start = new vscode.Position(Math.max(0, rng.from.ln - 1), Math.max(0, rng.from.ch));
        const end   = new vscode.Position(Math.max(0, rng.to.ln - 1),   Math.max(0, rng.to.ch));
        const diag  = new vscode.Diagnostic(new vscode.Range(start, end), entry.message, mapSeverity(entry.severity));
        diag.source = 'kafkasql-cluster-diff';
        diag.code   = entry.aspect;
        diags.push(diag);
      }
      clusterDiffDiagnostics.set(localUri, diags);

      if (diags.length === 0) {
        vscode.window.showInformationMessage(
            `KafkaSQL: local schema matches cluster "${node.label}"`);
      } else {
        const breaking = diags.filter(d => d.severity === vscode.DiagnosticSeverity.Error).length;
        const msg = breaking > 0
            ? `KafkaSQL: ${breaking} breaking difference(s) vs cluster "${node.label}" — see Problems panel`
            : `KafkaSQL: ${diags.length} difference(s) vs cluster "${node.label}" — see Problems panel`;
        vscode.window.showInformationMessage(msg);
      }
    }),

    vscode.commands.registerCommand('kafkasql.deployToCluster', async (node: ConnectionNode) => {
      if (!node) return;
      const picks = await vscode.window.showOpenDialog({
        canSelectMany: false,
        filters: { 'KafkaSQL files': ['kafka', 'kafkasql'] },
        title: `Pick local schema file to deploy to cluster "${node.label}"`
      });
      if (!picks || picks.length === 0) return;
      const localUri = picks[0];

      // Run a quick diff first to show the change count in the confirmation dialog
      let diffCount = 0;
      try {
        const diffResult = await sendLspCommand('kafkasql.diffWithCluster',
            [node.projectFile, node.label, localUri.fsPath]);
        if (Array.isArray(diffResult)) {
          diffCount = (diffResult as DiffEntryResponse[])
              .filter(e => e.kind !== 'UNCHANGED').length;
        }
      } catch { /* ignore — deploy will surface any connection errors */ }

      const confirm = await vscode.window.showWarningMessage(
          `Deploy ${diffCount} change(s) to cluster "${node.label}" (${node.bootstrapServers})?`,
          { modal: true }, 'Deploy');
      if (confirm !== 'Deploy') return;

      let result: unknown;
      try {
        result = await sendLspCommand('kafkasql.deployToCluster',
            [node.projectFile, node.label, localUri.fsPath]);
      } catch (err) {
        vscode.window.showErrorMessage(`Deploy to cluster failed: ${err}`);
        return;
      }

      if (result && typeof result === 'object' && 'error' in (result as object)) {
        vscode.window.showErrorMessage(
            `Deploy to cluster failed: ${(result as { error: string }).error}`);
        return;
      }
      if (result && typeof result === 'object' && 'deployed' in (result as object)) {
        const r = result as { deployed: number; operations: string[] };
        vscode.window.showInformationMessage(
            `KafkaSQL: deployed ${r.deployed} event(s) to cluster "${node.label}"`);
      }
  });

  // ── Deploy project to a chosen connection ─────────────────────────────────
  // Right-click a ProjectNode → "Deploy to..." → pick connection → deploy all
  // kafka files in the project as a single atomic operation.

  reg('kafkasql.deployProjectToCluster', async (node: ProjectNode) => {
    if (!node) return;

    const connections = explorer.getConnectionsForProject(node.label);
    if (connections.length === 0) {
      vscode.window.showErrorMessage(
        `KafkaSQL: project "${node.label}" has no connections defined in connections.toml`);
      return;
    }

    interface ConnItem extends vscode.QuickPickItem { name: string; }
    const items: ConnItem[] = connections.map(c => ({
      label: c.name,
      description: c.bootstrapServers,
      detail: `topic: ${c.topic}`,
      name: c.name,
    }));

    const picked = await vscode.window.showQuickPick<ConnItem>(items, {
      placeHolder: `Deploy project "${node.label}" to which cluster?`,
      matchOnDescription: true,
    });
    if (!picked) return;

    // Dry-run diff to show change count in confirmation
    let diffCount = 0;
    try {
      const diffResult = await sendLspCommand('kafkasql.diffProjectWithCluster',
          [node.projectFile, picked.name]);
      if (Array.isArray(diffResult)) {
        diffCount = (diffResult as DiffEntryResponse[]).filter(e => e.kind !== 'UNCHANGED').length;
      }
    } catch { /* ignore — deploy will surface any real errors */ }

    const confirm = await vscode.window.showWarningMessage(
        `Deploy project "${node.label}" (${diffCount} change(s)) to cluster "${picked.name}" (${picked.description})?`,
        { modal: true }, 'Deploy');
    if (confirm !== 'Deploy') return;

    let result: unknown;
    try {
      result = await sendLspCommand('kafkasql.deployProjectToCluster',
          [node.projectFile, picked.name]);
    } catch (err) {
      vscode.window.showErrorMessage(`Deploy project to cluster failed: ${err}`);
      return;
    }

    if (result && typeof result === 'object' && 'error' in (result as object)) {
      vscode.window.showErrorMessage(
          `Deploy project to cluster failed: ${(result as { error: string }).error}`);
      return;
    }
    if (result && typeof result === 'object' && 'deployed' in (result as object)) {
      const r = result as { deployed: number; operations: string[] };
      vscode.window.showInformationMessage(
          `KafkaSQL: deployed ${r.deployed} event(s) from project "${node.label}" to cluster "${picked.name}"`);
    }
  });

  // ── New Query from sidebar connection ──────────────────────────────────────
  // Opens a new untitled KafkaSQL document pre-pinned to the chosen connection.
  reg('kafkasql.newQuery', async (node: ConnectionNode) => {
    if (!node) return;
    const doc = await vscode.workspace.openTextDocument({ language: 'kafkasql', content: '' });
    pinnedConnections.set(doc.uri.toString(), {
      connectionName: node.label,
      projectFile:    node.projectFile,
      bootstrapServers: node.bootstrapServers,
    });
    await vscode.window.showTextDocument(doc, { preview: false });
    vscode.window.setStatusBarMessage(
      `KafkaSQL: new query on "${node.label}" (${node.bootstrapServers})`, 4000);
  });

  context.subscriptions.push(vscode.workspace.onDidCloseTextDocument(doc => {
    pinnedConnections.delete(doc.uri.toString());
  }));

  context.subscriptions.push(vscode.workspace.onDidRenameFiles(event => {
    for (const file of event.files) {
      const existing = pinnedConnections.get(file.oldUri.toString());
      if (existing) {
        pinnedConnections.delete(file.oldUri.toString());
        pinnedConnections.set(file.newUri.toString(), existing);
      }
    }
  }));

  // ── Add Connection ────────────────────────────────────────────────────────
  // Can be invoked from the Clusters root toolbar button or from the command palette.
  reg('kafkasql.addConnection', async () => {
    // Find all project directories that have (or could have) a connections.toml
    const projTomlUris = await vscode.workspace.findFiles(
      '**/*.proj.toml', '{.git,node_modules,build,.gradle,dist}/**');

    let targetToml: string;
    if (projTomlUris.length === 0) {
      // No project files — write to workspace root
      const wsRoot = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
      if (!wsRoot) { vscode.window.showErrorMessage('KafkaSQL: no workspace folder open'); return; }
      targetToml = path.join(wsRoot, 'connections.toml');
    } else if (projTomlUris.length === 1) {
      targetToml = path.join(path.dirname(projTomlUris[0].fsPath), 'connections.toml');
    } else {
      interface ProjItem extends vscode.QuickPickItem { tomlPath: string; }
      const items: ProjItem[] = projTomlUris.map(u => ({
        label: path.basename(path.dirname(u.fsPath)),
        description: path.dirname(u.fsPath),
        tomlPath: path.join(path.dirname(u.fsPath), 'connections.toml'),
      }));
      const picked = await vscode.window.showQuickPick<ProjItem>(items, {
        placeHolder: 'Add connection to which project?',
      });
      if (!picked) return;
      targetToml = picked.tomlPath;
    }

    const fields = await promptConnectionFields();
    if (!fields) return;
    maybeWarnOnStoredCredentials(fields);

    const existing = fs.existsSync(targetToml) ? fs.readFileSync(targetToml, 'utf8') : '';
    // Guard against duplicate names
    if (existing.includes(`[connection.${fields.name}]`)) {
      vscode.window.showErrorMessage(`KafkaSQL: connection "${fields.name}" already exists in ${path.basename(targetToml)}`);
      return;
    }
    const block = serializeConnectionBlock(fields.name, fields.bootstrap, fields.topic, fields.username || undefined, fields.password || undefined);
    const updated = upsertConnectionBlock(existing, fields.name, block);
    fs.writeFileSync(targetToml, updated, 'utf8');
    vscode.window.showInformationMessage(`KafkaSQL: connection "${fields.name}" added to ${path.basename(targetToml)}`);
    await explorer.refresh();
  });

  // ── Edit Connection ───────────────────────────────────────────────────────
  reg('kafkasql.editConnection', async (node: ConnectionNode) => {
    if (!node) return;
    const tomlPath = findConnectionsToml(path.dirname(node.projectFile));
    if (!tomlPath) { vscode.window.showErrorMessage('KafkaSQL: connections.toml not found'); return; }

    const fields = await promptConnectionFields({
      name:      node.label,
      bootstrap: node.bootstrapServers,
      topic:     node.topic,
      username:  node.username ?? '',
      password:  node.password ?? '',
    }, /* nameReadonly */ true);
    if (!fields) return;
    maybeWarnOnStoredCredentials(fields);

    const raw     = fs.readFileSync(tomlPath, 'utf8');
    const block   = serializeConnectionBlock(fields.name, fields.bootstrap, fields.topic, fields.username || undefined, fields.password || undefined);
    const updated = upsertConnectionBlock(raw, fields.name, block);
    fs.writeFileSync(tomlPath, updated, 'utf8');
    vscode.window.showInformationMessage(`KafkaSQL: connection "${fields.name}" updated`);
    await explorer.refresh();
  });

  // ── Remove Connection ─────────────────────────────────────────────────────
  reg('kafkasql.removeConnection', async (node: ConnectionNode) => {
    if (!node) return;
    const confirmed = await vscode.window.showWarningMessage(
      `Remove connection "${node.label}" from connections.toml?`,
      { modal: true }, 'Remove');
    if (confirmed !== 'Remove') return;

    const tomlPath = findConnectionsToml(path.dirname(node.projectFile));
    if (!tomlPath) { vscode.window.showErrorMessage('KafkaSQL: connections.toml not found'); return; }

    const raw     = fs.readFileSync(tomlPath, 'utf8');
    const updated = removeConnectionBlock(raw, node.label);
    fs.writeFileSync(tomlPath, updated, 'utf8');
    vscode.window.showInformationMessage(`KafkaSQL: connection "${node.label}" removed`);
    await explorer.refresh();
  });

  // ── Cancel Execution ───────────────────────────────────────────────────────
  reg('kafkasql.cancelExecution', async () => {
    if (!client) return;
    await sendLspCommand('kafkasql.cancelExecution', []);
  });

  // ── Execute Statement ─────────────────────────────────────────────────────
  // Runs the selected text (or entire document) against a chosen connection.

  reg('kafkasql.executeStatement', async (uri?: vscode.Uri) => {
    const editor = vscode.window.activeTextEditor;
    const activeUri = uri ?? editor?.document.uri;
    if (!activeUri) {
      vscode.window.showErrorMessage('KafkaSQL: open a .kafka file first');
      return;
    }
    if (!client) {
      vscode.window.showErrorMessage('KafkaSQL: language server is not running');
      return;
    }

    // Get selected text or fall back to full document
    const doc = editor?.document ?? await vscode.workspace.openTextDocument(activeUri);
    const selection = editor?.selection;
    const text = (selection && !selection.isEmpty)
      ? doc.getText(selection)
      : doc.getText();

    if (!text.trim()) {
      vscode.window.showWarningMessage('KafkaSQL: nothing to execute');
      return;
    }

    // ── Resolve connection ────────────────────────────────────────────────────
    // Pinned connections are set when a file is opened via "New Query" from the sidebar.
    const pinned = pinnedConnections.get(activeUri.toString());
    let pickedName: string;
    let pickedDescription: string;
    let lspFilePath: string;
    let activeDir: string;

    if (pinned) {
      pickedName        = pinned.connectionName;
      pickedDescription = pinned.bootstrapServers;
      lspFilePath       = pinned.projectFile;
      activeDir         = path.dirname(pinned.projectFile);
    } else {
      // For unsaved (untitled) files there is no real path, so fall back to the workspace root.
      const isUntitled = activeUri.scheme === 'untitled';
      activeDir = isUntitled
        ? (vscode.workspace.workspaceFolders?.[0]?.uri.fsPath ?? path.dirname(activeUri.fsPath))
        : path.dirname(activeUri.fsPath);
      const tomlPath  = findConnectionsToml(activeDir);
      if (!tomlPath) {
        vscode.window.showErrorMessage('KafkaSQL: no connections.toml found — add one alongside your .proj.toml');
        return;
      }
      const connections = parseConnectionsToml(tomlPath);
      if (connections.length === 0) {
        vscode.window.showErrorMessage('KafkaSQL: connections.toml has no valid connections');
        return;
      }

      interface ConnItem extends vscode.QuickPickItem { name: string; }
      const items: ConnItem[] = connections.map(c => ({
        label: c.name,
        description: c.bootstrapServers,
        detail: `topic: ${c.topic}`,
        name: c.name,
      }));

      const picked = connections.length === 1
        ? items[0]
        : await vscode.window.showQuickPick<ConnItem>(items, {
            placeHolder: 'Execute against which cluster?',
            matchOnDescription: true,
          });
      if (!picked) return;

      pickedName        = picked.name;
      pickedDescription = picked.description ?? '';
      lspFilePath       = isUntitled
        ? (vscode.workspace.workspaceFolders?.[0]?.uri.fsPath ?? '')
        : activeUri.fsPath;
    }

    resultsChannel.show(true);
    const isUntitledFile = activeUri.scheme === 'untitled';
    // Untitled and pinned files have no local project structure — always use interactive mode.
    const mode = (isUntitledFile || !!pinned) || isInteractiveMode(activeUri.fsPath, activeDir, activeUri.toString()) ? 'interactive' : 'file';
    const modeTag = mode === 'interactive' ? ' [interactive]' : '';
    resultsChannel.appendLine(`\n── Execute on "${pickedName}" (${pickedDescription})${modeTag} ──`);
    resultsChannel.appendLine(new Date().toISOString());

    let result: unknown;
    executionStatusBar.show();
    try {
      result = await vscode.window.withProgress(
        { location: vscode.ProgressLocation.Notification, title: `KafkaSQL: reading from "${pickedName}"…`, cancellable: true },
        async (_progress, token) => {
          token.onCancellationRequested(() => sendLspCommand('kafkasql.cancelExecution', []).catch(() => {}));
          return sendLspCommand('kafkasql.executeStatement', [lspFilePath, pickedName, text, mode]);
        }
      );
    } catch (err) {
      resultsChannel.appendLine(`ERROR: ${err}`);
      vscode.window.showErrorMessage(`KafkaSQL execute failed: ${err}`);
      return;
    } finally {
      executionStatusBar.hide();
    }

    if (result && typeof result === 'object' && 'error' in (result as object)) {
      const msg = (result as { error: string }).error;
      resultsChannel.appendLine(`ERROR: ${msg}`);
      vscode.window.showErrorMessage(`KafkaSQL execute: ${msg}`);
      return;
    }

    if (result && typeof result === 'object' && 'executed' in (result as object)) {
      const r = result as {
        executed: number;
        operations: string[];
        records?: Array<{ typeName: string; fields: Record<string, unknown> }>;
      };
      for (const op of r.operations) {
        resultsChannel.appendLine(`  ${op}`);
      }
      if ('records' in r) {
        // Records were already streamed to the channel via kafkasql/record notifications.
        // Just show the summary line.
        const count = (r.records ?? []).length;
        resultsChannel.appendLine(`\n${count} record(s) returned.`);
        if (count > 0) {
          vscode.window.showInformationMessage(
            `KafkaSQL: ${count} record(s) returned from "${pickedName}"`);
        } else {
          vscode.window.showInformationMessage(
            `KafkaSQL: no matching records on "${pickedName}"`);
        }
      } else if (r.executed > 0) {
        resultsChannel.appendLine(`\n${r.executed} event(s) written.`);
        vscode.window.showInformationMessage(
          `KafkaSQL: ${r.executed} event(s) written to "${pickedName}"`);
      } else {
        resultsChannel.appendLine(`\nnothing to write — schema already up to date.`);
        vscode.window.showInformationMessage(
          `KafkaSQL: nothing to write — schema already up to date on "${pickedName}"`);
      }
    }
  });

  startServer(context, () => explorer.refresh()).catch(err => {
    console.error('Failed to start KafkaSQL LSP:', err);
  });
}
export function deactivate(): Thenable<void> | undefined {
  if (client) {
    const stopPromise = client.stop();
    client = null;
    return stopPromise;
  }
  return undefined;
}
