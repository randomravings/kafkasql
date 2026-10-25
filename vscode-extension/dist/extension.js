"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.activate = activate;
exports.deactivate = deactivate;
const vscode = __importStar(require("vscode"));
const path = __importStar(require("path"));
const node_1 = require("vscode-languageclient/node");
const fs = __importStar(require("fs"));
const kafkaSqlProjectExplorer_1 = require("./kafkaSqlProjectExplorer");
let client = null;
let lspOutput; // module-level so startServer + commands can share it
let resultsChannel;
let includeDiagnostics;
let diffDiagnostics;
let clusterDiffDiagnostics;
// URI stored by "Select for Compare"
let compareBaseUri;
let currentMode = 'git';
let modeStatusBar;
// ── File mode status bar ────────────────────────────────────────────────────
let fileModeStatusBar;
let connectionStatusBar;
let executionStatusBar;
// Per-file mode overrides set by the user: uri.toString() → selected mode
// Absent = auto-detect from file path.
const fileModeOverrides = new Map();
const pinnedConnections = new Map();
const TOML_UNSAFE_CHARS = /["\\\x00-\x1f\x7f]/;
const WORKSPACE_STATE_FILE_MODES_KEY = 'kafkasql.fileModeOverrides';
const WORKSPACE_STATE_PINNED_CONNECTIONS_KEY = 'kafkasql.pinnedConnections';
function validateTomlSafe(fieldName, value, required = true) {
    const trimmed = value.trim();
    if (required && !trimmed)
        return 'Required';
    if (trimmed && TOML_UNSAFE_CHARS.test(trimmed)) {
        return `${fieldName} cannot contain quotes, backslashes, or control characters`;
    }
    return null;
}
function maybeWarnOnStoredCredentials(fields) {
    if (fields.username && fields.password) {
        vscode.window.showWarningMessage('KafkaSQL: credentials are stored in plaintext in connections.toml. Use local-only files and keep them out of source control.');
    }
}
function findBuiltServerJar(workspaceRoot) {
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
            if (parent === cur)
                break;
            cur = parent;
        }
        return null;
    }
    catch {
        return null;
    }
}
async function startServer(context, onReady) {
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
    const javaBin = javaHome ? path.join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
    const javaExe = fs.existsSync(javaBin) ? javaBin : 'java';
    lspOutput.appendLine(`[kafkasql-lsp] Using JAVA_HOME=${env.JAVA_HOME}`);
    lspOutput.appendLine(`[kafkasql-lsp] Launching jar: ${serverJar}`);
    // Let vscode-languageclient own the process lifecycle — this avoids the
    // manual-spawn race conditions and routes server stderr to lspOutput.
    const serverOptions = {
        run: { command: javaExe, args: ['-jar', serverJar], transport: node_1.TransportKind.stdio, options: { cwd: workspaceRoot, env } },
        debug: { command: javaExe, args: ['-jar', serverJar], transport: node_1.TransportKind.stdio, options: { cwd: workspaceRoot, env } },
    };
    const clientOptions = {
        documentSelector: [{ scheme: 'file', language: 'kafkasql' }],
        outputChannel: lspOutput,
        revealOutputChannelOn: node_1.RevealOutputChannelOn.Never,
        errorHandler: {
            error(error, _message, _count) {
                lspOutput.appendLine(`[kafkasql-lsp][error] ${error?.message ?? String(error)}`);
                return { action: node_1.ErrorAction.Continue, handled: true };
            },
            closed() {
                lspOutput.appendLine('[kafkasql-lsp] Server connection closed.');
                return { action: node_1.CloseAction.DoNotRestart, handled: true };
            },
        },
    };
    client = new node_1.LanguageClient('kafkasql', 'KafkaSQL Language Server', serverOptions, clientOptions);
    context.subscriptions.push(client);
    client.start().then(() => {
        lspOutput.appendLine('[kafkasql-lsp] Server ready.');
        sendModeToLsp(currentMode);
        // Stream individual records to the output channel as they arrive from the server.
        client.onNotification('kafkasql/record', (row) => {
            const fieldStr = Object.entries(row.fields)
                .map(([k, v]) => `${k}: ${JSON.stringify(v)}`)
                .join(', ');
            resultsChannel.appendLine(`  ${row.typeName} { ${fieldStr} }`);
        });
        onReady?.();
    }).catch((err) => {
        lspOutput.appendLine(`[kafkasql-lsp][error] Client start failed: ${err}`);
        client = null;
    });
}
async function collectAllIncludes(entryPath, workspaceRoot, seen = new Set()) {
    const absPath = path.isAbsolute(entryPath) ? entryPath : path.join(workspaceRoot, entryPath);
    if (seen.has(absPath) || !fs.existsSync(absPath))
        return [];
    seen.add(absPath);
    const text = fs.readFileSync(absPath, 'utf8');
    const includeRegex = /^\s*include\s+['"](.+?)['"]/gim;
    let match;
    let allFiles = [absPath];
    while ((match = includeRegex.exec(text))) {
        const incPath = match[1];
        const incFiles = await collectAllIncludes(incPath, workspaceRoot, seen);
        allFiles = allFiles.concat(incFiles);
    }
    return allFiles;
}
/** Call the LSP semanticDiff command and show results as diagnostics on rightUri. */
async function runSemanticDiff(leftUri, rightUri) {
    if (!client)
        return null;
    diffDiagnostics.delete(rightUri);
    try {
        const result = await client.sendRequest('workspace/executeCommand', {
            command: 'kafkasql.semanticDiff',
            arguments: [leftUri.fsPath, rightUri.fsPath]
        });
        if (!Array.isArray(result)) {
            if (result && typeof result === 'object' && 'error' in result) {
                vscode.window.showErrorMessage(`KafkaSQL diff error: ${result.error}`);
            }
            return null;
        }
        const entries = result;
        const diags = [];
        for (const entry of entries) {
            // For removed items (LEFT_ONLY) there is no rightRange; show at top of right file
            const rng = entry.rightRange ?? entry.leftRange;
            if (!rng)
                continue;
            // Java Pos uses 1-based lines; VS Code uses 0-based
            const start = new vscode.Position(Math.max(0, rng.from.ln - 1), Math.max(0, rng.from.ch));
            const end = new vscode.Position(Math.max(0, rng.to.ln - 1), Math.max(0, rng.to.ch));
            const diag = new vscode.Diagnostic(new vscode.Range(start, end), entry.message, mapSeverity(entry.severity));
            diag.source = 'kafkasql-diff';
            diag.code = entry.aspect;
            diags.push(diag);
        }
        diffDiagnostics.set(rightUri, diags);
        if (diags.length === 0) {
            vscode.window.showInformationMessage('KafkaSQL: no compatibility issues detected');
        }
        else {
            const breaking = diags.filter(d => d.severity === vscode.DiagnosticSeverity.Error).length;
            const msg = breaking > 0
                ? `KafkaSQL: ${breaking} breaking change(s), ${diags.length} total — see Problems panel`
                : `KafkaSQL: ${diags.length} change(s) — see Problems panel`;
            vscode.window.showInformationMessage(msg);
        }
        const changed = entries.filter(e => e.kind !== 'UNCHANGED');
        const breaking = changed.filter(e => e.severity === 'BREAKING').length;
        return { total: changed.length, breaking, entries: changed };
    }
    catch (err) {
        console.error('KafkaSQL semanticDiff failed:', err);
        return null;
    }
}
async function runClusterDiff(localUri, projectFile, connectionName, connectionLabel) {
    if (!client)
        return null;
    clusterDiffDiagnostics.delete(localUri);
    try {
        const result = await client.sendRequest('workspace/executeCommand', {
            command: 'kafkasql.diffWithCluster',
            arguments: [projectFile, connectionName, localUri.fsPath],
        });
        if (!Array.isArray(result)) {
            if (result && typeof result === 'object' && 'error' in result) {
                vscode.window.showErrorMessage(`KafkaSQL cluster diff error: ${result.error}`);
            }
            return null;
        }
        const entries = result;
        const diags = [];
        for (const entry of entries) {
            const rng = entry.rightRange ?? entry.leftRange;
            if (!rng)
                continue;
            const start = new vscode.Position(Math.max(0, rng.from.ln - 1), Math.max(0, rng.from.ch));
            const end = new vscode.Position(Math.max(0, rng.to.ln - 1), Math.max(0, rng.to.ch));
            const diag = new vscode.Diagnostic(new vscode.Range(start, end), entry.message, mapSeverity(entry.severity));
            diag.source = 'kafkasql-cluster-diff';
            diag.code = entry.aspect;
            diags.push(diag);
        }
        clusterDiffDiagnostics.set(localUri, diags);
        if (diags.length === 0) {
            vscode.window.showInformationMessage(`KafkaSQL: local schema matches cluster "${connectionLabel}"`);
        }
        else {
            const breaking = diags.filter(d => d.severity === vscode.DiagnosticSeverity.Error).length;
            const msg = breaking > 0
                ? `KafkaSQL: ${breaking} breaking difference(s) vs cluster "${connectionLabel}" — see Problems panel`
                : `KafkaSQL: ${diags.length} difference(s) vs cluster "${connectionLabel}" — see Problems panel`;
            vscode.window.showInformationMessage(msg);
        }
        const changed = entries.filter(e => e.kind !== 'UNCHANGED');
        const breaking = changed.filter(e => e.severity === 'BREAKING').length;
        return { total: changed.length, breaking, entries: changed };
    }
    catch (err) {
        vscode.window.showErrorMessage(`KafkaSQL cluster diff failed: ${err}`);
        return null;
    }
}
async function runProjectClusterDiff(projectFile, connectionName) {
    if (!client)
        return null;
    try {
        const result = await client.sendRequest('workspace/executeCommand', {
            command: 'kafkasql.diffProjectWithCluster',
            arguments: [projectFile, connectionName],
        });
        if (!Array.isArray(result)) {
            if (result && typeof result === 'object' && 'error' in result) {
                vscode.window.showErrorMessage(`KafkaSQL project/cluster diff error: ${result.error}`);
            }
            return null;
        }
        const entries = result;
        const changed = entries.filter(e => e.kind !== 'UNCHANGED');
        const breaking = changed.filter(e => e.severity === 'BREAKING').length;
        if (changed.length === 0) {
            vscode.window.showInformationMessage('KafkaSQL: project matches cluster');
        }
        else {
            const msg = breaking > 0
                ? `KafkaSQL: ${breaking} breaking difference(s), ${changed.length} total — see output`
                : `KafkaSQL: ${changed.length} difference(s) — see output`;
            vscode.window.showInformationMessage(msg);
        }
        return { total: changed.length, breaking, entries: changed };
    }
    catch (err) {
        vscode.window.showErrorMessage(`KafkaSQL project/cluster diff failed: ${err}`);
        return null;
    }
}
async function runCompositeSemanticDiff(leftType, leftRef, rightType, rightRef) {
    if (!client)
        return null;
    try {
        const result = await client.sendRequest('workspace/executeCommand', {
            command: 'kafkasql.semanticDiffComposite',
            arguments: [leftType, leftRef, rightType, rightRef],
        });
        if (!Array.isArray(result)) {
            if (result && typeof result === 'object' && 'error' in result) {
                vscode.window.showErrorMessage(`KafkaSQL composite diff error: ${result.error}`);
            }
            return null;
        }
        return result;
    }
    catch (err) {
        vscode.window.showErrorMessage(`KafkaSQL composite diff failed: ${err}`);
        return null;
    }
}
function mapSeverity(s) {
    switch (s) {
        case 'BREAKING': return vscode.DiagnosticSeverity.Error;
        case 'WARNING': return vscode.DiagnosticSeverity.Warning;
        case 'SAFE': return vscode.DiagnosticSeverity.Information;
        default: return vscode.DiagnosticSeverity.Hint;
    }
}
function renderCompareDialogHtml(payload) {
    const escapeHtml = (value) => value
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
    const renderOptionNodes = (type, selectedId) => {
        const options = payload.optionsByType[type] ?? [];
        if (options.length === 0)
            return '<option value="">(no options available)</option>';
        return options.map(opt => {
            const selected = selectedId && selectedId === opt.id ? ' selected' : '';
            return `<option value="${escapeHtml(opt.id)}"${selected}>${escapeHtml(opt.label)}</option>`;
        }).join('');
    };
    const initialStatus = 'Ready. Sources — project: ' + (payload.optionCounts.project || 0)
        + ', cluster: ' + (payload.optionCounts.cluster || 0)
        + ', folder: ' + (payload.optionCounts.folder || 0)
        + ', file: ' + (payload.optionCounts.file || 0);
    const payloadJson = JSON.stringify(payload).replace(/<\/script/gi, '<\\/script');
    return `<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>KafkaSQL Compare</title>
  <style>
    :root { color-scheme: light dark; }
    body {
      font-family: var(--vscode-font-family);
      color: var(--vscode-foreground);
      background: var(--vscode-editor-background);
      margin: 0; padding: 16px;
    }
    .grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
      margin-bottom: 16px;
    }
    .card {
      border: 1px solid var(--vscode-editorWidget-border, #666);
      border-radius: 6px;
      padding: 12px;
      background: var(--vscode-sideBar-background);
    }
    .card h3 { margin: 0 0 12px 0; font-size: 14px; }
    label { display:block; margin: 8px 0 4px; font-size: 12px; }
    select {
      width: 100%;
      box-sizing: border-box;
      padding: 6px;
      background: var(--vscode-input-background);
      color: var(--vscode-input-foreground);
      border: 1px solid var(--vscode-input-border, #666);
      border-radius: 4px;
    }
    .path-row {
      display: flex;
      gap: 8px;
      align-items: center;
      margin-top: 4px;
    }
    input.path {
      width: 100%;
      box-sizing: border-box;
      padding: 6px;
      background: var(--vscode-input-background);
      color: var(--vscode-input-foreground);
      border: 1px solid var(--vscode-input-border, #666);
      border-radius: 4px;
    }
    .hint {
      margin-top: 8px;
      font-size: 12px;
      color: var(--vscode-descriptionForeground);
      min-height: 32px;
      white-space: pre-wrap;
    }
    .actions { display:flex; gap:8px; justify-content:flex-end; }
    .result {
      margin-top: 12px;
      border: 1px solid var(--vscode-editorWidget-border, #666);
      border-radius: 6px;
      padding: 10px;
      background: var(--vscode-editor-background);
      min-height: 80px;
    }
    .result h4 { margin: 0 0 8px 0; font-size: 13px; }
    .result .status { font-size: 12px; color: var(--vscode-descriptionForeground); margin-bottom: 8px; }
    .result .result-grid { max-height: 340px; overflow-x: auto; overflow-y: scroll; }
    .result table {
      width: 100%;
      border-collapse: collapse;
      table-layout: fixed;
      font-size: 12px;
    }
    .result th, .result td {
      border: 1px solid var(--vscode-editorWidget-border, #666);
      padding: 4px 6px;
      vertical-align: top;
      word-break: break-word;
    }
    .result th {
      background: var(--vscode-editor-lineHighlightBackground);
      text-align: left;
      font-weight: 600;
    }
    .result td.side-empty {
      color: var(--vscode-descriptionForeground);
      font-style: italic;
    }
    .result .summary-grid {
      width: 100%;
      border-collapse: collapse;
      table-layout: fixed;
      font-size: 12px;
    }
    .result .summary-grid th,
    .result .summary-grid td {
      border: 2px solid var(--vscode-editorWidget-border, #666);
      padding: 4px 6px;
      vertical-align: top;
      overflow-wrap: anywhere;
    }
    .result .summary-grid .zone-left,
    .result .summary-grid .zone-right,
    .result .summary-grid .lane-control {
      background: transparent;
    }
    .result .summary-grid .summary-op {
      width: 2.5rem;
      min-width: 2.5rem;
      white-space: nowrap;
      text-align: center;
      font-weight: 600;
      border-right-width: 3px;
      border-right-color: var(--vscode-focusBorder);
      padding-left: 2px;
      padding-right: 2px;
    }
    .result .summary-grid .summary-op.op-eq {
      background: transparent;
      color: var(--vscode-foreground);
    }
    .result .summary-grid .summary-op.op-diff {
      background: rgba(255, 215, 0, 0.14);
      color: var(--vscode-foreground);
    }
    .result .summary-grid .summary-op.op-side {
      background: rgba(255, 82, 82, 0.16);
      color: var(--vscode-foreground);
    }
    .result .summary-grid tr.summary-row.row-diff td {
      background: rgba(255, 215, 0, 0.08);
    }
    .result .summary-grid tr.summary-row.row-side td {
      background: rgba(255, 82, 82, 0.10);
    }
    .result .summary-grid tr.detail-row.row-diff td {
      background: rgba(255, 215, 0, 0.05);
    }
    .result .summary-grid tr.detail-row.row-side td {
      background: rgba(255, 82, 82, 0.07);
    }
    .result .summary-grid .summary-toggle {
      width: 2rem;
      min-width: 2rem;
      white-space: nowrap;
      text-align: center;
      border-left-width: 3px;
      border-left-color: var(--vscode-focusBorder);
      padding-left: 2px;
      padding-right: 2px;
    }
    .result .summary-grid .summary-type {
      width: 8rem;
      min-width: 8rem;
      white-space: nowrap;
      font-weight: 600;
    }
    .result .summary-grid .summary-middle-sep {
      border-left-width: 3px !important;
      border-left-color: var(--vscode-focusBorder) !important;
    }
    .result .summary-grid td.zone-left {
      padding-right: 10px;
    }
    .result .summary-grid td.zone-right {
      padding-left: 10px;
    }
    .result .summary-grid .left-cell {
      display: flex;
      align-items: center;
      gap: 6px;
      min-width: 0;
    }
    .result .summary-grid .left-label {
      min-width: 0;
      overflow-wrap: anywhere;
    }
    .result .row-toggle {
      padding: 0 4px;
      line-height: 1.1;
      min-width: 18px;
      height: 18px;
      font-size: 11px;
    }
    .result .summary-grid .detail-cell {
      padding: 6px;
      background: var(--vscode-sideBar-background);
      border-top-width: 1px;
      border-top-style: dashed;
      border-top-color: var(--vscode-editorWidget-border, #666);
    }
    .result .summary-grid tr.summary-row.expanded td {
      border-bottom-width: 1px;
      border-bottom-style: dashed;
      border-bottom-color: var(--vscode-editorWidget-border, #666);
    }
    .result .summary-grid .detail-lines-cell,
    .result .summary-grid .detail-op-cell {
      padding: 0;
      background: var(--vscode-sideBar-background);
      font-family: var(--vscode-editor-font-family, var(--vscode-font-family));
      border-top-width: 1px;
      border-top-style: dashed;
      border-top-color: var(--vscode-editorWidget-border, #666);
    }
    .result .summary-grid .detail-op-cell {
      text-align: center;
      font-weight: 600;
    }
    .result .detail-lines {
      display: block;
      padding: 4px 6px;
      white-space: pre;
      overflow-wrap: anywhere;
      line-height: 1.35;
      min-height: 1.35em;
    }
    .result .detail-lines.op-diff {
      background: rgba(255, 215, 0, 0.12);
    }
    .result .detail-lines.op-side {
      background: rgba(255, 82, 82, 0.14);
    }
    .result .detail-lines.side-empty {
      color: var(--vscode-descriptionForeground);
      font-style: italic;
    }
    button {
      padding: 6px 12px;
      border-radius: 4px;
      border: 1px solid var(--vscode-button-border, transparent);
      cursor: pointer;
    }
    button.primary {
      background: var(--vscode-button-background);
      color: var(--vscode-button-foreground);
    }
    button.secondary {
      background: var(--vscode-button-secondaryBackground);
      color: var(--vscode-button-secondaryForeground);
    }
  </style>
</head>
<body>
  <div class="grid">
    <section class="card">
      <h3>Left side</h3>
      <label for="leftType">Type</label>
      <select id="leftType">
        <option value="project">Project</option>
        <option value="cluster">Cluster</option>
        <option value="folder">Folder</option>
        <option value="file">File</option>
      </select>
      <label for="leftSource">Source</label>
      <select id="leftSource">${renderOptionNodes(payload.defaultLeftType, payload.defaultLeftId)}</select>
      <div class="path-row">
        <input id="leftPath" class="path" type="text" readonly style="display:none" />
        <button id="leftBrowseBtn" class="secondary" style="display:none">Select…</button>
      </div>
      <div id="leftHint" class="hint"></div>
    </section>
    <section class="card">
      <h3>Right side</h3>
      <label for="rightType">Type</label>
      <select id="rightType">
        <option value="project">Project</option>
        <option value="cluster">Cluster</option>
        <option value="folder">Folder</option>
        <option value="file">File</option>
      </select>
      <label for="rightSource">Source</label>
      <select id="rightSource">${renderOptionNodes(payload.defaultRightType, payload.defaultRightId)}</select>
      <div class="path-row">
        <input id="rightPath" class="path" type="text" readonly style="display:none" />
        <button id="rightBrowseBtn" class="secondary" style="display:none">Select…</button>
      </div>
      <div id="rightHint" class="hint"></div>
    </section>
  </div>
  <div class="actions">
    <button id="cancelBtn" class="secondary">Cancel</button>
    <button id="compareBtn" class="primary">Compare</button>
  </div>
  <section class="result">
    <h4>Comparison Output</h4>
    <div id="resultStatus" class="status">${escapeHtml(initialStatus)}</div>
    <div id="resultGrid" class="result-grid"></div>
  </section>
  <script id="comparePayload" type="application/json">${payloadJson}</script>
  <script>
    (function () {
      const payloadEl = document.getElementById('comparePayload');
      const vscode = typeof acquireVsCodeApi === 'function'
        ? acquireVsCodeApi()
        : { postMessage: () => {} };
      let data = {};
      try {
        if (payloadEl && payloadEl.textContent) {
          data = JSON.parse(payloadEl.textContent);
        }
      } catch (err) {
        const statusEl = document.getElementById('resultStatus');
        if (statusEl) statusEl.textContent = 'Dialog error: payload parse failed (' + String(err) + ')';
      }

      const leftType = document.getElementById('leftType');
      const rightType = document.getElementById('rightType');
      const leftSource = document.getElementById('leftSource');
      const rightSource = document.getElementById('rightSource');
      const leftHint = document.getElementById('leftHint');
      const rightHint = document.getElementById('rightHint');
      const leftBrowseBtn = document.getElementById('leftBrowseBtn');
      const rightBrowseBtn = document.getElementById('rightBrowseBtn');
      const leftPath = document.getElementById('leftPath');
      const rightPath = document.getElementById('rightPath');
      const resultStatus = document.getElementById('resultStatus');
      const resultGrid = document.getElementById('resultGrid');
      const compareBtn = document.getElementById('compareBtn');
      window.addEventListener('error', (event) => {
        resultStatus.textContent = 'Dialog error: ' + (event.message || 'unknown error');
      });
      window.addEventListener('unhandledrejection', (event) => {
        const reason = event.reason && event.reason.message ? event.reason.message : String(event.reason || 'unknown rejection');
        resultStatus.textContent = 'Dialog error: ' + reason;
      });

    const state = {
      left: { type: 'project', sourceId: '', path: '' },
      right: { type: 'project', sourceId: '', path: '' },
    };

    function getSideState(side) { return side === 'left' ? state.left : state.right; }

    function populate(side) {
      const typeEl = side === 'left' ? leftType : rightType;
      const sourceEl = side === 'left' ? leftSource : rightSource;
      const hintEl = side === 'left' ? leftHint : rightHint;
      const browseBtn = side === 'left' ? leftBrowseBtn : rightBrowseBtn;
      const pathEl = side === 'left' ? leftPath : rightPath;
      const st = getSideState(side);
      const type = typeEl.value;
      st.type = type;
      const options = data.optionsByType[type] || [];

      const pickByDialog = type === 'file' || type === 'folder';
      sourceEl.style.display = pickByDialog ? 'none' : 'block';
      browseBtn.style.display = pickByDialog ? 'inline-block' : 'none';
      pathEl.style.display = pickByDialog ? 'block' : 'none';

      if (pickByDialog) {
        sourceEl.innerHTML = '';
        st.sourceId = '';
        hintEl.textContent = st.path ? st.path : 'Click Select… to choose source.';
        pathEl.value = st.path || '';
        return;
      }

      sourceEl.innerHTML = '';
      if (options.length === 0) {
        const opt = document.createElement('option');
        opt.value = '';
        opt.textContent = '(no options available)';
        sourceEl.appendChild(opt);
        sourceEl.disabled = true;
        st.sourceId = '';
        hintEl.textContent = '';
        return;
      }

      sourceEl.disabled = false;
      for (const o of options) {
        const opt = document.createElement('option');
        opt.value = o.id;
        opt.textContent = o.label;
        sourceEl.appendChild(opt);
      }
      if (side === 'left' && data.defaultLeftId && options.some(o => o.id === data.defaultLeftId)) {
        sourceEl.value = data.defaultLeftId;
      } else if (side === 'right' && data.defaultRightId && options.some(o => o.id === data.defaultRightId)) {
        sourceEl.value = data.defaultRightId;
      }
      st.sourceId = sourceEl.value;
      updateHint(side);
    }

    function updateHint(side) {
      const sourceEl = side === 'left' ? leftSource : rightSource;
      const hintEl = side === 'left' ? leftHint : rightHint;
      const pathEl = side === 'left' ? leftPath : rightPath;
      const st = getSideState(side);
      if (st.type === 'file' || st.type === 'folder') {
        hintEl.textContent = st.path ? st.path : 'Click Select… to choose source.';
        pathEl.value = st.path || '';
        return;
      }
      const options = data.optionsByType[st.type] || [];
      const sel = options.find(o => o.id === sourceEl.value);
      st.sourceId = sourceEl.value;
      hintEl.textContent = (sel && sel.detail) ? sel.detail : '';
    }

    leftType.addEventListener('change', () => {
      state.left.sourceId = '';
      state.left.path = '';
      populate('left');
    });
    rightType.addEventListener('change', () => {
      state.right.sourceId = '';
      state.right.path = '';
      populate('right');
    });
    leftSource.addEventListener('change', () => updateHint('left'));
    rightSource.addEventListener('change', () => updateHint('right'));
    leftBrowseBtn.addEventListener('click', () => {
      vscode.postMessage({ command: 'browse', side: 'left', type: state.left.type });
    });
    rightBrowseBtn.addEventListener('click', () => {
      vscode.postMessage({ command: 'browse', side: 'right', type: state.right.type });
    });

    document.getElementById('cancelBtn').addEventListener('click', () => {
      vscode.postMessage({ command: 'cancel' });
    });
    compareBtn.addEventListener('click', () => {
      vscode.postMessage({
        command: 'compare',
        leftType: leftType.value,
        rightType: rightType.value,
        leftId: state.left.sourceId || leftSource.value,
        rightId: state.right.sourceId || rightSource.value,
        leftPath: state.left.path,
        rightPath: state.right.path,
      });
    });

    const opForValues = (leftValue, rightValue) => {
      const l = (leftValue || '').toString();
      const r = (rightValue || '').toString();
      if (l && r) return l === r ? '==' : '!=';
      return l ? '<<' : (r ? '>>' : '==');
    };
    const opClassForValue = (op) => {
      if (op === '!=') return 'op-diff';
      if (op === '<<' || op === '>>') return 'op-side';
      return 'op-eq';
    };
    const objectParts = (side, row) => {
      const key = side === 'left' ? (row.leftKey || '') : (row.rightKey || '');
      if (!key) return { createLine: '', members: {}, memberKeys: [], props: {} };
      const type = side === 'left' ? (row.leftType || 'OBJECT') : (row.rightType || 'OBJECT');
      const children = side === 'left' ? (row.leftChildren || []) : (row.rightChildren || []);
      const props = side === 'left' ? (row.leftProperties || {}) : (row.rightProperties || {});
      const members = {};
      const memberKeys = [];
      for (const child of children) {
        const kind = (child.kind || '').toUpperCase();
        const rawName = child.name || '';
        const shortName = rawName.includes('.') ? rawName.substring(rawName.lastIndexOf('.') + 1) : rawName;
        const signature = (child.signature || '').trim();
        const memberKey = kind + ':' + shortName;
        if (kind === 'SYMBOL') {
          members[memberKey] = '  ' + shortName + (signature ? ' = ' + signature : '');
        } else {
          members[memberKey] = '  ' + shortName + (signature ? ' ' + signature : '');
        }
        memberKeys.push(memberKey);
      }
      memberKeys.sort((a, b) => a.localeCompare(b));
      const normalizedProps = {};
      const propKeys = Object.keys(props).sort((a, b) => a.localeCompare(b));
      for (const p of propKeys) {
        normalizedProps[p] = props[p] || '';
      }
      return {
        createLine: 'CREATE ' + type + ' ' + key,
        members,
        memberKeys,
        props: normalizedProps,
      };
    };
    const alignedRowLines = (row) => {
      const left = objectParts('left', row);
      const right = objectParts('right', row);
      const linePairs = [];
      const formatPropertyLine = (name, value) => {
        if (!name) return '';
        const raw = (value || '').toString();
        if (name.toUpperCase() === 'COMMENT') {
          const escaped = raw.replace(/'/g, "''");
          return name + " '" + escaped + "'";
        }
        return name + ' ' + raw;
      };

      if (left.createLine || right.createLine) {
        linePairs.push([left.createLine || '', right.createLine || '']);
      }

      const hasLeftMembers = left.memberKeys.length > 0;
      const hasRightMembers = right.memberKeys.length > 0;
      if (hasLeftMembers || hasRightMembers) {
        linePairs.push([hasLeftMembers ? '(' : '', hasRightMembers ? '(' : '']);
        const memberKeySet = new Set([...left.memberKeys, ...right.memberKeys]);
        const memberKeys = Array.from(memberKeySet).sort((a, b) => a.localeCompare(b));
        for (const k of memberKeys) {
          linePairs.push([left.members[k] || '', right.members[k] || '']);
        }
        linePairs.push([hasLeftMembers ? ')' : '', hasRightMembers ? ')' : '']);
      }

      const propKeySet = new Set([...Object.keys(left.props), ...Object.keys(right.props)]);
      const propKeys = Array.from(propKeySet).sort((a, b) => a.localeCompare(b));
      for (const p of propKeys) {
        const leftLine = Object.prototype.hasOwnProperty.call(left.props, p) ? formatPropertyLine(p, left.props[p]) : '';
        const rightLine = Object.prototype.hasOwnProperty.call(right.props, p) ? formatPropertyLine(p, right.props[p]) : '';
        linePairs.push([leftLine, rightLine]);
      }

      return linePairs.length > 0 ? linePairs : [['', '']];
    };
    const renderCompareRows = (rows) => {
      resultGrid.innerHTML = '';
      const grid = document.createElement('table');
      grid.className = 'summary-grid';
      const tbody = document.createElement('tbody');
      for (const row of (rows || [])) {
        const summaryRow = document.createElement('tr');
        summaryRow.className = 'summary-row';
        const rowOp = row.op || '';
        const rowOpClass = opClassForValue(rowOp);
        const rowClass = 'row-' + rowOpClass.substring(3);
        summaryRow.className += ' ' + rowClass;

        const opTd = document.createElement('td');
        opTd.className = 'summary-op lane-control ' + rowOpClass;
        opTd.textContent = rowOp;
        summaryRow.appendChild(opTd);

        const leftTypeTd = document.createElement('td');
        leftTypeTd.className = 'summary-type zone-left';
        leftTypeTd.textContent = row.leftType || '';
        summaryRow.appendChild(leftTypeTd);

        const leftTd = document.createElement('td');
        const leftCell = document.createElement('div');
        leftCell.className = 'left-cell';
        const leftLabel = document.createElement('span');
        leftLabel.className = 'left-label';
        leftLabel.textContent = row.leftKey || '∅';
        leftCell.appendChild(leftLabel);
        leftTd.appendChild(leftCell);
        leftTd.className = 'zone-left';
        if (!leftLabel.textContent || leftLabel.textContent === '∅') leftTd.className += ' side-empty';
        summaryRow.appendChild(leftTd);

        const rightTypeTd = document.createElement('td');
        rightTypeTd.className = 'summary-type summary-middle-sep zone-right';
        rightTypeTd.textContent = row.rightType || '';
        summaryRow.appendChild(rightTypeTd);

        const rightTd = document.createElement('td');
        rightTd.textContent = row.rightKey || '∅';
        rightTd.className = 'zone-right';
        if (!rightTd.textContent || rightTd.textContent === '∅') rightTd.className += ' side-empty';
        summaryRow.appendChild(rightTd);

        const toggleCol = document.createElement('td');
        toggleCol.className = 'summary-toggle lane-control';
        const toggleBtn = document.createElement('button');
        toggleBtn.className = 'secondary row-toggle';
        toggleBtn.textContent = '+';
        toggleBtn.title = 'Show lines';
        toggleCol.appendChild(toggleBtn);
        summaryRow.appendChild(toggleCol);

        tbody.appendChild(summaryRow);

        const detailRow = document.createElement('tr');
        detailRow.className = 'detail-row ' + rowClass;
        detailRow.style.display = 'none';
        const linePairs = alignedRowLines(row);
        const opDetailCol = document.createElement('td');
        opDetailCol.className = 'detail-op-cell summary-op lane-control ' + rowOpClass;
        detailRow.appendChild(opDetailCol);

        const typeSpacerCol = document.createElement('td');
        typeSpacerCol.className = 'summary-type zone-left';
        detailRow.appendChild(typeSpacerCol);

        const leftDetailCol = document.createElement('td');
        leftDetailCol.className = 'detail-lines-cell zone-left';
        detailRow.appendChild(leftDetailCol);

        const rightTypeSpacerCol = document.createElement('td');
        rightTypeSpacerCol.className = 'summary-type summary-middle-sep zone-right';
        detailRow.appendChild(rightTypeSpacerCol);

        const rightDetailCol = document.createElement('td');
        rightDetailCol.className = 'detail-lines-cell zone-right';
        detailRow.appendChild(rightDetailCol);

        const emptyCol = document.createElement('td');
        emptyCol.className = 'summary-toggle lane-control';
        detailRow.appendChild(emptyCol);

        for (const pair of linePairs) {
          const leftLine = pair[0] || '';
          const rightLine = pair[1] || '';
          const lineOp = opForValues(leftLine, rightLine);
          const lineOpClass = opClassForValue(lineOp);
          const leftLineEl = document.createElement('div');
          leftLineEl.className = 'detail-lines ' + lineOpClass;
          leftLineEl.textContent = leftLine;
          if (!leftLine) leftLineEl.className += ' side-empty';
          leftDetailCol.appendChild(leftLineEl);

          const opLineEl = document.createElement('div');
          opLineEl.className = 'detail-lines ' + lineOpClass;
          opLineEl.textContent = lineOp;
          opDetailCol.appendChild(opLineEl);

          const rightLineEl = document.createElement('div');
          rightLineEl.className = 'detail-lines ' + lineOpClass;
          rightLineEl.textContent = rightLine;
          if (!rightLine) rightLineEl.className += ' side-empty';
          rightDetailCol.appendChild(rightLineEl);
        }
        tbody.appendChild(detailRow);

        toggleBtn.addEventListener('click', () => {
          const expanded = detailRow.style.display !== 'none';
          detailRow.style.display = expanded ? 'none' : '';
          summaryRow.classList.toggle('expanded', !expanded);
          toggleBtn.textContent = expanded ? '+' : '-';
          toggleBtn.title = expanded ? 'Show lines' : 'Hide lines';
        });
      }

      grid.appendChild(tbody);
      resultGrid.appendChild(grid);
    };

    window.addEventListener('message', event => {
      const msg = event.data;
      if (!msg || !msg.command) return;
      if (msg.command === 'browseResult') {
        const side = msg.side;
        const st = getSideState(side);
        st.path = msg.path || '';
        updateHint(side);
        return;
      }
      if (msg.command === 'compareStarted') {
        compareBtn.disabled = true;
        resultStatus.textContent = 'Comparing...';
        resultGrid.innerHTML = '';
        return;
      }
      if (msg.command === 'compareError') {
        compareBtn.disabled = false;
        resultStatus.textContent = msg.message || 'Comparison failed.';
        return;
      }
      if (msg.command === 'compareResult') {
        compareBtn.disabled = false;
        resultStatus.textContent = 'Done. ' + (msg.summary || '');
        renderCompareRows(msg.rows);
      }
    });

      try {
        if (data.defaultLeftType) leftType.value = data.defaultLeftType;
        if (data.defaultRightType) rightType.value = data.defaultRightType;
        if (data.defaultLeftPath) state.left.path = data.defaultLeftPath;
        if (data.defaultRightPath) state.right.path = data.defaultRightPath;
        const counts = data.optionCounts || {
          project: ((data.optionsByType && data.optionsByType.project) || []).length,
          cluster: ((data.optionsByType && data.optionsByType.cluster) || []).length,
          folder: ((data.optionsByType && data.optionsByType.folder) || []).length,
          file: ((data.optionsByType && data.optionsByType.file) || []).length,
        };
        resultStatus.textContent =
          'Ready. Sources — project: ' + (counts.project || 0)
          + ', cluster: ' + (counts.cluster || 0)
          + ', folder: ' + (counts.folder || 0)
          + ', file: ' + (counts.file || 0);
        populate('left');
        populate('right');
      } catch (err) {
        resultStatus.textContent = 'Dialog error: ' + String(err);
      }
    })();
  </script>
</body>
</html>`;
}
function modeLabel(mode) {
    switch (mode) {
        case 'none': return '$(circle-slash) Compat: Off';
        case 'git': return '$(git-compare) Compat: Git HEAD';
    }
}
async function sendModeToLsp(mode) {
    if (!client)
        return;
    try {
        await client.sendRequest('workspace/executeCommand', {
            command: 'kafkasql.setComparisonMode',
            arguments: [mode.toUpperCase(), null]
        });
    }
    catch (err) {
        lspOutput?.appendLine(`[kafkasql-lsp] setComparisonMode failed: ${err}`);
    }
}
async function pickComparisonMode() {
    const items = [
        { label: '$(git-compare) Git HEAD', description: 'Compare against the last committed version', mode: 'git' },
        { label: '$(circle-slash) Off', description: 'Disable compatibility checking', mode: 'none' },
    ];
    const picked = await vscode.window.showQuickPick(items, {
        placeHolder: 'KafkaSQL: select compatibility check mode'
    });
    if (!picked)
        return;
    const mode = picked.mode;
    currentMode = mode;
    modeStatusBar.text = modeLabel(currentMode);
    await sendModeToLsp(currentMode);
    vscode.window.showInformationMessage(`KafkaSQL compatibility mode: ${modeStatusBar.text.replace(/\$\([^)]+\) /, '')}`);
}
/** Walks upward from `startDir` looking for a `connections.toml` file. */
function findConnectionsToml(startDir) {
    let dir = startDir;
    while (true) {
        const candidate = path.join(dir, 'connections.toml');
        if (fs.existsSync(candidate))
            return candidate;
        const parent = path.dirname(dir);
        if (parent === dir)
            return null;
        dir = parent;
    }
}
/** Parses the TOML subset used by connections.toml. */
function parseConnectionsToml(tomlPath) {
    const lines = fs.readFileSync(tomlPath, 'utf8').split('\n');
    const result = [];
    let current = null;
    const flush = () => {
        if (current?.name && current.bootstrapServers && current.topic) {
            result.push(current);
        }
        current = null;
    };
    for (const raw of lines) {
        const line = raw.trim();
        if (!line || line.startsWith('#'))
            continue;
        const headerMatch = line.match(/^\[connection\.(.+)\]$/);
        if (headerMatch) {
            flush();
            current = { name: headerMatch[1] };
            continue;
        }
        if (!current)
            continue;
        const kvMatch = line.match(/^(\w+)\s*=\s*"([^"]*)"/)
            || line.match(/^(\w+)\s*=\s*([^#\s]+)/);
        if (!kvMatch)
            continue;
        const [, k, v] = kvMatch;
        if (k === 'bootstrap')
            current.bootstrapServers = v;
        if (k === 'topic')
            current.topic = v;
        if (k === 'username')
            current.username = v;
        if (k === 'password')
            current.password = v;
    }
    flush();
    return result;
}
// ── connections.toml write helpers ───────────────────────────────────────────
/** Escape a string value for use inside a TOML basic (double-quoted) string. */
function tomlEscape(value) {
    return value
        .replace(/\\/g, '\\\\')
        .replace(/"/g, '\\"')
        .replace(/\n/g, '\\n')
        .replace(/\r/g, '\\r')
        .replace(/\t/g, '\\t')
        .replace(/[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]/g, c => `\\u${c.codePointAt(0).toString(16).padStart(4, '0')}`);
}
/** Serialize a single [connection.*] block (no trailing newline). */
function serializeConnectionBlock(name, bootstrap, topic, username, password) {
    const lines = [`[connection.${name}]`];
    lines.push(`bootstrap = "${tomlEscape(bootstrap)}"`);
    lines.push(`topic     = "${tomlEscape(topic)}"`);
    if (username) {
        lines.push(`username  = "${tomlEscape(username)}"`);
    }
    if (password) {
        lines.push(`password  = "${tomlEscape(password)}"`);
    }
    return lines.join('\n');
}
/**
 * Remove a [connection.<name>] section (and any blank lines directly before it)
 * from raw TOML text. Returns the original text unchanged if the section is not found.
 */
function removeConnectionBlock(tomlText, name) {
    const lines = tomlText.split('\n');
    const header = `[connection.${name}]`;
    let start = -1;
    let end = lines.length;
    for (let i = 0; i < lines.length; i++) {
        const t = lines[i].trim();
        if (t === header) {
            start = i;
        }
        else if (start >= 0 && i > start && t.startsWith('[')) {
            end = i;
            break;
        }
    }
    if (start < 0)
        return tomlText;
    // Eat blank lines that immediately precede the section header
    let removeFrom = start;
    while (removeFrom > 0 && lines[removeFrom - 1].trim() === '') {
        removeFrom--;
    }
    lines.splice(removeFrom, end - removeFrom);
    return lines.join('\n');
}
/** Replace an existing [connection.<name>] block, or append it if not present. */
function upsertConnectionBlock(tomlText, name, block) {
    const cleaned = removeConnectionBlock(tomlText, name);
    const base = cleaned.trimEnd();
    if (!base)
        return block + '\n';
    return base + '\n\n' + block + '\n';
}
/** Multi-step input box sequence to collect / edit connection fields. */
async function promptConnectionFields(defaults, nameReadonly = false) {
    let name;
    if (nameReadonly) {
        name = defaults?.name ?? '';
    }
    else {
        const v = await vscode.window.showInputBox({
            title: 'KafkaSQL — Connection name',
            prompt: 'Unique identifier (letters, digits, hyphens, dots, underscores)',
            value: defaults?.name ?? '',
            validateInput: s => /^[a-zA-Z0-9_.-]+$/.test(s.trim()) ? null : 'Use letters, digits, hyphens, dots, or underscores',
        });
        if (v === undefined)
            return undefined;
        name = v.trim();
    }
    const bootstrap = await vscode.window.showInputBox({
        title: 'KafkaSQL — Bootstrap servers',
        prompt: 'Kafka bootstrap server(s), comma-separated (e.g. localhost:9092)',
        value: defaults?.bootstrap ?? 'localhost:9092',
        validateInput: s => validateTomlSafe('Bootstrap servers', s),
    });
    if (bootstrap === undefined)
        return undefined;
    const topic = await vscode.window.showInputBox({
        title: 'KafkaSQL — Event-log topic',
        prompt: 'KafkaSQL event-log topic name',
        value: defaults?.topic ?? '_kafkasql_log',
        validateInput: s => validateTomlSafe('Topic', s),
    });
    if (topic === undefined)
        return undefined;
    const username = await vscode.window.showInputBox({
        title: 'KafkaSQL — Username (optional)',
        prompt: 'SASL/SCRAM-SHA-256 username — leave empty for PLAINTEXT',
        value: defaults?.username ?? '',
        validateInput: s => validateTomlSafe('Username', s, false),
    });
    if (username === undefined)
        return undefined;
    let password = '';
    if (username.trim()) {
        const pw = await vscode.window.showInputBox({
            title: 'KafkaSQL — Password',
            prompt: 'SASL/SCRAM-SHA-256 password',
            value: defaults?.password ?? '',
            password: true,
            validateInput: s => validateTomlSafe('Password', s),
        });
        if (pw === undefined)
            return undefined;
        password = pw;
    }
    return { name, bootstrap: bootstrap.trim(), topic: topic.trim(), username: username.trim(), password };
}
/** Walks upward from `startDir` looking for the first `*.proj.toml` file. */
function findProjToml(startDir) {
    let dir = startDir;
    while (true) {
        try {
            const entries = fs.readdirSync(dir);
            const found = entries.find(e => e.endsWith('.proj.toml'));
            if (found)
                return path.join(dir, found);
        }
        catch { /* unreadable dir */ }
        const parent = path.dirname(dir);
        if (parent === dir)
            return null;
        dir = parent;
    }
}
/** Reads the `kafka` key from the `[project]` section of a `.proj.toml` file. */
function readKafkaDirFromProjToml(tomlPath) {
    const lines = fs.readFileSync(tomlPath, 'utf8').split('\n');
    let kafkaDir = 'model'; // project default
    let inProjectSection = false;
    for (const raw of lines) {
        const line = raw.trim();
        if (!line || line.startsWith('#'))
            continue;
        if (line.startsWith('[')) {
            const section = line.replace(/^\[/, '').replace(/\].*$/, '').trim();
            inProjectSection = section === 'project';
            continue;
        }
        if (!inProjectSection)
            continue;
        const eq = line.indexOf('=');
        if (eq < 0)
            continue;
        const key = line.substring(0, eq).trim().toLowerCase();
        if (key === 'kafka') {
            let val = line.substring(eq + 1).trim();
            if (val.startsWith('"')) {
                const close = val.indexOf('"', 1);
                val = close >= 0 ? val.substring(1, close) : val.substring(1);
            }
            else {
                const ci = val.indexOf('#');
                if (ci >= 0)
                    val = val.substring(0, ci).trim();
            }
            kafkaDir = val;
        }
    }
    return kafkaDir;
}
/**
 * Returns the effective mode for a file.
 * Priority: explicit per-file override → path-based auto-detection.
 *
 * Auto-detection:
 * - inside project model root → project
 * - outside model root (but in a project) → interactive
 * - no project → file
 */
function getFileMode(filePath, fileDir, uriKey) {
    if (uriKey && fileModeOverrides.has(uriKey)) {
        return fileModeOverrides.get(uriKey);
    }
    const projTomlPath = findProjToml(fileDir);
    if (!projTomlPath)
        return 'file'; // no project → file mode
    const projDir = path.dirname(projTomlPath);
    const kafkaDir = readKafkaDirFromProjToml(projTomlPath);
    const modelRoot = path.normalize(path.join(projDir, kafkaDir));
    const normalized = path.normalize(filePath);
    const insideModelRoot = normalized.startsWith(modelRoot + path.sep) || normalized === modelRoot;
    return insideModelRoot ? 'project' : 'interactive';
}
function activate(context) {
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
    function reg(id, handler) {
        try {
            context.subscriptions.push(vscode.commands.registerCommand(id, handler));
        }
        catch {
            lspOutput.appendLine(`[kafkasql-ext] Note: command '${id}' already registered (stale from previous session); handler not updated until next full reload.`);
        }
    }
    reg('kafkasql.startServer', () => startServer(context));
    // ── Restore persisted per-document state ──────────────────────────────────
    const savedModes = context.workspaceState.get(WORKSPACE_STATE_FILE_MODES_KEY, {});
    for (const [uri, mode] of Object.entries(savedModes)) {
        fileModeOverrides.set(uri, mode);
    }
    const savedPins = context.workspaceState.get(WORKSPACE_STATE_PINNED_CONNECTIONS_KEY, {});
    for (const [uri, pin] of Object.entries(savedPins)) {
        if (pin?.connectionName && pin?.projectFile && pin?.bootstrapServers) {
            pinnedConnections.set(uri, pin);
        }
    }
    const persistEditorRuntimeState = async () => {
        await context.workspaceState.update(WORKSPACE_STATE_FILE_MODES_KEY, Object.fromEntries(fileModeOverrides.entries()));
        await context.workspaceState.update(WORKSPACE_STATE_PINNED_CONNECTIONS_KEY, Object.fromEntries(pinnedConnections.entries()));
    };
    let explorer;
    // ── Compare commands ──────────────────────────────────────────────────────
    // Step 1: right-click a .kafka file → remember it as the left side
    reg('kafkasql.selectForCompare', (uri) => {
        compareBaseUri = uri;
        // Expose the context key so the "Compare with Selected" menu item becomes visible
        vscode.commands.executeCommand('setContext', 'kafkasql.compareBase', true);
        vscode.window.showInformationMessage(`KafkaSQL: selected ${path.basename(uri.fsPath)} as compare base`);
    });
    // Step 2: right-click another .kafka file → open native diff editor
    reg('kafkasql.compareWithSelected', (uri) => {
        if (!compareBaseUri) {
            vscode.window.showErrorMessage('KafkaSQL: no base file selected — use "Select for Compare" first');
            return;
        }
        const left = compareBaseUri;
        const right = uri;
        const title = `${path.basename(left.fsPath)} ↔ ${path.basename(right.fsPath)}`;
        vscode.commands.executeCommand('vscode.diff', left, right, title);
        runSemanticDiff(left, right);
    });
    // Bonus: from the active editor, open a file picker to choose the right side
    reg('kafkasql.compareWithActive', async (uri) => {
        const leftUri = uri ?? vscode.window.activeTextEditor?.document.uri;
        if (!leftUri) {
            vscode.window.showErrorMessage('KafkaSQL: no active file to compare');
            return;
        }
        const picks = await vscode.window.showOpenDialog({
            canSelectMany: false,
            filters: { 'KafkaSQL files': ['kafka', 'kafkasql'] }
        });
        if (!picks || picks.length === 0)
            return;
        const rightUri = picks[0];
        const title = `${path.basename(leftUri.fsPath)} ↔ ${path.basename(rightUri.fsPath)}`;
        vscode.commands.executeCommand('vscode.diff', leftUri, rightUri, title);
        runSemanticDiff(leftUri, rightUri);
    });
    reg('kafkasql.compareAgainst', async (uri) => {
        const invocationActiveUri = vscode.window.activeTextEditor?.document.uri;
        const includePattern = '**/*.{kafka,kafkasql}';
        const excludePattern = '{**/.git/**,**/node_modules/**,**/build/**,**/.gradle/**,**/dist/**}';
        const allKafkaFiles = (await vscode.workspace.findFiles(includePattern, excludePattern))
            .filter(u => u.scheme === 'file')
            .sort((a, b) => a.fsPath.localeCompare(b.fsPath));
        const projectTomlSet = new Set();
        const discoveredProjectTomls = (await vscode.workspace.findFiles('**/*.proj.toml', excludePattern))
            .filter(u => u.scheme === 'file');
        for (const proj of discoveredProjectTomls) {
            projectTomlSet.add(proj.fsPath);
        }
        const skipDirs = new Set(['.git', 'node_modules', 'build', '.gradle', 'dist']);
        const scanForProjTomls = (dir) => {
            let entries = [];
            try {
                entries = fs.readdirSync(dir, { withFileTypes: true });
            }
            catch {
                return;
            }
            for (const entry of entries) {
                const full = path.join(dir, entry.name);
                if (entry.isDirectory()) {
                    if (skipDirs.has(entry.name))
                        continue;
                    scanForProjTomls(full);
                    continue;
                }
                if (entry.isFile() && entry.name.endsWith('.proj.toml')) {
                    projectTomlSet.add(full);
                }
            }
        };
        for (const folder of vscode.workspace.workspaceFolders ?? []) {
            scanForProjTomls(folder.uri.fsPath);
        }
        for (const kafkaFile of allKafkaFiles) {
            const fileProjToml = findProjToml(path.dirname(kafkaFile.fsPath));
            if (fileProjToml)
                projectTomlSet.add(fileProjToml);
        }
        const activeUriForProjectDiscovery = uri ?? vscode.window.activeTextEditor?.document.uri;
        if (activeUriForProjectDiscovery?.scheme === 'file') {
            const activeProjToml = findProjToml(path.dirname(activeUriForProjectDiscovery.fsPath));
            if (activeProjToml)
                projectTomlSet.add(activeProjToml);
        }
        const docCandidates = vscode.workspace.textDocuments
            .filter(d => d.uri.scheme === 'file' && (d.languageId === 'kafkasql' || d.fileName.endsWith('.kafka') || d.fileName.endsWith('.kafkasql')))
            .map(d => d.uri.fsPath);
        if (uri?.scheme === 'file')
            docCandidates.push(uri.fsPath);
        if (invocationActiveUri?.scheme === 'file')
            docCandidates.push(invocationActiveUri.fsPath);
        for (const filePath of docCandidates) {
            const proj = findProjToml(path.dirname(filePath));
            if (proj)
                projectTomlSet.add(proj);
        }
        const ancestorDirs = new Set();
        for (const filePath of docCandidates) {
            let dir = path.dirname(filePath);
            for (let hops = 0; hops < 12; hops++) {
                ancestorDirs.add(dir);
                const parent = path.dirname(dir);
                if (parent === dir)
                    break;
                dir = parent;
            }
        }
        for (const dir of ancestorDirs) {
            let entries = [];
            try {
                entries = fs.readdirSync(dir);
            }
            catch {
                continue;
            }
            for (const name of entries) {
                if (name.endsWith('.proj.toml')) {
                    projectTomlSet.add(path.join(dir, name));
                }
            }
        }
        if (explorer) {
            try {
                await explorer.refresh();
            }
            catch (err) {
                lspOutput?.appendLine(`[kafkasql-ext] explorer refresh before compare failed: ${err}`);
            }
            for (const node of explorer.getProjectNodes()) {
                if (node?.projectFile)
                    projectTomlSet.add(node.projectFile);
            }
        }
        const projectTomls = Array.from(projectTomlSet)
            .sort((a, b) => a.localeCompare(b))
            .map(fsPath => vscode.Uri.file(fsPath));
        const optionsByType = {
            project: [],
            cluster: [],
            folder: [],
            file: [],
        };
        const optionById = new Map();
        let seq = 0;
        const addOpt = (type, opt) => {
            const id = `${type}-${++seq}`;
            const full = { id, ...opt };
            optionsByType[type].push(full);
            optionById.set(id, full);
        };
        for (const file of allKafkaFiles) {
            addOpt('file', {
                label: vscode.workspace.asRelativePath(file, false),
                detail: file.fsPath,
                kind: 'file',
                fileUri: file.toString(),
            });
        }
        for (const proj of projectTomls) {
            const projectFile = proj.fsPath;
            const projectName = path.basename(projectFile);
            const projectDir = path.dirname(projectFile);
            addOpt('project', {
                label: projectName,
                detail: projectFile,
                kind: 'project',
                projectFile,
            });
            const connectionsToml = path.join(projectDir, 'connections.toml');
            if (fs.existsSync(connectionsToml)) {
                for (const conn of parseConnectionsToml(connectionsToml)) {
                    addOpt('cluster', {
                        label: `${conn.name} (${conn.bootstrapServers})`,
                        detail: `${projectName} • topic: ${conn.topic}`,
                        kind: 'cluster',
                        projectFile,
                        connectionName: conn.name,
                    });
                }
            }
        }
        const activeUri = uri ?? vscode.window.activeTextEditor?.document.uri;
        const activeFileDefault = activeUri && activeUri.scheme === 'file'
            ? optionsByType.file.find(o => o.fileUri === activeUri.toString())
            : undefined;
        const hasWorkspaceFolder = (vscode.workspace.workspaceFolders?.length ?? 0) > 0;
        const pickDefaultType = () => {
            if (optionsByType.project.length > 0)
                return 'project';
            if (optionsByType.cluster.length > 0)
                return 'cluster';
            if (hasWorkspaceFolder)
                return 'folder';
            return 'file';
        };
        const defaultType = pickDefaultType();
        const firstSourceIdFor = (type) => {
            if (type === 'folder' || type === 'file')
                return null;
            return optionsByType[type][0]?.id ?? null;
        };
        const payload = {
            optionsByType,
            optionCounts: {
                project: optionsByType.project.length,
                cluster: optionsByType.cluster.length,
                folder: optionsByType.folder.length,
                file: optionsByType.file.length,
            },
            defaultLeftType: defaultType,
            defaultRightType: defaultType,
            defaultLeftId: defaultType === 'file'
                ? null
                : firstSourceIdFor(defaultType),
            defaultRightId: firstSourceIdFor(defaultType),
            defaultLeftPath: defaultType === 'file' ? (activeUri?.scheme === 'file' ? activeUri.fsPath : '') : '',
            defaultRightPath: '',
        };
        const panel = vscode.window.createWebviewPanel('kafkasqlCompareDialog', 'KafkaSQL Compare', vscode.ViewColumn.Active, { enableScripts: true, retainContextWhenHidden: false });
        panel.webview.html = renderCompareDialogHtml(payload);
        const sub = panel.webview.onDidReceiveMessage(async (msg) => {
            if (msg?.command === 'cancel') {
                panel.dispose();
                return;
            }
            if (msg?.command === 'browse') {
                const isFolder = msg.type === 'folder';
                const isProject = msg.type === 'project';
                const picks = await vscode.window.showOpenDialog({
                    canSelectMany: false,
                    canSelectFiles: !isFolder,
                    canSelectFolders: isFolder,
                    filters: isFolder
                        ? undefined
                        : (isProject ? { 'KafkaSQL project files': ['toml'] } : { 'KafkaSQL files': ['kafka', 'kafkasql'] }),
                    title: isFolder ? 'Select folder' : (isProject ? 'Select .proj.toml file' : 'Select file'),
                });
                if (!picks || picks.length === 0)
                    return;
                if (isProject && !picks[0].fsPath.endsWith('.proj.toml')) {
                    vscode.window.showErrorMessage('KafkaSQL: select a .proj.toml project file.');
                    return;
                }
                panel.webview.postMessage({
                    command: 'browseResult',
                    side: msg.side,
                    path: picks[0].fsPath,
                });
                return;
            }
            if (msg?.command !== 'compare')
                return;
            panel.webview.postMessage({ command: 'compareStarted' });
            const publishResult = (summary, entries, leftEndpoint, rightEndpoint) => {
                const sourceIndexCache = new Map();
                const collectKafkaFiles = (rootPath) => {
                    const out = [];
                    const walk = (dir) => {
                        let entries = [];
                        try {
                            entries = fs.readdirSync(dir, { withFileTypes: true });
                        }
                        catch {
                            return;
                        }
                        for (const entry of entries) {
                            const full = path.join(dir, entry.name);
                            if (entry.isDirectory()) {
                                walk(full);
                                continue;
                            }
                            if (entry.isFile() && (entry.name.endsWith('.kafka') || entry.name.endsWith('.kafkasql'))) {
                                out.push(full);
                            }
                        }
                    };
                    walk(rootPath);
                    return out.sort((a, b) => a.localeCompare(b));
                };
                const parseSourceIndex = (sourcePath) => {
                    if (!sourcePath)
                        return null;
                    if (sourceIndexCache.has(sourcePath))
                        return sourceIndexCache.get(sourcePath);
                    try {
                        if (!fs.existsSync(sourcePath))
                            return null;
                        const text = fs.readFileSync(sourcePath, 'utf8');
                        const lines = text.split(/\r?\n/);
                        const lineToObject = new Map();
                        const objectKeys = new Set();
                        const objectTypes = new Map();
                        const objectChildren = new Map();
                        const objectProperties = new Map();
                        let currentContext = '';
                        const normalizeContext = (ctx) => {
                            const trimmed = ctx.trim();
                            return trimmed.toUpperCase() === 'GLOBAL' ? '' : trimmed;
                        };
                        const findBody = (statementText, marker) => {
                            const start = statementText.search(marker);
                            if (start < 0)
                                return null;
                            const openIndex = statementText.indexOf('(', start);
                            if (openIndex < 0)
                                return null;
                            let depth = 0;
                            let closeIndex = -1;
                            for (let pos = openIndex; pos < statementText.length; pos++) {
                                const ch = statementText[pos];
                                if (ch === '(')
                                    depth++;
                                if (ch === ')') {
                                    depth--;
                                    if (depth === 0) {
                                        closeIndex = pos;
                                        break;
                                    }
                                }
                            }
                            if (closeIndex < 0)
                                return null;
                            return statementText.slice(openIndex + 1, closeIndex);
                        };
                        const parseStructFields = (statementText) => {
                            const body = findBody(statementText, /AS\s+STRUCT\s*\(/i);
                            if (!body)
                                return [];
                            const fields = [];
                            for (const rawLine of body.split(/\r?\n/)) {
                                const noComment = rawLine.replace(/--.*$/, '').trim();
                                if (!noComment)
                                    continue;
                                const clean = noComment.endsWith(',') ? noComment.slice(0, -1).trim() : noComment;
                                const match = /^([A-Za-z_]\w*)\s+(.+)$/.exec(clean);
                                if (!match)
                                    continue;
                                fields.push({ kind: 'FIELD', name: match[1], signature: match[2].trim() });
                            }
                            return fields;
                        };
                        const parseStructFieldsFromBody = (body, prefix = '') => {
                            const fields = [];
                            for (const rawLine of body.split(/\r?\n/)) {
                                const noComment = rawLine.replace(/--.*$/, '').trim();
                                if (!noComment)
                                    continue;
                                const clean = noComment.endsWith(',') ? noComment.slice(0, -1).trim() : noComment;
                                const match = /^([A-Za-z_]\w*)\s+(.+)$/.exec(clean);
                                if (!match)
                                    continue;
                                const fieldName = prefix ? `${prefix}.${match[1]}` : match[1];
                                fields.push({ kind: 'FIELD', name: fieldName, signature: match[2].trim() });
                            }
                            return fields;
                        };
                        const parseEnumSymbols = (statementText) => {
                            const body = findBody(statementText, /AS\s+ENUM\s*\(/i);
                            if (!body)
                                return [];
                            const symbols = [];
                            for (const rawLine of body.split(/\r?\n/)) {
                                const noComment = rawLine.replace(/--.*$/, '').trim();
                                if (!noComment)
                                    continue;
                                if (/^COMMENT\b/i.test(noComment))
                                    continue;
                                const clean = noComment.endsWith(',') ? noComment.slice(0, -1).trim() : noComment;
                                const match = /^([A-Za-z_]\w*)\s*=\s*(.+)$/.exec(clean);
                                if (!match)
                                    continue;
                                symbols.push({ kind: 'SYMBOL', name: match[1], signature: match[2].trim() });
                            }
                            return symbols;
                        };
                        const parseUnionMembers = (statementText) => {
                            const body = findBody(statementText, /AS\s+UNION\s*\(/i);
                            if (!body)
                                return [];
                            const fields = [];
                            for (const rawLine of body.split(/\r?\n/)) {
                                const noComment = rawLine.replace(/--.*$/, '').trim();
                                if (!noComment)
                                    continue;
                                const clean = noComment.endsWith(',') ? noComment.slice(0, -1).trim() : noComment;
                                const match = /^([A-Za-z_]\w*)\s+(.+)$/.exec(clean);
                                if (!match)
                                    continue;
                                fields.push({ kind: 'MEMBER', name: match[1], signature: match[2].trim() });
                            }
                            return fields;
                        };
                        const parseStreamChildren = (statementText) => {
                            const body = findBody(statementText, /CREATE\s+STREAM\b/i);
                            if (!body)
                                return [];
                            const children = [];
                            const memberRegex = /TYPE\s+([A-Za-z_]\w*)\s+AS\s+/gi;
                            let match;
                            while ((match = memberRegex.exec(body)) !== null) {
                                const memberName = match[1];
                                const afterAs = body.slice(memberRegex.lastIndex).trimStart();
                                if (afterAs.toUpperCase().startsWith('STRUCT')) {
                                    const structOffset = body.slice(memberRegex.lastIndex).search(/STRUCT\s*\(/i);
                                    if (structOffset < 0) {
                                        children.push({ kind: 'TYPE', name: memberName, signature: 'STRUCT' });
                                        continue;
                                    }
                                    const structStartInBody = memberRegex.lastIndex + structOffset;
                                    const openIndex = body.indexOf('(', structStartInBody);
                                    if (openIndex < 0) {
                                        children.push({ kind: 'TYPE', name: memberName, signature: 'STRUCT' });
                                        continue;
                                    }
                                    let depth = 0;
                                    let closeIndex = -1;
                                    for (let pos = openIndex; pos < body.length; pos++) {
                                        const ch = body[pos];
                                        if (ch === '(')
                                            depth++;
                                        if (ch === ')') {
                                            depth--;
                                            if (depth === 0) {
                                                closeIndex = pos;
                                                break;
                                            }
                                        }
                                    }
                                    if (closeIndex < 0) {
                                        children.push({ kind: 'TYPE', name: memberName, signature: 'STRUCT' });
                                        continue;
                                    }
                                    children.push({ kind: 'STRUCT', name: memberName, signature: 'STRUCT' });
                                    const structBody = body.slice(openIndex + 1, closeIndex);
                                    children.push(...parseStructFieldsFromBody(structBody, memberName));
                                    memberRegex.lastIndex = closeIndex + 1;
                                    continue;
                                }
                                const lineRemainder = body.slice(memberRegex.lastIndex).split('\n', 1)[0].trim();
                                const cleaned = lineRemainder.replace(/--.*$/, '').trim();
                                if (cleaned) {
                                    children.push({ kind: 'TYPE', name: memberName, signature: cleaned });
                                }
                                else {
                                    children.push({ kind: 'TYPE', name: memberName, signature: '' });
                                }
                            }
                            return children;
                        };
                        const resolveTypeInstance = (statementText) => {
                            if (/AS\s+STRUCT\b/i.test(statementText))
                                return 'STRUCT';
                            if (/AS\s+ENUM\b/i.test(statementText))
                                return 'ENUM';
                            if (/AS\s+UNION\b/i.test(statementText))
                                return 'UNION';
                            if (/AS\s+SCALAR\b/i.test(statementText))
                                return 'SCALAR';
                            return 'DERIVED';
                        };
                        const parseObjectProperties = (statementText, declKind, instanceType) => {
                            const props = {};
                            const defaultMatch = statementText.match(/\bDEFAULT\b\s+(.+?)(?=(\bCOMMENT\b|$))/is);
                            if (defaultMatch?.[1]?.trim())
                                props.DEFAULT = defaultMatch[1].trim().replace(/\s+/g, ' ');
                            const commentMatch = statementText.match(/\bCOMMENT\b\s+(['"])(.*?)\1/is);
                            if (commentMatch?.[2] !== undefined)
                                props.COMMENT = commentMatch[2].trim();
                            if (declKind === 'STREAM') {
                                const distributeMatch = statementText.match(/\bDISTRIBUTE\s+BY\s*\(([^)]*)\)/i);
                                if (distributeMatch?.[1]?.trim())
                                    props.DISTRIBUTE = distributeMatch[1].trim().replace(/\s+/g, ' ');
                            }
                            if (declKind === 'TYPE') {
                                if (instanceType === 'SCALAR') {
                                    const scalarMatch = statementText.match(/AS\s+SCALAR\s+(.+?)(?=(\bDEFAULT\b|\bCOMMENT\b|$))/is);
                                    if (scalarMatch?.[1]?.trim())
                                        props.BASE = scalarMatch[1].trim().replace(/\s+/g, ' ');
                                }
                                else if (instanceType === 'DERIVED') {
                                    const targetMatch = statementText.match(/CREATE\s+TYPE\s+[A-Za-z_]\w*\s+AS\s+(.+?)(?=(\bDEFAULT\b|\bCOMMENT\b|$))/is);
                                    if (targetMatch?.[1]?.trim())
                                        props.TARGET = targetMatch[1].trim().replace(/\s+/g, ' ');
                                }
                            }
                            return props;
                        };
                        const statementHeader = /^\s*(CREATE|USE|ALTER|DROP|READ|WRITE|SHOW|EXPLAIN)\b/i;
                        for (let i = 0; i < lines.length; i++) {
                            const lineNo = i + 1;
                            const line = lines[i];
                            const useCtx = line.match(/^\s*USE\s+CONTEXT\s+([A-Za-z_][\w]*(?:\.[A-Za-z_][\w]*)*)\s*;?/i);
                            if (useCtx?.[1]) {
                                currentContext = normalizeContext(useCtx[1]);
                            }
                            const createDecl = line.match(/^\s*CREATE\s+(TYPE|STREAM|CONTEXT)\s+([A-Za-z_][\w]*)\b/i);
                            if (!createDecl)
                                continue;
                            const declKind = createDecl[1].toUpperCase();
                            const declName = createDecl[2];
                            const scopedContext = normalizeContext(currentContext);
                            const fqObject = declKind === 'CONTEXT'
                                ? (scopedContext ? `${scopedContext}.${declName}` : declName)
                                : (scopedContext ? `${scopedContext}.${declName}` : declName);
                            objectKeys.add(fqObject);
                            let end = i + 1;
                            while (end < lines.length && !statementHeader.test(lines[end])) {
                                if (lines[end].includes(';')) {
                                    end++;
                                    break;
                                }
                                end++;
                            }
                            const statementText = lines.slice(i, Math.min(end, lines.length)).join('\n');
                            const instanceType = declKind === 'TYPE' ? resolveTypeInstance(statementText) : declKind;
                            if (!objectTypes.has(fqObject)) {
                                objectTypes.set(fqObject, instanceType);
                            }
                            if (!objectProperties.has(fqObject)) {
                                objectProperties.set(fqObject, parseObjectProperties(statementText, declKind, instanceType));
                            }
                            if (declKind === 'TYPE') {
                                const children = instanceType === 'STRUCT' ? parseStructFields(statementText)
                                    : instanceType === 'ENUM' ? parseEnumSymbols(statementText)
                                        : instanceType === 'UNION' ? parseUnionMembers(statementText)
                                            : [];
                                if (children.length > 0)
                                    objectChildren.set(fqObject, children);
                            }
                            else if (declKind === 'STREAM') {
                                const children = parseStreamChildren(statementText);
                                if (children.length > 0)
                                    objectChildren.set(fqObject, children);
                            }
                            for (let row = i + 1; row <= Math.max(i + 1, end); row++) {
                                lineToObject.set(row, fqObject);
                            }
                        }
                        const idx = { lineToObject, objectKeys, objectTypes, objectChildren, objectProperties };
                        sourceIndexCache.set(sourcePath, idx);
                        return idx;
                    }
                    catch (err) {
                        lspOutput?.appendLine(`[kafkasql-ext] compare source index failed for ${sourcePath}: ${err}`);
                        return null;
                    }
                };
                const objectKeyFromRange = (range) => {
                    const line = range?.from?.ln;
                    if (!range?.source || !line || line <= 0)
                        return null;
                    const idx = parseSourceIndex(range.source);
                    return idx?.lineToObject.get(line) ?? null;
                };
                const extractObjectKey = (entry) => {
                    const rightKey = objectKeyFromRange(entry.rightRange);
                    const leftKey = objectKeyFromRange(entry.leftRange);
                    if (entry.kind === 'LEFT_ONLY' && leftKey)
                        return leftKey;
                    if (entry.kind === 'RIGHT_ONLY' && rightKey)
                        return rightKey;
                    if (rightKey && leftKey)
                        return rightKey === leftKey ? rightKey : `${leftKey} ↔ ${rightKey}`;
                    if (rightKey)
                        return rightKey;
                    if (leftKey)
                        return leftKey;
                    const quoted = /'([^']+)'/.exec(entry.message);
                    if (quoted?.[1])
                        return quoted[1];
                    return null;
                };
                const keysAndTypesForEndpoint = (endpoint) => {
                    const keyInfo = new Map();
                    if (!endpoint)
                        return keyInfo;
                    const files = endpoint.kind === 'file' ? [endpoint.uri.fsPath]
                        : endpoint.kind === 'folder' ? collectKafkaFiles(endpoint.folderPath)
                            : endpoint.kind === 'project'
                                ? (() => {
                                    const projectDir = path.dirname(endpoint.projectFile);
                                    const kafkaDir = readKafkaDirFromProjToml(endpoint.projectFile);
                                    return collectKafkaFiles(path.join(projectDir, kafkaDir));
                                })()
                                : [];
                    for (const file of files) {
                        const idx = parseSourceIndex(file);
                        if (!idx)
                            continue;
                        for (const key of idx.objectKeys) {
                            if (!keyInfo.has(key)) {
                                keyInfo.set(key, {
                                    type: idx.objectTypes.get(key) ?? '',
                                    children: idx.objectChildren.get(key) ?? [],
                                    properties: idx.objectProperties.get(key) ?? {},
                                });
                            }
                        }
                    }
                    return keyInfo;
                };
                const leftKeyTypes = keysAndTypesForEndpoint(leftEndpoint);
                const rightKeyTypes = keysAndTypesForEndpoint(rightEndpoint);
                const leftKeys = new Set(leftKeyTypes.keys());
                const rightKeys = new Set(rightKeyTypes.keys());
                for (const entry of entries) {
                    const leftKey = objectKeyFromRange(entry.leftRange);
                    const rightKey = objectKeyFromRange(entry.rightRange);
                    if (entry.kind === 'LEFT_ONLY') {
                        if (leftKey)
                            leftKeys.add(leftKey);
                        else {
                            const fallback = extractObjectKey(entry);
                            if (fallback) {
                                leftKeys.add(fallback);
                            }
                        }
                        continue;
                    }
                    if (entry.kind === 'RIGHT_ONLY') {
                        if (rightKey)
                            rightKeys.add(rightKey);
                        else {
                            const fallback = extractObjectKey(entry);
                            if (fallback) {
                                rightKeys.add(fallback);
                            }
                        }
                        continue;
                    }
                    if (leftKey)
                        leftKeys.add(leftKey);
                    if (rightKey)
                        rightKeys.add(rightKey);
                    if (!leftKey && !rightKey) {
                        const k = extractObjectKey(entry);
                        if (k) {
                            leftKeys.add(k);
                            rightKeys.add(k);
                        }
                    }
                }
                const allKeys = Array.from(new Set([...leftKeys, ...rightKeys])).sort((a, b) => a.localeCompare(b));
                const rows = allKeys.map((key) => {
                    const leftPresent = leftKeys.has(key);
                    const rightPresent = rightKeys.has(key);
                    const leftType = leftPresent ? (leftKeyTypes.get(key)?.type ?? '') : '';
                    const rightType = rightPresent ? (rightKeyTypes.get(key)?.type ?? '') : '';
                    const op = leftPresent && rightPresent
                        ? (leftType === rightType ? '==' : '!=')
                        : (leftPresent ? '<<' : '>>');
                    return {
                        leftKey: leftPresent ? key : '',
                        leftType,
                        leftChildren: leftPresent ? (leftKeyTypes.get(key)?.children ?? []) : [],
                        leftProperties: leftPresent ? (leftKeyTypes.get(key)?.properties ?? {}) : {},
                        op,
                        rightKey: rightPresent ? key : '',
                        rightType,
                        rightChildren: rightPresent ? (rightKeyTypes.get(key)?.children ?? []) : [],
                        rightProperties: rightPresent ? (rightKeyTypes.get(key)?.properties ?? {}) : {},
                    };
                });
                panel.webview.postMessage({
                    command: 'compareResult',
                    summary: `${rows.length} key(s)`,
                    rows: rows.slice(0, 1000),
                });
            };
            const publishError = (message) => {
                panel.webview.postMessage({ command: 'compareError', message });
            };
            const publishNoResultError = () => {
                publishError('KafkaSQL: comparison failed. See KafkaSQL output for details.');
            };
            const resolveEndpoint = (side) => {
                const sideType = side === 'Left' ? msg.leftType : msg.rightType;
                const sideId = side === 'Left' ? msg.leftId : msg.rightId;
                const sidePath = side === 'Left' ? msg.leftPath : msg.rightPath;
                if (sideType === 'file') {
                    if (!sidePath)
                        return null;
                    return { kind: 'file', uri: vscode.Uri.file(sidePath), label: `file:${path.basename(sidePath)}` };
                }
                if (sideType === 'folder') {
                    if (!sidePath)
                        return null;
                    return { kind: 'folder', folderPath: sidePath, label: `folder:${path.basename(sidePath)}` };
                }
                if (sideType === 'project') {
                    if (sidePath) {
                        return { kind: 'project', projectFile: sidePath, label: `project:${path.basename(sidePath)}` };
                    }
                    const option = optionById.get(sideId);
                    if (!option)
                        return null;
                    return { kind: 'project', projectFile: option.projectFile, label: `project:${path.basename(option.projectFile)}` };
                }
                const option = optionById.get(sideId);
                if (!option)
                    return null;
                return {
                    kind: 'cluster',
                    projectFile: option.projectFile,
                    connectionName: option.connectionName,
                    label: `cluster:${option.connectionName}`,
                };
            };
            const left = resolveEndpoint('Left');
            const right = resolveEndpoint('Right');
            if (!left || !right) {
                const msg = 'KafkaSQL: select both left and right sources.';
                vscode.window.showErrorMessage(msg);
                publishError(msg);
                return;
            }
            if (left.kind === 'file' && right.kind === 'file' && left.uri.fsPath === right.uri.fsPath) {
                const msg = 'KafkaSQL: left and right files must be different.';
                vscode.window.showErrorMessage(msg);
                publishError(msg);
                return;
            }
            resultsChannel.show(true);
            resultsChannel.appendLine('\n── Compare ──');
            resultsChannel.appendLine(new Date().toISOString());
            resultsChannel.appendLine(`Left : ${left.label}`);
            resultsChannel.appendLine(`Right: ${right.label}`);
            if (left.kind === 'cluster' && right.kind === 'cluster') {
                const msg = 'KafkaSQL: cluster-to-cluster comparison is not supported yet.';
                vscode.window.showErrorMessage(msg);
                resultsChannel.appendLine(`ERROR: ${msg}`);
                publishError(msg);
                return;
            }
            if (left.kind === 'file' && right.kind === 'file') {
                const title = `${path.basename(left.uri.fsPath)} ↔ ${path.basename(right.uri.fsPath)}`;
                await vscode.commands.executeCommand('vscode.diff', left.uri, right.uri, title);
                const summary = await runSemanticDiff(left.uri, right.uri);
                if (!summary) {
                    publishNoResultError();
                    return;
                }
                resultsChannel.appendLine(`Result: ${summary.total} change(s), ${summary.breaking} breaking`);
                publishResult(`${summary.total} change(s), ${summary.breaking} breaking`, summary.entries, left, right);
                return;
            }
            if (left.kind !== 'cluster' && right.kind !== 'cluster') {
                const leftType = left.kind;
                const rightType = right.kind;
                const leftRef = left.kind === 'file' ? left.uri.fsPath :
                    left.kind === 'project' ? left.projectFile :
                        left.folderPath;
                const rightRef = right.kind === 'file' ? right.uri.fsPath :
                    right.kind === 'project' ? right.projectFile :
                        right.folderPath;
                const entries = await runCompositeSemanticDiff(leftType, leftRef, rightType, rightRef);
                if (!entries) {
                    publishNoResultError();
                    return;
                }
                const changed = entries.filter(e => e.kind !== 'UNCHANGED');
                const breaking = changed.filter(e => e.severity === 'BREAKING').length;
                resultsChannel.appendLine(`Result: ${changed.length} change(s), ${breaking} breaking`);
                publishResult(`${changed.length} change(s), ${breaking} breaking`, changed, left, right);
                if (changed.length === 0) {
                    vscode.window.showInformationMessage('KafkaSQL: no compatibility issues detected');
                }
                else {
                    const msg = breaking > 0
                        ? `KafkaSQL: ${breaking} breaking change(s), ${changed.length} total — see output`
                        : `KafkaSQL: ${changed.length} change(s) — see output`;
                    vscode.window.showInformationMessage(msg);
                }
                return;
            }
            if (left.kind === 'project' && right.kind === 'cluster') {
                const summary = await runProjectClusterDiff(left.projectFile, right.connectionName);
                if (!summary) {
                    publishNoResultError();
                    return;
                }
                resultsChannel.appendLine(`Result: ${summary.total} difference(s), ${summary.breaking} breaking`);
                publishResult(`${summary.total} difference(s), ${summary.breaking} breaking`, summary.entries, left, right);
                return;
            }
            if (left.kind === 'cluster' && right.kind === 'project') {
                const summary = await runProjectClusterDiff(right.projectFile, left.connectionName);
                if (!summary) {
                    publishNoResultError();
                    return;
                }
                resultsChannel.appendLine(`Result: ${summary.total} difference(s), ${summary.breaking} breaking`);
                publishResult(`${summary.total} difference(s), ${summary.breaking} breaking`, summary.entries, left, right);
                return;
            }
            let summary = null;
            if (left.kind === 'cluster' && right.kind === 'file') {
                summary = await runClusterDiff(right.uri, left.projectFile, left.connectionName, left.connectionName);
            }
            else if (left.kind === 'file' && right.kind === 'cluster') {
                summary = await runClusterDiff(left.uri, right.projectFile, right.connectionName, right.connectionName);
            }
            if (!summary) {
                publishNoResultError();
                return;
            }
            resultsChannel.appendLine(`Result: ${summary.total} difference(s), ${summary.breaking} breaking`);
            publishResult(`${summary.total} difference(s), ${summary.breaking} breaking`, summary.entries, left, right);
        });
        panel.onDidDispose(() => sub.dispose());
    });
    // ── Comparison mode status bar ─────────────────────────────────────────────
    modeStatusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
    modeStatusBar.command = 'kafkasql.changeComparisonMode';
    modeStatusBar.tooltip = 'KafkaSQL: click to change compatibility check mode';
    modeStatusBar.text = modeLabel(currentMode);
    modeStatusBar.show();
    context.subscriptions.push(modeStatusBar);
    reg('kafkasql.changeComparisonMode', () => pickComparisonMode());
    // ── LSP command helper ──────────────────────────────────────────────────
    const sendLspCommand = (cmd, args) => {
        if (!client)
            return Promise.reject(new Error('LSP not ready'));
        return client.sendRequest('workspace/executeCommand', { command: cmd, arguments: args });
    };
    // ── File mode status bar ────────────────────────────────────────────────────
    // Shows whether the active .kafka file is in File/Project/Interactive mode.
    // Click to override the auto-detected mode for this file.
    fileModeStatusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 101);
    fileModeStatusBar.command = 'kafkasql.pickFileMode';
    context.subscriptions.push(fileModeStatusBar);
    // ── Execution stop status bar ──────────────────────────────────────────────
    connectionStatusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 102);
    connectionStatusBar.command = 'kafkasql.pickInteractiveConnection';
    context.subscriptions.push(connectionStatusBar);
    // ── Execution stop status bar ──────────────────────────────────────────────
    executionStatusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 103);
    executionStatusBar.command = 'kafkasql.cancelExecution';
    executionStatusBar.text = '$(stop-circle) Stop';
    executionStatusBar.tooltip = 'KafkaSQL: cancel the running query';
    executionStatusBar.backgroundColor = new vscode.ThemeColor('statusBarItem.warningBackground');
    context.subscriptions.push(executionStatusBar);
    function updateFileModeStatusBar(editor) {
        const doc = editor?.document;
        const isKafka = doc && (doc.languageId === 'kafkasql'
            || doc.fileName.endsWith('.kafka')
            || doc.fileName.endsWith('.kafkasql'));
        if (!isKafka) {
            fileModeStatusBar.hide();
            return;
        }
        const uriKey = doc.uri.toString();
        const overridden = fileModeOverrides.has(uriKey);
        const mode = inferInteractiveRuntime(doc).mode;
        const pin = overridden ? ' $(pinned)' : '';
        if (mode === 'interactive') {
            fileModeStatusBar.text = `$(symbol-event) Interactive${pin}`;
            fileModeStatusBar.tooltip = `KafkaSQL: Interactive mode${overridden ? ' (manually set)' : ' (auto-detected)'}\n`
                + 'INCLUDE statements are ignored; types are resolved from the live Kafka cluster.\n'
                + 'Click to change.';
        }
        else if (mode === 'project') {
            fileModeStatusBar.text = `$(repo) Project mode${pin}`;
            fileModeStatusBar.tooltip = `KafkaSQL: Project mode${overridden ? ' (manually set)' : ' (auto-detected)'}\n`
                + 'Project conventions and local model resolution are enabled.\n'
                + 'Click to change.';
        }
        else {
            fileModeStatusBar.text = `$(file-code) File mode${pin}`;
            fileModeStatusBar.tooltip = `KafkaSQL: File mode${overridden ? ' (manually set)' : ' (auto-detected)'}\n`
                + 'Types are resolved from local sources without project conventions.\n'
                + 'Click to change.';
        }
        fileModeStatusBar.show();
    }
    function inferInteractiveRuntime(doc) {
        const isKafka = doc.languageId === 'kafkasql'
            || doc.fileName.endsWith('.kafka')
            || doc.fileName.endsWith('.kafkasql');
        if (!isKafka)
            return { mode: 'file', pinned: undefined, activeDir: '', isKafka: false };
        const uriKey = doc.uri.toString();
        const isUntitledFile = doc.uri.scheme === 'untitled';
        const pinned = pinnedConnections.get(uriKey);
        const defaultActiveDir = isUntitledFile
            ? (vscode.workspace.workspaceFolders?.[0]?.uri.fsPath ?? path.dirname(doc.fileName))
            : path.dirname(doc.fileName);
        const activeDir = pinned ? path.dirname(pinned.projectFile) : defaultActiveDir;
        const mode = isUntitledFile
            ? (fileModeOverrides.get(uriKey) ?? 'interactive')
            : getFileMode(doc.fileName, activeDir, uriKey);
        return { mode, pinned, activeDir, isKafka: true };
    }
    function updateConnectionStatusBar(editor) {
        const doc = editor?.document;
        if (!doc) {
            connectionStatusBar.hide();
            return;
        }
        const runtime = inferInteractiveRuntime(doc);
        if (!runtime.isKafka || runtime.mode !== 'interactive') {
            connectionStatusBar.hide();
            return;
        }
        if (runtime.pinned) {
            connectionStatusBar.text = `$(plug) ${runtime.pinned.connectionName}`;
            connectionStatusBar.tooltip = `KafkaSQL interactive connection: ${runtime.pinned.connectionName} (${runtime.pinned.bootstrapServers})\nClick to change connection.`;
        }
        else {
            connectionStatusBar.text = '$(plug) Select connection';
            connectionStatusBar.tooltip = 'KafkaSQL interactive connection is not set.\nClick to select a connection.';
        }
        connectionStatusBar.show();
    }
    reg('kafkasql.pickInteractiveConnection', async () => {
        const editor = vscode.window.activeTextEditor;
        const doc = editor?.document;
        if (!doc)
            return;
        const runtime = inferInteractiveRuntime(doc);
        if (!runtime.isKafka || runtime.mode !== 'interactive') {
            vscode.window.showInformationMessage('KafkaSQL: active file is not in interactive mode.');
            return;
        }
        const tomlPath = findConnectionsToml(runtime.activeDir);
        if (!tomlPath) {
            vscode.window.showErrorMessage('KafkaSQL: no connections.toml found — add one alongside your .proj.toml');
            return;
        }
        const connections = parseConnectionsToml(tomlPath);
        if (connections.length === 0) {
            vscode.window.showErrorMessage('KafkaSQL: connections.toml has no valid connections');
            return;
        }
        const items = connections.map(c => ({
            label: c.name,
            description: c.bootstrapServers,
            detail: `topic: ${c.topic}${runtime.pinned?.connectionName === c.name ? ' • current' : ''}`,
            name: c.name,
            bootstrap: c.bootstrapServers,
        }));
        const orderedItems = runtime.pinned
            ? [...items.filter(i => i.name === runtime.pinned.connectionName), ...items.filter(i => i.name !== runtime.pinned.connectionName)]
            : items;
        const picked = await vscode.window.showQuickPick(orderedItems, {
            placeHolder: 'Interactive mode: choose connection',
            matchOnDescription: true,
        });
        if (!picked)
            return;
        const fallbackFilePath = doc.uri.scheme === 'untitled'
            ? (vscode.workspace.workspaceFolders?.[0]?.uri.fsPath ?? '')
            : doc.uri.fsPath;
        const projectFileForConnection = runtime.pinned?.projectFile
            ?? findProjToml(path.dirname(tomlPath))
            ?? fallbackFilePath;
        pinnedConnections.set(doc.uri.toString(), {
            connectionName: picked.name,
            projectFile: projectFileForConnection,
            bootstrapServers: picked.bootstrap,
        });
        fileModeOverrides.set(doc.uri.toString(), 'interactive');
        if (client) {
            try {
                await sendLspCommand('kafkasql.setFileMode', [doc.uri.toString(), 'interactive']);
            }
            catch (err) {
                lspOutput?.appendLine(`[kafkasql-ext] setFileMode interactive after connection pick failed: ${err}`);
            }
        }
        updateConnectionStatusBar(editor);
        updateFileModeStatusBar(editor);
    });
    reg('kafkasql.pickFileMode', async () => {
        const editor = vscode.window.activeTextEditor;
        if (!editor)
            return;
        const doc = editor.document;
        const isKafka = doc.languageId === 'kafkasql'
            || doc.fileName.endsWith('.kafka')
            || doc.fileName.endsWith('.kafkasql');
        if (!isKafka)
            return;
        const uriKey = doc.uri.toString();
        const currentMode = inferInteractiveRuntime(doc).mode;
        const isOverridden = fileModeOverrides.has(uriKey);
        const items = [
            {
                label: '$(symbol-event) Interactive mode',
                description: 'Semantic model backed by the live Kafka cluster',
                detail: currentMode === 'interactive' && !isOverridden ? '$(check) current (auto-detected)' : (currentMode === 'interactive' && isOverridden ? '$(check) current (manually set)' : undefined),
                value: 'interactive',
            },
            {
                label: '$(repo) Project mode',
                description: 'Local model resolution with project conventions enabled',
                detail: currentMode === 'project' && !isOverridden ? '$(check) current (auto-detected)' : (currentMode === 'project' && isOverridden ? '$(check) current (manually set)' : undefined),
                value: 'project',
            },
            {
                label: '$(file-code) File mode',
                description: 'Types resolved from local sources without project conventions',
                detail: currentMode === 'file' && !isOverridden ? '$(check) current (auto-detected)' : (currentMode === 'file' && isOverridden ? '$(check) current (manually set)' : undefined),
                value: 'file',
            },
            {
                label: '$(sync) Auto-detect',
                description: 'Infer from file location (model root = Project, misc/ = Interactive, no project = File)',
                detail: !isOverridden ? '$(check) current' : undefined,
                value: 'auto',
            },
        ];
        const picked = await vscode.window.showQuickPick(items, {
            placeHolder: `KafkaSQL mode for ${path.basename(doc.fileName)}`,
        });
        if (!picked)
            return;
        if (picked.value === 'auto') {
            fileModeOverrides.delete(uriKey);
        }
        else {
            fileModeOverrides.set(uriKey, picked.value);
            if (picked.value !== 'interactive') {
                pinnedConnections.delete(uriKey);
            }
        }
        await persistEditorRuntimeState();
        updateFileModeStatusBar(editor);
        updateConnectionStatusBar(editor);
        // Also update the execute-statement mode so it matches what diagnostics are using.
        // Tell the LSP to re-run diagnostics with the new mode.
        if (client) {
            try {
                await sendLspCommand('kafkasql.setFileMode', [uriKey, picked.value]);
            }
            catch (err) {
                lspOutput?.appendLine(`[kafkasql-ext] setFileMode failed: ${err}`);
            }
        }
    });
    updateFileModeStatusBar(vscode.window.activeTextEditor);
    updateConnectionStatusBar(vscode.window.activeTextEditor);
    context.subscriptions.push(vscode.window.onDidChangeActiveTextEditor(editor => {
        updateFileModeStatusBar(editor);
        updateConnectionStatusBar(editor);
    }));
    context.subscriptions.push(vscode.workspace.onDidOpenTextDocument(() => {
        updateFileModeStatusBar(vscode.window.activeTextEditor);
        updateConnectionStatusBar(vscode.window.activeTextEditor);
    }));
    const out = vscode.window.createOutputChannel('KafkaSQL Debug');
    context.subscriptions.push(out);
    // log diagnostics that VS Code receives (helpful to verify message matching)
    const diagListener = vscode.languages.onDidChangeDiagnostics((e) => {
        for (const uri of e.uris ? e.uris : [ /*no uris provided in older APIs*/]) {
            const ds = vscode.languages.getDiagnostics(uri);
            out.appendLine(`[ext] diagnostics changed for ${uri.toString()} -> ${ds.length} entries`);
            for (const d of ds) {
                out.appendLine(`[ext] ${uri.toString()}: ${d.range.start.line + 1}:${d.range.start.character + 1} ${d.severity} ${d.source} ${d.message}`);
            }
        }
        // also log currently active editor URI for quick compare
        const active = vscode.window.activeTextEditor;
        if (active)
            out.appendLine(`[ext] active editor: ${active.document.uri.toString()}`);
    });
    context.subscriptions.push(diagListener);
    // ── Project explorer tree view ───────────────────────────────────────────────────
    // sendLspCommand is defined above (before the file mode status bar) so that
    // the pickFileMode command handler can use it.
    explorer = new kafkaSqlProjectExplorer_1.KafkaSqlProjectExplorer(context, sendLspCommand);
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
    reg('kafkasql.explorer.openFile', (filePath, line) => {
        vscode.window.showTextDocument(vscode.Uri.file(filePath), {
            selection: new vscode.Range(line, 0, line, 0),
            preview: false,
        });
    });
    // ── Cluster diff / deploy commands ────────────────────────────────────────
    reg('kafkasql.diffWithCluster', async (node) => {
        if (!node)
            return;
        const picks = await vscode.window.showOpenDialog({
            canSelectMany: false,
            filters: { 'KafkaSQL files': ['kafka', 'kafkasql'] },
            title: `Pick local schema file to diff against cluster "${node.label}"`
        });
        if (!picks || picks.length === 0)
            return;
        const localUri = picks[0];
        clusterDiffDiagnostics.delete(localUri);
        let result;
        try {
            result = await sendLspCommand('kafkasql.diffWithCluster', [node.projectFile, node.label, localUri.fsPath]);
        }
        catch (err) {
            vscode.window.showErrorMessage(`Diff with cluster failed: ${err}`);
            return;
        }
        if (!Array.isArray(result)) {
            if (result && typeof result === 'object' && 'error' in result) {
                vscode.window.showErrorMessage(`Diff with cluster failed: ${result.error}`);
            }
            return;
        }
        const entries = result;
        const diags = [];
        for (const entry of entries) {
            const rng = entry.rightRange ?? entry.leftRange;
            if (!rng)
                continue;
            const start = new vscode.Position(Math.max(0, rng.from.ln - 1), Math.max(0, rng.from.ch));
            const end = new vscode.Position(Math.max(0, rng.to.ln - 1), Math.max(0, rng.to.ch));
            const diag = new vscode.Diagnostic(new vscode.Range(start, end), entry.message, mapSeverity(entry.severity));
            diag.source = 'kafkasql-cluster-diff';
            diag.code = entry.aspect;
            diags.push(diag);
        }
        clusterDiffDiagnostics.set(localUri, diags);
        if (diags.length === 0) {
            vscode.window.showInformationMessage(`KafkaSQL: local schema matches cluster "${node.label}"`);
        }
        else {
            const breaking = diags.filter(d => d.severity === vscode.DiagnosticSeverity.Error).length;
            const msg = breaking > 0
                ? `KafkaSQL: ${breaking} breaking difference(s) vs cluster "${node.label}" — see Problems panel`
                : `KafkaSQL: ${diags.length} difference(s) vs cluster "${node.label}" — see Problems panel`;
            vscode.window.showInformationMessage(msg);
        }
    }),
        vscode.commands.registerCommand('kafkasql.deployToCluster', async (node) => {
            if (!node)
                return;
            const picks = await vscode.window.showOpenDialog({
                canSelectMany: false,
                filters: { 'KafkaSQL files': ['kafka', 'kafkasql'] },
                title: `Pick local schema file to deploy to cluster "${node.label}"`
            });
            if (!picks || picks.length === 0)
                return;
            const localUri = picks[0];
            // Run a quick diff first to show the change count in the confirmation dialog
            let diffCount = 0;
            try {
                const diffResult = await sendLspCommand('kafkasql.diffWithCluster', [node.projectFile, node.label, localUri.fsPath]);
                if (Array.isArray(diffResult)) {
                    diffCount = diffResult
                        .filter(e => e.kind !== 'UNCHANGED').length;
                }
            }
            catch { /* ignore — deploy will surface any connection errors */ }
            const confirm = await vscode.window.showWarningMessage(`Deploy ${diffCount} change(s) to cluster "${node.label}" (${node.bootstrapServers})?`, { modal: true }, 'Deploy');
            if (confirm !== 'Deploy')
                return;
            let result;
            try {
                result = await sendLspCommand('kafkasql.deployToCluster', [node.projectFile, node.label, localUri.fsPath]);
            }
            catch (err) {
                vscode.window.showErrorMessage(`Deploy to cluster failed: ${err}`);
                return;
            }
            if (result && typeof result === 'object' && 'error' in result) {
                vscode.window.showErrorMessage(`Deploy to cluster failed: ${result.error}`);
                return;
            }
            if (result && typeof result === 'object' && 'deployed' in result) {
                const r = result;
                vscode.window.showInformationMessage(`KafkaSQL: deployed ${r.deployed} event(s) to cluster "${node.label}"`);
            }
        });
    // ── Deploy project to a chosen connection ─────────────────────────────────
    // Right-click a ProjectNode → "Deploy to..." → pick connection → deploy all
    // kafka files in the project as a single atomic operation.
    reg('kafkasql.deployProjectToCluster', async (node) => {
        if (!node)
            return;
        const connections = explorer.getConnectionsForProject(node.label);
        if (connections.length === 0) {
            vscode.window.showErrorMessage(`KafkaSQL: project "${node.label}" has no connections defined in connections.toml`);
            return;
        }
        const items = connections.map(c => ({
            label: c.name,
            description: c.bootstrapServers,
            detail: `topic: ${c.topic}`,
            name: c.name,
        }));
        const picked = await vscode.window.showQuickPick(items, {
            placeHolder: `Deploy project "${node.label}" to which cluster?`,
            matchOnDescription: true,
        });
        if (!picked)
            return;
        // Dry-run diff to show change count in confirmation
        let diffCount = 0;
        try {
            const diffResult = await sendLspCommand('kafkasql.diffProjectWithCluster', [node.projectFile, picked.name]);
            if (Array.isArray(diffResult)) {
                diffCount = diffResult.filter(e => e.kind !== 'UNCHANGED').length;
            }
        }
        catch { /* ignore — deploy will surface any real errors */ }
        const confirm = await vscode.window.showWarningMessage(`Deploy project "${node.label}" (${diffCount} change(s)) to cluster "${picked.name}" (${picked.description})?`, { modal: true }, 'Deploy');
        if (confirm !== 'Deploy')
            return;
        let result;
        try {
            result = await sendLspCommand('kafkasql.deployProjectToCluster', [node.projectFile, picked.name]);
        }
        catch (err) {
            vscode.window.showErrorMessage(`Deploy project to cluster failed: ${err}`);
            return;
        }
        if (result && typeof result === 'object' && 'error' in result) {
            vscode.window.showErrorMessage(`Deploy project to cluster failed: ${result.error}`);
            return;
        }
        if (result && typeof result === 'object' && 'deployed' in result) {
            const r = result;
            vscode.window.showInformationMessage(`KafkaSQL: deployed ${r.deployed} event(s) from project "${node.label}" to cluster "${picked.name}"`);
        }
    });
    // ── New Query from sidebar connection ──────────────────────────────────────
    // Opens a new untitled KafkaSQL document pre-pinned to the chosen connection.
    reg('kafkasql.newQuery', async (node) => {
        if (!node)
            return;
        const doc = await vscode.workspace.openTextDocument({ language: 'kafkasql', content: '' });
        const uriKey = doc.uri.toString();
        pinnedConnections.set(doc.uri.toString(), {
            connectionName: node.label,
            projectFile: node.projectFile,
            bootstrapServers: node.bootstrapServers,
        });
        fileModeOverrides.set(uriKey, 'interactive');
        await persistEditorRuntimeState();
        if (client) {
            try {
                await sendLspCommand('kafkasql.setFileMode', [uriKey, 'interactive']);
            }
            catch (err) {
                lspOutput?.appendLine(`[kafkasql-ext] setFileMode interactive for new query failed: ${err}`);
            }
        }
        await vscode.window.showTextDocument(doc, { preview: false });
        updateConnectionStatusBar(vscode.window.activeTextEditor);
        vscode.window.setStatusBarMessage(`KafkaSQL: new query on "${node.label}" (${node.bootstrapServers})`, 4000);
    });
    context.subscriptions.push(vscode.workspace.onDidCloseTextDocument(doc => {
        pinnedConnections.delete(doc.uri.toString());
        fileModeOverrides.delete(doc.uri.toString());
        void persistEditorRuntimeState();
        updateConnectionStatusBar(vscode.window.activeTextEditor);
    }));
    context.subscriptions.push(vscode.workspace.onDidRenameFiles(event => {
        for (const file of event.files) {
            const existing = pinnedConnections.get(file.oldUri.toString());
            if (existing) {
                pinnedConnections.delete(file.oldUri.toString());
                pinnedConnections.set(file.newUri.toString(), existing);
            }
            const existingMode = fileModeOverrides.get(file.oldUri.toString());
            if (existingMode) {
                fileModeOverrides.delete(file.oldUri.toString());
                fileModeOverrides.set(file.newUri.toString(), existingMode);
            }
        }
        void persistEditorRuntimeState();
        updateConnectionStatusBar(vscode.window.activeTextEditor);
    }));
    // ── Add Connection ────────────────────────────────────────────────────────
    // Can be invoked from the Clusters root toolbar button or from the command palette.
    reg('kafkasql.addConnection', async () => {
        // Find all project directories that have (or could have) a connections.toml
        const projTomlUris = await vscode.workspace.findFiles('**/*.proj.toml', '{.git,node_modules,build,.gradle,dist}/**');
        let targetToml;
        if (projTomlUris.length === 0) {
            // No project files — write to workspace root
            const wsRoot = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
            if (!wsRoot) {
                vscode.window.showErrorMessage('KafkaSQL: no workspace folder open');
                return;
            }
            targetToml = path.join(wsRoot, 'connections.toml');
        }
        else if (projTomlUris.length === 1) {
            targetToml = path.join(path.dirname(projTomlUris[0].fsPath), 'connections.toml');
        }
        else {
            const items = projTomlUris.map(u => ({
                label: path.basename(path.dirname(u.fsPath)),
                description: path.dirname(u.fsPath),
                tomlPath: path.join(path.dirname(u.fsPath), 'connections.toml'),
            }));
            const picked = await vscode.window.showQuickPick(items, {
                placeHolder: 'Add connection to which project?',
            });
            if (!picked)
                return;
            targetToml = picked.tomlPath;
        }
        const fields = await promptConnectionFields();
        if (!fields)
            return;
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
    reg('kafkasql.editConnection', async (node) => {
        if (!node)
            return;
        const tomlPath = findConnectionsToml(path.dirname(node.projectFile));
        if (!tomlPath) {
            vscode.window.showErrorMessage('KafkaSQL: connections.toml not found');
            return;
        }
        const fields = await promptConnectionFields({
            name: node.label,
            bootstrap: node.bootstrapServers,
            topic: node.topic,
            username: node.username ?? '',
            password: node.password ?? '',
        }, /* nameReadonly */ true);
        if (!fields)
            return;
        maybeWarnOnStoredCredentials(fields);
        const raw = fs.readFileSync(tomlPath, 'utf8');
        const block = serializeConnectionBlock(fields.name, fields.bootstrap, fields.topic, fields.username || undefined, fields.password || undefined);
        const updated = upsertConnectionBlock(raw, fields.name, block);
        fs.writeFileSync(tomlPath, updated, 'utf8');
        vscode.window.showInformationMessage(`KafkaSQL: connection "${fields.name}" updated`);
        await explorer.refresh();
    });
    // ── Remove Connection ─────────────────────────────────────────────────────
    reg('kafkasql.removeConnection', async (node) => {
        if (!node)
            return;
        const confirmed = await vscode.window.showWarningMessage(`Remove connection "${node.label}" from connections.toml?`, { modal: true }, 'Remove');
        if (confirmed !== 'Remove')
            return;
        const tomlPath = findConnectionsToml(path.dirname(node.projectFile));
        if (!tomlPath) {
            vscode.window.showErrorMessage('KafkaSQL: connections.toml not found');
            return;
        }
        const raw = fs.readFileSync(tomlPath, 'utf8');
        const updated = removeConnectionBlock(raw, node.label);
        fs.writeFileSync(tomlPath, updated, 'utf8');
        vscode.window.showInformationMessage(`KafkaSQL: connection "${node.label}" removed`);
        await explorer.refresh();
    });
    // ── Cancel Execution ───────────────────────────────────────────────────────
    reg('kafkasql.cancelExecution', async () => {
        if (!client)
            return;
        await sendLspCommand('kafkasql.cancelExecution', []);
    });
    // ── Execute Statement ─────────────────────────────────────────────────────
    // Runs the selected text (or entire document) against a chosen connection.
    reg('kafkasql.executeStatement', async (uri) => {
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
        const isUntitledFile = activeUri.scheme === 'untitled';
        // Pinned connections are set when a file is opened via "New Query" from the sidebar.
        const pinned = pinnedConnections.get(activeUri.toString());
        const defaultActiveDir = isUntitledFile
            ? (vscode.workspace.workspaceFolders?.[0]?.uri.fsPath ?? path.dirname(activeUri.fsPath))
            : path.dirname(activeUri.fsPath);
        const activeDir = pinned ? path.dirname(pinned.projectFile) : defaultActiveDir;
        const uriKey = activeUri.toString();
        const mode = isUntitledFile
            ? (fileModeOverrides.get(uriKey) ?? 'interactive')
            : getFileMode(activeUri.fsPath, activeDir, uriKey);
        // ── Resolve connection ────────────────────────────────────────────────────
        let pickedName;
        let pickedDescription;
        let lspFilePath;
        const tomlPath = findConnectionsToml(activeDir);
        if (!tomlPath) {
            vscode.window.showErrorMessage('KafkaSQL: no connections.toml found — add one alongside your .proj.toml');
            return;
        }
        const connections = parseConnectionsToml(tomlPath);
        if (connections.length === 0) {
            vscode.window.showErrorMessage('KafkaSQL: connections.toml has no valid connections');
            return;
        }
        const items = connections.map(c => ({
            label: c.name,
            description: c.bootstrapServers,
            detail: `topic: ${c.topic}`,
            name: c.name,
            bootstrap: c.bootstrapServers,
            topic: c.topic,
        }));
        // For pinned queries, show the currently pinned connection first.
        let orderedItems = items;
        if (mode === 'interactive' && pinned) {
            orderedItems = [
                ...items.filter(i => i.name === pinned.connectionName),
                ...items.filter(i => i.name !== pinned.connectionName),
            ].map(i => ({
                ...i,
                detail: i.name === pinned.connectionName ? `${i.detail} • current` : i.detail,
            }));
        }
        // Interactive documents can switch connection from the status bar.
        // During execution, only prompt when no connection is pinned yet.
        const shouldPromptForConnection = !pinned;
        const picked = shouldPromptForConnection
            ? await vscode.window.showQuickPick(orderedItems, {
                placeHolder: mode === 'interactive'
                    ? 'Interactive mode: run against which cluster?'
                    : 'Execute against which cluster?',
                matchOnDescription: true,
            })
            : items.find(i => i.name === pinned.connectionName);
        if (!picked)
            return;
        pickedName = picked.name;
        pickedDescription = picked.description ?? '';
        const fallbackFilePath = isUntitledFile
            ? (vscode.workspace.workspaceFolders?.[0]?.uri.fsPath ?? '')
            : activeUri.fsPath;
        const projectFileForConnection = pinned?.projectFile
            ?? findProjToml(path.dirname(tomlPath))
            ?? fallbackFilePath;
        lspFilePath = mode === 'interactive'
            ? projectFileForConnection
            : (pinned ? pinned.projectFile : fallbackFilePath);
        if (mode === 'interactive') {
            pinnedConnections.set(uriKey, {
                connectionName: picked.name,
                projectFile: projectFileForConnection,
                bootstrapServers: picked.bootstrap,
            });
            await persistEditorRuntimeState();
        }
        resultsChannel.show(true);
        const modeTag = mode === 'interactive' ? ' [interactive]' : (mode === 'project' ? ' [project]' : ' [file]');
        resultsChannel.appendLine(`\n── Execute on "${pickedName}" (${pickedDescription})${modeTag} ──`);
        resultsChannel.appendLine(new Date().toISOString());
        let result;
        executionStatusBar.show();
        try {
            result = await vscode.window.withProgress({ location: vscode.ProgressLocation.Notification, title: `KafkaSQL: reading from "${pickedName}"…`, cancellable: true }, async (_progress, token) => {
                token.onCancellationRequested(() => sendLspCommand('kafkasql.cancelExecution', []).catch(() => { }));
                return sendLspCommand('kafkasql.executeStatement', [lspFilePath, pickedName, text, mode]);
            });
        }
        catch (err) {
            resultsChannel.appendLine(`ERROR: ${err}`);
            vscode.window.showErrorMessage(`KafkaSQL execute failed: ${err}`);
            return;
        }
        finally {
            executionStatusBar.hide();
        }
        if (result && typeof result === 'object' && 'error' in result) {
            const msg = result.error;
            resultsChannel.appendLine(`ERROR: ${msg}`);
            vscode.window.showErrorMessage(`KafkaSQL execute: ${msg}`);
            return;
        }
        if (result && typeof result === 'object' && 'executed' in result) {
            const r = result;
            for (const op of r.operations) {
                resultsChannel.appendLine(`  ${op}`);
            }
            if ('records' in r) {
                // Records were already streamed to the channel via kafkasql/record notifications.
                // Just show the summary line.
                const count = (r.records ?? []).length;
                resultsChannel.appendLine(`\n${count} record(s) returned.`);
                if (count > 0) {
                    vscode.window.showInformationMessage(`KafkaSQL: ${count} record(s) returned from "${pickedName}"`);
                }
                else {
                    vscode.window.showInformationMessage(`KafkaSQL: no matching records on "${pickedName}"`);
                }
            }
            else if (r.executed > 0) {
                resultsChannel.appendLine(`\n${r.executed} event(s) written.`);
                vscode.window.showInformationMessage(`KafkaSQL: ${r.executed} event(s) written to "${pickedName}"`);
            }
            else {
                resultsChannel.appendLine(`\nnothing to write — schema already up to date.`);
                vscode.window.showInformationMessage(`KafkaSQL: nothing to write — schema already up to date on "${pickedName}"`);
            }
        }
    });
    startServer(context, () => explorer.refresh()).catch(err => {
        console.error('Failed to start KafkaSQL LSP:', err);
    });
}
function deactivate() {
    if (client) {
        const stopPromise = client.stop();
        client = null;
        return stopPromise;
    }
    return undefined;
}
//# sourceMappingURL=extension.js.map