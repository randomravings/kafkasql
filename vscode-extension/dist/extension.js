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
const child_process_1 = require("child_process");
const path = __importStar(require("path"));
const node_1 = require("vscode-languageclient/node");
const fs = __importStar(require("fs"));
const kafkaSqlProjectExplorer_1 = require("./kafkaSqlProjectExplorer");
let client = null;
let serverProc = null;
let includeDiagnostics;
let diffDiagnostics;
// URI stored by "Select for Compare"
let compareBaseUri;
let currentMode = 'git';
let modeStatusBar;
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
async function startServer(context) {
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
    serverProc = (0, child_process_1.spawn)(javaExe, ['-jar', serverJar], { cwd: workspaceRoot, shell: false, env });
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
    const serverOptions = () => Promise.resolve({ reader: serverProc.stdout, writer: serverProc.stdin });
    const clientOptions = {
        documentSelector: [{ scheme: 'file', language: 'kafkasql' }],
        outputChannelName: 'KafkaSQL LSP'
    };
    client = new node_1.LanguageClient('kafkasql', 'KafkaSQL Language Server', serverOptions, clientOptions);
    context.subscriptions.push(client);
    await client.start();
    // Sync current comparison mode to newly started LSP server
    sendModeToLsp(currentMode);
    vscode.window.showInformationMessage('KafkaSQL language server started');
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
        return;
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
            return;
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
    }
    catch (err) {
        console.error('KafkaSQL semanticDiff failed:', err);
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
        console.error('kafkasql.setComparisonMode failed:', err);
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
function activate(context) {
    includeDiagnostics = vscode.languages.createDiagnosticCollection('kafkasql-includes');
    context.subscriptions.push(includeDiagnostics);
    diffDiagnostics = vscode.languages.createDiagnosticCollection('kafkasql-diff');
    context.subscriptions.push(diffDiagnostics);
    context.subscriptions.push(vscode.commands.registerCommand('kafkasql.startServer', () => startServer(context)));
    // ── Compare commands ──────────────────────────────────────────────────────
    // Step 1: right-click a .kafka file → remember it as the left side
    context.subscriptions.push(vscode.commands.registerCommand('kafkasql.selectForCompare', (uri) => {
        compareBaseUri = uri;
        // Expose the context key so the "Compare with Selected" menu item becomes visible
        vscode.commands.executeCommand('setContext', 'kafkasql.compareBase', true);
        vscode.window.showInformationMessage(`KafkaSQL: selected ${path.basename(uri.fsPath)} as compare base`);
    }));
    // Step 2: right-click another .kafka file → open native diff editor
    context.subscriptions.push(vscode.commands.registerCommand('kafkasql.compareWithSelected', (uri) => {
        if (!compareBaseUri) {
            vscode.window.showErrorMessage('KafkaSQL: no base file selected — use "Select for Compare" first');
            return;
        }
        const left = compareBaseUri;
        const right = uri;
        const title = `${path.basename(left.fsPath)} ↔ ${path.basename(right.fsPath)}`;
        vscode.commands.executeCommand('vscode.diff', left, right, title);
        runSemanticDiff(left, right);
    }));
    // Bonus: from the active editor, open a file picker to choose the right side
    context.subscriptions.push(vscode.commands.registerCommand('kafkasql.compareWithActive', async (uri) => {
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
    }));
    // ── Comparison mode status bar ─────────────────────────────────────────────
    modeStatusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
    modeStatusBar.command = 'kafkasql.changeComparisonMode';
    modeStatusBar.tooltip = 'KafkaSQL: click to change compatibility check mode';
    modeStatusBar.text = modeLabel(currentMode);
    modeStatusBar.show();
    context.subscriptions.push(modeStatusBar);
    context.subscriptions.push(vscode.commands.registerCommand('kafkasql.changeComparisonMode', () => pickComparisonMode()));
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
    const sendLspCommand = (cmd, args) => {
        if (!client)
            return Promise.reject(new Error('LSP not ready'));
        return client.sendRequest('workspace/executeCommand', { command: cmd, arguments: args });
    };
    const explorer = new kafkaSqlProjectExplorer_1.KafkaSqlProjectExplorer(context, sendLspCommand);
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
    context.subscriptions.push(vscode.commands.registerCommand('kafkasql.explorer.refresh', () => explorer.refresh()), vscode.commands.registerCommand('kafkasql.explorer.toggleFlatContexts', () => explorer.toggleFlatContexts()), vscode.commands.registerCommand('kafkasql.explorer.expandAll', async () => {
        for (const node of explorer.getProjectNodes()) {
            await treeView.reveal(node, { expand: 5 });
        }
    }), vscode.commands.registerCommand('kafkasql.explorer.collapseAll', () => {
        vscode.commands.executeCommand('workbench.actions.treeView.kafkasqlProjectExplorer.collapseAll');
    }), vscode.commands.registerCommand('kafkasql.explorer.openFile', (filePath, line) => {
        vscode.window.showTextDocument(vscode.Uri.file(filePath), {
            selection: new vscode.Range(line, 0, line, 0),
            preview: false,
        });
    }));
    startServer(context).catch(err => {
        console.error('Failed to start KafkaSQL LSP:', err);
    });
}
function deactivate() {
    if (client) {
        const stopPromise = client.stop();
        client = null;
        if (serverProc) {
            try {
                serverProc.kill();
            }
            catch { /* ignore */ }
            serverProc = null;
        }
        return stopPromise;
    }
    return undefined;
}
//# sourceMappingURL=extension.js.map