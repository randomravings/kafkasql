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
const os = require("os");
let client = null;
let serverProc = null;
function findProjectRoot(startPath) {
    let cur = startPath;
    while (true) {
        if (fs.existsSync(path.join(cur, 'settings.gradle')) || fs.existsSync(path.join(cur, 'settings.gradle.kts'))) {
            return cur;
        }
        const parent = path.dirname(cur);
        if (parent === cur || parent === '' || parent === os.homedir())
            break;
        cur = parent;
    }
    return startPath;
}
function findGradleBinary(workspaceRoot) {
    if (!workspaceRoot)
        return 'gradle';
    const wrapper = path.join(workspaceRoot, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
    try {
        if (fs.existsSync(wrapper))
            return wrapper;
    }
    catch { }
    return 'gradle';
}
function findBuiltServerJar(workspaceRoot) {
    try {
        const libsDir = path.join(workspaceRoot, 'lsp', 'build', 'libs');
        if (!fs.existsSync(libsDir))
            return null;
        const files = fs.readdirSync(libsDir).filter(f => f.endsWith('.jar'));
        if (files.length === 0)
            return null;
        // prefer shadow/all jar if present
        const pref = files.find(f => /shadow|all|fat/i.test(f));
        const chosen = pref || files[0];
        return path.join(libsDir, chosen);
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
            try {
                fs.chmodSync(gradle, 0o755);
            }
            catch { /* ignore permission change errors */ }
        }
    }
    catch { /* ignore */ }
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
        serverProc = (0, child_process_1.spawn)(javaExe, ['-jar', serverJar], { cwd: workspaceRoot, shell: false, env });
    }
    else {
        output.appendLine(`Gradle args: ${gradleArgs.join(' ')}`);
        // fallback to Gradle (best-effort quiet console). still risky, but used only when jar missing.
        serverProc = (0, child_process_1.spawn)(gradle, ['--quiet', '--console=plain', ...gradleArgs], {
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
    const serverOptions = () => Promise.resolve({ reader: serverProc.stdout, writer: serverProc.stdin });
    const clientOptions = {
        documentSelector: [{ scheme: 'file', language: 'kafkasql' }],
        outputChannelName: 'KafkaSQL LSP'
    };
    client = new node_1.LanguageClient('kafkasql', 'KafkaSQL Language Server', serverOptions, clientOptions);
    context.subscriptions.push(client);
    client.start();
    vscode.window.showInformationMessage('KafkaSQL language server started');
}
function activate(context) {
    context.subscriptions.push(vscode.commands.registerCommand('kafkasql.startServer', () => startServer(context)));
    // attempt auto-start if a workspace is open
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