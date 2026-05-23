import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';

// ── Node types ────────────────────────────────────────────────────────────────

type NodeKind = 'projectsRoot' | 'clustersRoot' | 'project' | 'noProject' | 'empty' | 'context' | 'category' | 'object'
  | 'connection' | 'liveContext' | 'liveCategory' | 'liveObject' | 'loading' | 'liveError';

type ObjectKind = 'TYPE_ENUM' | 'TYPE_STRUCT' | 'TYPE_SCALAR' | 'TYPE_DERIVED' | 'TYPE_UNION' | 'STREAM';

/** Category names that appear as SQL-Server-style folder nodes under a context. */
type CategoryKind = 'Contexts' | 'Types' | 'Streams';

export class ProjectNode {
  readonly kind = 'project' as const;
  constructor(
    public readonly label: string,
    public readonly kafkaRoot: string,
    public readonly projectFile: string,
  ) {}
}

export class NoProjectNode {
  readonly kind = 'noProject' as const;
  readonly label = 'No .proj.toml found in workspace';
}

export class ProjectsRootNode {
  readonly kind = 'projectsRoot' as const;
  readonly label = 'Projects';
}

export class ClustersRootNode {
  readonly kind = 'clustersRoot' as const;
  readonly label = 'Clusters';
}

export class ContextNode {
  readonly kind = 'context' as const;
  constructor(
    public readonly label: string,         // e.g. "com.example"
    public readonly declaredInFile: string,
    public readonly declaredAtLine: number,
  ) {}
}

/** Grouping folder node — like "Tables", "Views" in SQL Server Object Explorer. */
export class CategoryNode {
  readonly kind = 'category' as const;
  constructor(
    public readonly category: CategoryKind,
    public readonly contextName: string,   // owning context for child lookup
    public readonly count: number,
  ) {}
  get label() { return this.category; }
}

export class ObjectNode {
  readonly kind = 'object' as const;
  constructor(
    public readonly label: string,
    public readonly objectKind: ObjectKind,
    public readonly contextName: string,
    public readonly filePath: string,
    public readonly line: number,
  ) {}
}

export class EmptyNode {
  readonly kind = 'empty' as const;
  readonly label = '<empty>';
}

export class ConnectionNode {
  readonly kind = 'connection' as const;
  constructor(
    public readonly label: string,         // connection name
    public readonly projectName: string,
    public readonly projectFile: string,   // path to .proj.toml for LSP request
    public readonly bootstrapServers: string,
    public readonly topic: string,
    public readonly username?: string,
    public readonly password?: string,
  ) {}
  get connectionKey() { return `${this.projectName}/${this.label}`; }
}

export class LiveContextNode {
  readonly kind = 'liveContext' as const;
  constructor(
    public readonly label: string,         // FQN
    public readonly connectionKey: string,
  ) {}
}

export class LiveCategoryNode {
  readonly kind = 'liveCategory' as const;
  constructor(
    public readonly category: CategoryKind,
    public readonly contextName: string,
    public readonly connectionKey: string,
    public readonly count: number,
  ) {}
}

export class LiveObjectNode {
  readonly kind = 'liveObject' as const;
  constructor(
    public readonly label: string,
    public readonly objectKind: ObjectKind,
    public readonly contextName: string,
    public readonly connectionKey: string,
  ) {}
}

export class LoadingNode {
  readonly kind = 'loading' as const;
  readonly label = 'Loading...';
}

export class LiveErrorNode {
  readonly kind = 'liveError' as const;
  constructor(public readonly message: string) {}
  get label() { return '\u26a0 ' + this.message; }
}

export type KafkaSqlTreeNode =
  | ProjectsRootNode
  | ClustersRootNode
  | ProjectNode
  | NoProjectNode
  | EmptyNode
  | ContextNode
  | CategoryNode
  | ObjectNode
  | ConnectionNode
  | LiveContextNode
  | LiveCategoryNode
  | LiveObjectNode
  | LoadingNode
  | LiveErrorNode;

// ── Parsed project data ───────────────────────────────────────────────────────

export interface ParsedConnection {
  name: string;
  bootstrapServers: string;
  topic: string;
  username?: string;
  password?: string;
}

interface KafkaSqlObject {
  name: string;
  kind: ObjectKind;
  filePath: string;
  line: number;           // 0-based
}

interface ParsedContext {
  name: string;           // fully-qualified, e.g. "com.example"
  declaredInFile: string;
  declaredAtLine: number; // 0-based
  objects: KafkaSqlObject[];
}

interface ParsedProject {
  name: string;
  kafkaRoot: string;
  projectFile: string;
  contexts: Map<string, ParsedContext>;
  rootObjects: KafkaSqlObject[]; // objects in files without USE CONTEXT
  connections: ParsedConnection[];
}

// ── Regexes (applied per-line, so no multiline flag needed) ──────────────────

const RE_USE_CONTEXT     = /\bUSE\s+CONTEXT\s+([\w.]+)\s*;/i;
const RE_CREATE_CONTEXT  = /^\s*CREATE\s+CONTEXT\s+([\w.]+)/i;
const RE_CREATE_STREAM   = /^\s*CREATE\s+STREAM\s+(\w+)/i;
const RE_CREATE_TYPE_AS  = /^\s*CREATE\s+TYPE\s+(\w+)\s+AS\s+(ENUM|STRUCT|SCALAR|UNION)\b/i;
const RE_CREATE_TYPE_DER = /^\s*CREATE\s+TYPE\s+(\w+)\s+AS\s+\w/i; // derived / aliased

// ── Project file parsing ──────────────────────────────────────────────────────

interface ProjFile {
  name: string;
  kafkaDir: string;
}

function parseProjectFile(filePath: string): ProjFile | null {
  const stem = path.basename(filePath, '.proj.toml');
  let name: string | null = null;
  let kafkaDir = 'model';
  let inProjectSection = false;
  let hasProjectSection = false;
  for (const raw of fs.readFileSync(filePath, 'utf8').split('\n')) {
    // Strip inline comments respecting quotes
    let t = raw.trim();
    if (!t || t.startsWith('#')) continue;
    // Section header
    if (t.startsWith('[')) {
      const section = t.replace(/^\[/, '').replace(/\].*$/, '').trim();
      inProjectSection = section === 'project';
      if (inProjectSection) hasProjectSection = true;
      continue;
    }
    if (!inProjectSection) continue;
    const eq = t.indexOf('=');
    if (eq < 0) continue;
    const key = t.slice(0, eq).trim().toLowerCase();
    let val = t.slice(eq + 1).trim();
    // Strip quotes and inline comments
    if (val.startsWith('"') && val.includes('"', 1)) val = val.slice(1, val.indexOf('"', 1));
    else { const ci = val.indexOf('#'); if (ci >= 0) val = val.slice(0, ci).trim(); }
      if (key === 'name')  name = val || null;
    if (key === 'kafka') kafkaDir = val;
  }
  if (!hasProjectSection) {
    log('skipping ' + filePath + ': no [project] section');
    return null;
  }
  if (!name) {
    log('skipping ' + filePath + ': [project] has no name key');
    return null;
  }
  return { name, kafkaDir };
}

// ── connections.toml parsing ──────────────────────────────────────────────────

function parseConnectionsFile(dir: string): ParsedConnection[] {
  const filePath = path.join(dir, 'connections.toml');
  if (!fs.existsSync(filePath)) return [];
  const connections: ParsedConnection[] = [];
  let current: { name: string; bootstrap?: string; topic?: string; username?: string; password?: string } | null = null;
  for (const raw of fs.readFileSync(filePath, 'utf8').split('\n')) {
    const t = raw.trim();
    if (!t || t.startsWith('#')) continue;
    if (t.startsWith('[')) {
      if (current?.bootstrap && current.topic) {
        connections.push({ name: current.name, bootstrapServers: current.bootstrap, topic: current.topic, username: current.username, password: current.password });
      }
      const section = t.replace(/^\[/, '').replace(/\].*$/, '').trim();
      if (section.startsWith('connection.')) {
        current = { name: section.slice('connection.'.length) };
      } else {
        current = null;
      }
      continue;
    }
    if (!current) continue;
    const eq = t.indexOf('=');
    if (eq < 0) continue;
    const key = t.slice(0, eq).trim().toLowerCase();
    let val = t.slice(eq + 1).trim();
    if (val.startsWith('"') && val.includes('"', 1)) val = val.slice(1, val.indexOf('"', 1));
    else { const ci = val.indexOf('#'); if (ci >= 0) val = val.slice(0, ci).trim(); }
    if (key === 'bootstrap') current.bootstrap = val;
    if (key === 'topic')     current.topic = val;
    if (key === 'username')  current.username = val;
    if (key === 'password')  current.password = val;
  }
  if (current?.bootstrap && current.topic) {
    connections.push({ name: current.name, bootstrapServers: current.bootstrap, topic: current.topic, username: current.username, password: current.password });
  }
  return connections;
}

// ── Per-file .kafka parsing ───────────────────────────────────────────────────

interface FileParse {
  /** Last USE CONTEXT active at end of file (authoritative context for objects). */
  activeContext: string | null;
  objects: KafkaSqlObject[];
  /** All contexts declared via CREATE CONTEXT in this file. */
  createdContexts: Array<{ name: string; line: number }>;
}

function parseKafkaFile(filePath: string): FileParse {
  const lines = fs.readFileSync(filePath, 'utf8').split('\n');
  const out: FileParse = { activeContext: null, objects: [], createdContexts: [] };

  // Strip single-line comments before matching, keep line numbers intact
  const stripped = lines.map(l => l.replace(/--.*$/, '').trim());

  for (let i = 0; i < stripped.length; i++) {
    const line = stripped[i];
    if (!line) continue;

    // USE CONTEXT — update active context
    const useM = RE_USE_CONTEXT.exec(line);
    if (useM) { out.activeContext = useM[1]; continue; }

    // CREATE CONTEXT — register it; qualify with the active context if it's a short name
    // e.g. CREATE CONTEXT example inside USE CONTEXT com → stored as com.example
    const ctxM = RE_CREATE_CONTEXT.exec(line);
    if (ctxM) {
      const shortName = ctxM[1];
      const fqn = out.activeContext && !shortName.startsWith(out.activeContext + '.')
        ? out.activeContext + '.' + shortName
        : shortName;
      out.createdContexts.push({ name: fqn, line: i });
      continue;
    }

    // CREATE STREAM
    const streamM = RE_CREATE_STREAM.exec(line);
    if (streamM) {
      out.objects.push({ name: streamM[1], kind: 'STREAM', filePath, line: i });
      continue;
    }

    // CREATE TYPE … AS ENUM|STRUCT|SCALAR
    const typeAsM = RE_CREATE_TYPE_AS.exec(line);
    if (typeAsM) {
      const qual = typeAsM[2].toUpperCase();
      const kind: ObjectKind =
        qual === 'ENUM'   ? 'TYPE_ENUM'   :
        qual === 'STRUCT' ? 'TYPE_STRUCT' :
        qual === 'UNION'  ? 'TYPE_UNION'  : 'TYPE_SCALAR';
      out.objects.push({ name: typeAsM[1], kind, filePath, line: i });
      continue;
    }

    // CREATE TYPE … AS <other>  (derived / aliased)
    const typeDerM = RE_CREATE_TYPE_DER.exec(line);
    if (typeDerM) {
      out.objects.push({ name: typeDerM[1], kind: 'TYPE_DERIVED', filePath, line: i });
    }
  }

  return out;
}

// ── Full project scan ─────────────────────────────────────────────────────────

let out: vscode.OutputChannel | undefined;

function log(msg: string) {
  out?.appendLine('[kafkasql-explorer] ' + msg);
}

function scanKafkaDir(dir: string, projectName: string, projectFile: string, connections: ParsedConnection[]): ParsedProject {
  const contexts = new Map<string, ParsedContext>();
  const rootObjects: KafkaSqlObject[] = [];

  function walk(currentDir: string) {
    if (!fs.existsSync(currentDir)) { log('dir not found: ' + currentDir); return; }
    for (const entry of fs.readdirSync(currentDir, { withFileTypes: true })) {
      if (entry.isDirectory()) {
        walk(path.join(currentDir, entry.name));
      } else if (entry.name.endsWith('.kafka') || entry.name.endsWith('.kafkasql')) {
        const filePath = path.join(currentDir, entry.name);
        let parsed: FileParse;
        try {
          parsed = parseKafkaFile(filePath);
        } catch (e: any) {
          log('parse error in ' + filePath + ': ' + e.message);
          continue;
        }
        log(`file ${filePath}: activeCtx=${parsed.activeContext ?? 'none'}, `
          + `creates=[${parsed.createdContexts.map(c => c.name).join(', ')}], `
          + `objects=${parsed.objects.length}`);

        // Register every declared context in this file.
        // Always update declaredInFile/Line — even if a placeholder exists from a
        // deeper file that was scanned first (depth-first walk order).
        for (const { name: ctxName, line: ctxLine } of parsed.createdContexts) {
          if (!contexts.has(ctxName)) {
            contexts.set(ctxName, {
              name: ctxName,
              declaredInFile: filePath,
              declaredAtLine: ctxLine,
              objects: [],
            });
          } else {
            const existing = contexts.get(ctxName)!;
            existing.declaredInFile = filePath;
            existing.declaredAtLine = ctxLine;
          }
        }

        // Attach objects to their context bucket
        if (parsed.activeContext) {
          if (parsed.objects.length > 0) {
            if (!contexts.has(parsed.activeContext)) {
              // Context used but not yet declared — placeholder
              contexts.set(parsed.activeContext, {
                name: parsed.activeContext,
                declaredInFile: filePath,
                declaredAtLine: 0,
                objects: [],
              });
            }
            contexts.get(parsed.activeContext)!.objects.push(...parsed.objects);
          }
        } else {
          rootObjects.push(...parsed.objects);
        }
      }
    }
  }

  log('scanning kafka root: ' + dir);
  walk(dir);
  log('found contexts: ' + [...contexts.keys()].join(', '));
  return { name: projectName, kafkaRoot: dir, projectFile, contexts, rootObjects, connections };
}

// ── TreeDataProvider ──────────────────────────────────────────────────────────

export class KafkaSqlProjectExplorer
    implements vscode.TreeDataProvider<KafkaSqlTreeNode> {

  private _onDidChangeTreeData =
      new vscode.EventEmitter<KafkaSqlTreeNode | null | undefined>();
  readonly onDidChangeTreeData: vscode.Event<KafkaSqlTreeNode | null | undefined> =
      this._onDidChangeTreeData.event;

  private projects: ParsedProject[] = [];
  private watcher: vscode.FileSystemWatcher | null = null;
  private flatContexts = false;
  private liveCache = new Map<string, 'loading' | { error: string } | Map<string, ParsedContext>>();

  toggleFlatContexts(): void {
    this.flatContexts = !this.flatContexts;
    this._onDidChangeTreeData.fire(undefined);
  }

  getProjectNodes(): ProjectNode[] {
    return this.projects.map(p => new ProjectNode(p.name, p.kafkaRoot, p.projectFile));
  }

  getConnectionsForProject(projectName: string): ParsedConnection[] {
    return this.projects.find(p => p.name === projectName)?.connections ?? [];
  }

  constructor(
    private readonly context: vscode.ExtensionContext,
    private readonly sendLspCommand: (cmd: string, args: any[]) => Promise<any>,
  ) {
    out = vscode.window.createOutputChannel('KafkaSQL Explorer');
    context.subscriptions.push(out);

    // Watch .kafka/.proj.toml/.rules.toml files and refresh on any change
    this.watcher = vscode.workspace.createFileSystemWatcher(
      '**/*.{kafka,kafkasql,proj.toml,rules.toml}'
    );
    const doRefresh = () => this.refresh().catch(e => log('watcher refresh error: ' + e));
    this.watcher.onDidChange(doRefresh);
    this.watcher.onDidCreate(doRefresh);
    this.watcher.onDidDelete(doRefresh);
    context.subscriptions.push(this.watcher);

    // Initial load — async, fires onDidChangeTreeData when done
    this.refresh().catch(e => log('initial refresh error: ' + e));
  }

  async refresh(): Promise<void> {
    this.liveCache.clear();
    try {
      this.projects = await this.loadProjects();
      log('refresh done, projects: ' + this.projects.map(p => p.name).join(', '));
    } catch (e: any) {
      log('refresh error: ' + e?.message + '\n' + e?.stack);
      this.projects = [];
    }
    this._onDidChangeTreeData.fire(undefined);
  }

  private async loadProjects(): Promise<ParsedProject[]> {
    // Use VS Code's own file finder — respects workspace exclusions, no manual walking needed
    const uris = await vscode.workspace.findFiles(
      '**/*.proj.toml',
      '{.git,node_modules,build,.gradle,dist}/**'
    );
    log('findFiles found: ' + uris.map(u => u.fsPath).join(', '));

    const projects: ParsedProject[] = [];
    for (const uri of uris) {
      try {
        const projFile = uri.fsPath;
        const projDir  = path.dirname(projFile);
        const parsed = parseProjectFile(projFile);
        if (!parsed) continue;  // missing [project] or name — skip silently
        const { name, kafkaDir } = parsed;
        const kafkaRoot = path.join(projDir, kafkaDir);
        const connections = parseConnectionsFile(projDir);
        log('loading project "' + name + '" kafka root: ' + kafkaRoot + ', connections: ' + connections.map(c => c.name).join(', '));
        projects.push(scanKafkaDir(kafkaRoot, name, projFile, connections));
      } catch (e: any) {
        log('failed to load project ' + uri.fsPath + ': ' + e?.message);
      }
    }
    return projects;
  }

  // ── TreeDataProvider interface ──────────────────────────────────────────────

  getTreeItem(node: KafkaSqlTreeNode): vscode.TreeItem {
    try {
      switch (node.kind) {
        case 'projectsRoot': {
          const item = new vscode.TreeItem(
            'Projects',
            vscode.TreeItemCollapsibleState.Expanded
          );
          item.iconPath     = icon('folder-library', 'terminal.ansiBrightYellow');
          item.contextValue = 'kafkasqlProjectsRoot';
          return item;
        }
        case 'clustersRoot': {
          const item = new vscode.TreeItem(
            'Clusters',
            vscode.TreeItemCollapsibleState.Expanded
          );
          item.iconPath     = icon('server', 'terminal.ansiBrightGreen');
          item.contextValue = 'kafkasqlClustersRoot';
          return item;
        }
        case 'project': {
          const item = new vscode.TreeItem(
            node.label,
            vscode.TreeItemCollapsibleState.Expanded
          );
          item.iconPath     = icon('database', 'charts.yellow');
          item.contextValue = 'kafkasqlProject';
          return item;
        }
        case 'noProject': {
          const item = new vscode.TreeItem(node.label);
          item.iconPath = icon('info', 'disabledForeground');
          return item;
        }
        case 'empty': {
          const item = new vscode.TreeItem(node.label);
          item.iconPath = icon('circle-slash', 'disabledForeground');
          return item;
        }
        case 'context': {
          // In flat mode show the full FQN; in nested mode show only the last segment
          const label = this.flatContexts
            ? node.label
            : node.label.slice(node.label.lastIndexOf('.') + 1);
          const item = new vscode.TreeItem(
            label,
            vscode.TreeItemCollapsibleState.Expanded
          );
          item.tooltip      = node.label;  // full FQN on hover
          item.description  = 'context';
          item.iconPath     = icon('symbol-namespace', 'terminal.ansiBrightBlue');
          item.contextValue = 'kafkasqlContext';
          item.command = {
            command: 'kafkasql.explorer.openFile',
            title: 'Open',
            arguments: [node.declaredInFile, node.declaredAtLine],
          };
          return item;
        }
        case 'category': {
          const item = new vscode.TreeItem(
            node.category,
            vscode.TreeItemCollapsibleState.Collapsed
          );
          item.iconPath    = categoryIcon(node.category);
          item.description = `(${node.count})`;
          item.contextValue = 'kafkasqlCategory';
          return item;
        }
        case 'object': {
          const item = new vscode.TreeItem(
            node.label,
            vscode.TreeItemCollapsibleState.None
          );
          item.iconPath     = objectIcon(node.objectKind);
          item.description  = objectKindLabel(node.objectKind);
          item.contextValue = 'kafkasqlObject';
          item.command = {
            command: 'kafkasql.explorer.openFile',
            title: 'Open',
            arguments: [node.filePath, node.line],
          };
          return item;
        }
        case 'connection': {
          const item = new vscode.TreeItem(
            node.label,
            vscode.TreeItemCollapsibleState.Collapsed
          );
          item.iconPath     = icon('plug', 'terminal.ansiBrightGreen');
          const maxLen = 40;
          item.description  = node.bootstrapServers.length > maxLen
            ? node.bootstrapServers.slice(0, maxLen) + '…'
            : node.bootstrapServers;
          item.tooltip      = `${node.projectName} — ${node.bootstrapServers} / topic: ${node.topic}${node.username ? ' 🔐' : ''}`;
          item.contextValue = 'kafkasqlConnection';
          return item;
        }
        case 'liveContext': {
          const label = this.flatContexts
            ? node.label
            : node.label.slice(node.label.lastIndexOf('.') + 1);
          const item = new vscode.TreeItem(
            label,
            vscode.TreeItemCollapsibleState.Expanded
          );
          item.tooltip      = node.label;
          item.description  = 'live';
          item.iconPath     = icon('symbol-namespace', 'terminal.ansiBrightBlue');
          item.contextValue = 'kafkasqlLiveContext';
          return item;
        }
        case 'liveCategory': {
          const item = new vscode.TreeItem(
            node.category,
            vscode.TreeItemCollapsibleState.Collapsed
          );
          item.iconPath    = categoryIcon(node.category);
          item.description = `(${node.count})`;
          item.contextValue = 'kafkasqlLiveCategory';
          return item;
        }
        case 'liveObject': {
          const item = new vscode.TreeItem(
            node.label,
            vscode.TreeItemCollapsibleState.None
          );
          item.iconPath    = objectIcon(node.objectKind);
          item.description = objectKindLabel(node.objectKind);
          item.contextValue = 'kafkasqlLiveObject';
          // No command — no local file to open
          return item;
        }
        case 'loading': {
          const item = new vscode.TreeItem(
            node.label,
            vscode.TreeItemCollapsibleState.None
          );
          item.iconPath = new vscode.ThemeIcon('loading~spin');
          return item;
        }
        case 'liveError': {
          const item = new vscode.TreeItem(
            node.label,
            vscode.TreeItemCollapsibleState.None
          );
          item.iconPath = icon('warning', 'disabledForeground');
          return item;
        }
      }
    } catch (e: any) {
      log('getTreeItem error: ' + e?.message);
      return new vscode.TreeItem('(error)');
    }
  }

  getChildren(node?: KafkaSqlTreeNode): vscode.ProviderResult<KafkaSqlTreeNode[]> {
    try {
      // ── Root: always two master nodes ─────────────────────────────────────
      if (!node) {
        return [new ProjectsRootNode(), new ClustersRootNode()];
      }

      // ── ProjectsRoot → one ProjectNode per .proj.toml ────────────────────
      if (node.kind === 'projectsRoot') {
        if (this.projects.length === 0) return [new NoProjectNode()];
        return this.projects.map(p => new ProjectNode(p.name, p.kafkaRoot, p.projectFile));
      }

      // ── ClustersRoot → all connections from all projects ─────────────────
      if (node.kind === 'clustersRoot') {
        const all: ConnectionNode[] = [];
        for (const proj of this.projects) {
          for (const c of proj.connections) {
            all.push(new ConnectionNode(c.name, proj.name, proj.projectFile, c.bootstrapServers, c.topic, c.username, c.password));
          }
        }
        if (all.length === 0) return [new EmptyNode()];
        return all;
      }

      // ── Project → contexts (no connections here any more) ────────────────
      if (node.kind === 'project') {
        const proj = this.projects.find(p => p.name === node.label);
        const hasContent = proj &&
          (proj.contexts.size > 0 || proj.rootObjects.length > 0);
        let localNodes: KafkaSqlTreeNode[];
        if (!hasContent) {
          localNodes = [new EmptyNode()];
        } else if (this.flatContexts) {
          if (!proj) {
            localNodes = [];
          } else {
            localNodes = [...proj.contexts.values()]
              .sort((a, b) => a.name.localeCompare(b.name))
              .map(c => new ContextNode(c.name, c.declaredInFile, c.declaredAtLine));
          }
        } else {
          const rootCount = proj
            ? [...proj.contexts.values()].filter(c => isRootContext(c, proj.contexts)).length
            : 0;
          localNodes = [new CategoryNode('Contexts', node.label, rootCount)];
        }
        return localNodes;
      }

      // ── Connection → live contexts (triggers fetch if not cached) ─────────
      if (node.kind === 'connection') {
        const cached = this.liveCache.get(node.connectionKey);
        if (cached === undefined) {
          this.fetchLiveModel(node);  // async — fires update when done
          return [new LoadingNode()];
        }
        if (cached === 'loading') return [new LoadingNode()];
        if ('error' in cached) return [new LiveErrorNode(cached.error)];
        const contexts = cached as Map<string, ParsedContext>;
        if (contexts.size === 0) return [new EmptyNode()];
        // Flat: all contexts alphabetically. Nested: root contexts only (children nest inside).
        const visible = this.flatContexts
          ? [...contexts.values()]
          : [...contexts.values()].filter(c => isRootContext(c, contexts));
        return visible
          .sort((a, b) => a.name.localeCompare(b.name))
          .map(c => new LiveContextNode(c.name, node.connectionKey));
      }

      // ── LiveContext → objects (flat) or category folders (nested) ─────────
      if (node.kind === 'liveContext') {
        const cached = this.liveCache.get(node.connectionKey);
        if (!cached || cached === 'loading' || 'error' in cached) return [];
        const contexts = cached as Map<string, ParsedContext>;
        const ctx = contexts.get(node.label);
        if (!ctx) return [];
        if (this.flatContexts) {
          return ctx.objects
            .slice()
            .sort((a, b) => a.name.localeCompare(b.name))
            .map(o => new LiveObjectNode(o.name, o.kind, node.label, node.connectionKey));
        }
        const childCount  = getDirectChildContexts(node.label, contexts).length;
        const typeCount   = ctx.objects.filter(o => o.kind !== 'STREAM').length;
        const streamCount = ctx.objects.filter(o => o.kind === 'STREAM').length;
        const folders: LiveCategoryNode[] = [];
        if (childCount > 0)  folders.push(new LiveCategoryNode('Contexts', node.label, node.connectionKey, childCount));
        if (typeCount > 0)   folders.push(new LiveCategoryNode('Types',    node.label, node.connectionKey, typeCount));
        if (streamCount > 0) folders.push(new LiveCategoryNode('Streams',  node.label, node.connectionKey, streamCount));
        return folders;
      }

      // ── LiveCategory → live objects or child live contexts ────────────────
      if (node.kind === 'liveCategory') {
        const cached = this.liveCache.get(node.connectionKey);
        if (!cached || cached === 'loading' || 'error' in cached) return [];
        const contexts = cached as Map<string, ParsedContext>;
        if (node.category === 'Contexts') {
          return getDirectChildContexts(node.contextName, contexts)
            .sort((a, b) => a.name.localeCompare(b.name))
            .map(c => new LiveContextNode(c.name, node.connectionKey));
        }
        const ctx = contexts.get(node.contextName);
        if (!ctx) return [];
        const isStream = node.category === 'Streams';
        return ctx.objects
          .filter(o => isStream ? o.kind === 'STREAM' : o.kind !== 'STREAM')
          .sort((a, b) => a.name.localeCompare(b.name))
          .map(o => new LiveObjectNode(o.name, o.kind, node.contextName, node.connectionKey));
      }

      // ── Context → objects directly (flat) or category folders (nested) ───
      if (node.kind === 'context') {
        const proj = this.projects.find(p => p.contexts.has(node.label));
        const ctx  = this.findContext(node.label);
        if (this.flatContexts) {
          // No folders — all objects sorted by name
          if (!ctx) return [];
          return ctx.objects
            .slice()
            .sort((a, b) => a.name.localeCompare(b.name))
            .map(o => new ObjectNode(o.name, o.kind, node.label, o.filePath, o.line));
        }
        const childCount  = proj ? getDirectChildContexts(node.label, proj.contexts).length : 0;
        const typeCount   = ctx  ? ctx.objects.filter(o => o.kind !== 'STREAM').length : 0;
        const streamCount = ctx  ? ctx.objects.filter(o => o.kind === 'STREAM').length : 0;
        const folders: CategoryNode[] = [];
        if (childCount > 0)  folders.push(new CategoryNode('Contexts', node.label, childCount));
        if (typeCount > 0)   folders.push(new CategoryNode('Types',    node.label, typeCount));
        if (streamCount > 0) folders.push(new CategoryNode('Streams',  node.label, streamCount));
        return folders;
      }

      // ── Category → child Contexts or Objects ──────────────────────────────
      if (node.kind === 'category') {
        if (node.category === 'Contexts') {
          // Project-level Contexts folder: contextName is the project name
          const proj = this.projects.find(
            p => p.name === node.contextName || p.contexts.has(node.contextName)
          );
          if (!proj) return [];          if (this.flatContexts) {
            // All contexts sorted alphabetically
            return [...proj.contexts.values()]
              .sort((a, b) => a.name.localeCompare(b.name))
              .map(c => new ContextNode(c.name, c.declaredInFile, c.declaredAtLine));
          }          // Top-level folder (contextName = project name) → root contexts;
          // nested folder (contextName = parent FQN) → direct children
          const isProjectRoot = proj.name === node.contextName;
          const children = isProjectRoot
            ? [...proj.contexts.values()].filter(c => isRootContext(c, proj.contexts))
            : getDirectChildContexts(node.contextName, proj.contexts);
          return children
            .sort((a, b) => a.name.localeCompare(b.name))
            .map(c => new ContextNode(c.name, c.declaredInFile, c.declaredAtLine));
        }
        const ctx = this.findContext(node.contextName);
        if (!ctx) return [];
        const isStream = node.category === 'Streams';
        return ctx.objects
          .filter(o => isStream ? o.kind === 'STREAM' : o.kind !== 'STREAM')
          .sort((a, b) => {
            // Within Types: enums → scalars → structs → derived; then alpha
            const typeOrder = (k: ObjectKind) =>
              k === 'TYPE_ENUM' ? 0 : k === 'TYPE_SCALAR' ? 1 : k === 'TYPE_STRUCT' ? 2 : 3;
            return typeOrder(a.kind) - typeOrder(b.kind) || a.name.localeCompare(b.name);
          })
          .map(o => new ObjectNode(o.name, o.kind, node.contextName, o.filePath, o.line));
      }

      return [];
    } catch (e: any) {
      log('getChildren error: ' + e?.message + '\n' + e?.stack);
      return [];
    }
  }

  getParent(node: KafkaSqlTreeNode): KafkaSqlTreeNode | null {
    switch (node.kind) {
      case 'projectsRoot':
      case 'clustersRoot':
        return null;
      case 'project':
      case 'noProject':
        return new ProjectsRootNode();
      case 'empty': {
        // parent is the project node — find which project has no content
        const proj = this.projects.find(
          p => p.contexts.size === 0 && p.rootObjects.length === 0
        );
        return proj ? new ProjectNode(proj.name, proj.kafkaRoot, proj.projectFile) : null;
      }
      case 'category': {
        // contextName is either a project name or a context FQN
        const proj = this.projects.find(p => p.name === node.contextName);
        if (proj) return new ProjectNode(proj.name, proj.kafkaRoot, proj.projectFile);
        const ctx = this.findContext(node.contextName);
        if (ctx) return new ContextNode(ctx.name, ctx.declaredInFile, ctx.declaredAtLine);
        return null;
      }
      case 'context': {
        // parent is the ProjectNode (flat) or the Contexts CategoryNode (nested)
        const proj = this.projects.find(p => p.contexts.has(node.label));
        if (!proj) return null;
        if (this.flatContexts) {
          return new ProjectNode(proj.name, proj.kafkaRoot, proj.projectFile);
        }
        const ctx = proj.contexts.get(node.label)!;
        if (isRootContext(ctx, proj.contexts)) {
          return new CategoryNode('Contexts', proj.name, 0);
        }
        const parts = node.label.split('.');
        for (let i = parts.length - 1; i >= 1; i--) {
          const parentFqn = parts.slice(0, i).join('.');
          if (proj.contexts.has(parentFqn)) {
            return new CategoryNode('Contexts', parentFqn, 0);
          }
        }
        return new CategoryNode('Contexts', proj.name, 0);
      }
      case 'object': {
        const ctx = this.findContext(node.contextName);
        if (!ctx) return null;
        if (this.flatContexts) {
          return new ContextNode(ctx.name, ctx.declaredInFile, ctx.declaredAtLine);
        }
        const category = node.objectKind === 'STREAM' ? 'Streams' : 'Types';
        return new CategoryNode(category, node.contextName, 0);
      }
      case 'connection': {
        return new ClustersRootNode();
      }
      case 'liveContext': {
        const key = node.connectionKey;
        const cached = this.liveCache.get(key);
        const contexts = (cached && cached !== 'loading' && !('error' in cached))
          ? cached as Map<string, ParsedContext>
          : null;
        // In nested mode: if this context has a parent in the live map, its parent
        // is the Contexts category of that parent context; otherwise it's the connection.
        if (!this.flatContexts && contexts) {
          const lastDot = node.label.lastIndexOf('.');
          if (lastDot >= 0) {
            const parentName = node.label.slice(0, lastDot);
            if (contexts.has(parentName)) {
              return new LiveCategoryNode('Contexts', parentName, key, 0);
            }
          }
        }
        // Flat mode or root context: parent is the ConnectionNode.
        const slash = key.indexOf('/');
        const projName = slash >= 0 ? key.slice(0, slash) : key;
        const connName = slash >= 0 ? key.slice(slash + 1) : '';
        const proj = this.projects.find(p => p.name === projName);
        if (!proj) return null;
        const conn = proj.connections.find(c => c.name === connName);
        if (!conn) return null;
        return new ConnectionNode(conn.name, proj.name, proj.projectFile, conn.bootstrapServers, conn.topic);
      }
      case 'liveCategory': {
        return new LiveContextNode(node.contextName, node.connectionKey);
      }
      case 'liveObject': {
        if (this.flatContexts) {
          return new LiveContextNode(node.contextName, node.connectionKey);
        }
        const category = node.objectKind === 'STREAM' ? 'Streams' : 'Types';
        return new LiveCategoryNode(category, node.contextName, node.connectionKey, 0);
      }
      case 'loading':
      case 'liveError':
        return null;
    }
  }

  private async fetchLiveModel(node: ConnectionNode): Promise<void> {
    const key = node.connectionKey;
    this.liveCache.set(key, 'loading');
    this._onDidChangeTreeData.fire(undefined);
    try {
      const result = await this.sendLspCommand('kafkasql.liveModel', [node.projectFile, node.label]);
      if (result && typeof result === 'object' && 'error' in result) {
        this.liveCache.set(key, { error: String(result.error) });
      } else {
        this.liveCache.set(key, this.liveResultToContextMap(result ?? []));
      }
    } catch (e: any) {
      this.liveCache.set(key, { error: e?.message ?? 'Connection failed' });
    }
    this._onDidChangeTreeData.fire(undefined);
  }

  private liveResultToContextMap(result: Array<{ name: string; context: string; kind: string }>): Map<string, ParsedContext> {
    const contexts = new Map<string, ParsedContext>();
    for (const entry of result) {
      if (entry.kind === 'CONTEXT') {
        // Use the full qualified name as key (context.name or just name if root)
        const fullName = entry.context ? `${entry.context}.${entry.name}` : entry.name;
        if (!contexts.has(fullName)) {
          contexts.set(fullName, { name: fullName, declaredInFile: '', declaredAtLine: -1, objects: [] });
        }
        continue;
      }
      const ctxName = entry.context || '';
      if (!contexts.has(ctxName)) {
        contexts.set(ctxName, { name: ctxName, declaredInFile: '', declaredAtLine: -1, objects: [] });
      }
      const kind: ObjectKind =
        entry.kind === 'STREAM'      ? 'STREAM'      :
        entry.kind === 'TYPE_ENUM'   ? 'TYPE_ENUM'   :
        entry.kind === 'TYPE_STRUCT' ? 'TYPE_STRUCT' :
        entry.kind === 'TYPE_SCALAR' ? 'TYPE_SCALAR' :
        entry.kind === 'TYPE_UNION'  ? 'TYPE_UNION'  :
        entry.kind === 'TYPE_DERIVED'? 'TYPE_DERIVED': 'TYPE_DERIVED';
      contexts.get(ctxName)!.objects.push({ name: entry.name, kind, filePath: '', line: -1 });
    }
    return contexts;
  }

  private findContext(name: string): ParsedContext | undefined {
    for (const proj of this.projects) {
      const ctx = proj.contexts.get(name);
      if (ctx) return ctx;
    }
    return undefined;
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** True if the context has no parent context in the project (i.e. it sits at the root). */
function isRootContext(ctx: ParsedContext, allContexts: Map<string, ParsedContext>): boolean {
  const lastDot = ctx.name.lastIndexOf('.');
  if (lastDot < 0) return true;                          // no dot → definitely root
  return !allContexts.has(ctx.name.slice(0, lastDot));   // root if parent not declared
}

/** Direct children of parentName — one level deep only. */
function getDirectChildContexts(
  parentName: string,
  allContexts: Map<string, ParsedContext>
): ParsedContext[] {
  const prefix = parentName + '.';
  return [...allContexts.values()].filter(c => {
    if (!c.name.startsWith(prefix)) return false;
    return !c.name.slice(prefix.length).includes('.');   // no further dots = direct child
  });
}

function icon(id: string, colorId: string): vscode.ThemeIcon {
  return new vscode.ThemeIcon(id, new vscode.ThemeColor(colorId));
}

function categoryIcon(_category: CategoryKind): vscode.ThemeIcon {
  return new vscode.ThemeIcon('folder');
}

function objectIcon(kind: ObjectKind): vscode.ThemeIcon {
  switch (kind) {
    case 'STREAM':       return icon('symbol-event',    'symbolIcon.eventForeground');
    case 'TYPE_ENUM':    return icon('symbol-enum',     'symbolIcon.enumForeground');
    case 'TYPE_STRUCT':  return icon('symbol-struct',   'symbolIcon.structForeground');
    case 'TYPE_SCALAR':  return icon('symbol-constant', 'symbolIcon.constantForeground');
    case 'TYPE_DERIVED': return icon('symbol-class',    'symbolIcon.classForeground');
    case 'TYPE_UNION':   return icon('symbol-misc',     'symbolIcon.typeParameterForeground');
  }
}

function objectKindLabel(kind: ObjectKind): string {
  switch (kind) {
    case 'STREAM':       return 'stream';
    case 'TYPE_ENUM':    return 'enum';
    case 'TYPE_STRUCT':  return 'struct';
    case 'TYPE_SCALAR':  return 'scalar';
    case 'TYPE_DERIVED': return 'type';
    case 'TYPE_UNION':   return 'union';
  }
}
