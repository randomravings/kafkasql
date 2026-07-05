package kafkasql.lsp;

import java.net.URI;
import java.nio.file.*;
import java.util.regex.*;

import kafkasql.lang.compare.DiffEntry;
import kafkasql.lang.compare.RuleSet;
import kafkasql.lang.compare.ScriptDiffer;
import kafkasql.runtime.diagnostics.Diagnostics;
import kafkasql.runtime.diagnostics.DiagnosticEntry;
import kafkasql.lang.input.Input;
import kafkasql.lang.input.StringInput;
import kafkasql.linter.LintSettings;
import kafkasql.linter.LintSettingsLoader;
import kafkasql.pipeline.Pipeline;
import kafkasql.pipeline.PipelineContext;
import kafkasql.pipeline.PipelineResult;
import kafkasql.pipeline.phases.LintPhase;
import kafkasql.pipeline.phases.ParsePhase;
import kafkasql.pipeline.phases.SemanticPhase;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.*;
import org.eclipse.lsp4j.DiagnosticTag;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class KafkaSqlTextDocumentService implements TextDocumentService {

  // ─────────────────────────────────────────────────────────────────────────────
  // Comparison mode
  // ─────────────────────────────────────────────────────────────────────────────

  public enum ComparisonMode { NONE, GIT, FILE }

  private volatile ComparisonMode comparisonMode = ComparisonMode.GIT;
  private volatile String         comparisonFilePath = null; // only used when mode == FILE

  /**
   * Called from the workspace executeCommand handler when the user explicitly chooses a
   * diagnostics mode for a specific file.
   *
   * @param uri  the document URI
   * @param mode {@code "interactive"}, {@code "file"}, or {@code "auto"} (clears the override)
   */
  void setFileMode(String uri, String mode) {
    switch (mode.toLowerCase()) {
      case "interactive" -> fileModeOverrides.put(uri, Boolean.TRUE);
      case "file"        -> fileModeOverrides.put(uri, Boolean.FALSE);
      default            -> fileModeOverrides.remove(uri);   // "auto" — revert to path detection
    }
    System.err.println("[kafkasql-lsp] setFileMode " + uri + " -> " + mode);
    // Re-run diagnostics immediately so the editor reflects the new mode
    String text = openDocumentText.get(uri);
    if (text != null) {
      try {
        parseAndPublishDiagnostics(uri, text);
      } catch (Throwable t) {
        System.err.println("[kafkasql-lsp] refresh after setFileMode failed: " + t.getMessage());
      }
    }
  }

  /** Called from the workspace executeCommand handler when the user changes the mode. */
  void setComparisonMode(String mode, String filePath) {
    this.comparisonMode = switch (mode.toUpperCase()) {
      case "NONE" -> ComparisonMode.NONE;
      case "FILE" -> ComparisonMode.FILE;
      default     -> ComparisonMode.GIT;
    };
    this.comparisonFilePath = (this.comparisonMode == ComparisonMode.FILE) ? filePath : null;
    System.err.println("[kafkasql-lsp] comparison mode set to " + this.comparisonMode
        + (this.comparisonFilePath != null ? " (" + this.comparisonFilePath + ")" : ""));
    // Re-run diagnostics for every open document so the new mode takes effect immediately
    openDocumentText.forEach((uri, text) -> {
      try {
        parseAndPublishDiagnostics(uri, text);
      } catch (Throwable t) {
        System.err.println("[kafkasql-lsp] refresh after mode change failed for " + uri + ": " + t.getMessage());
      }
    });
  }

  // ─────────────────────────────────────────────────────────────────────────────

  private LanguageClient client;
  private String workspaceRoot = null;
  /** Full pipeline: parse + semantic analysis + lint. Used for model files inside the project root. */
  private final Pipeline pipeline;
  /**
   * Interactive pipeline: parse only. Used for misc/interactive scripts that live outside the
   * project's model root.  Semantic analysis is intentionally skipped because objects are not
   * defined locally — they are resolved from the live Kafka cluster at execution time.
   */
  private final Pipeline interactivePipeline;
  /** Last-seen text per open URI — used to re-run diagnostics when the comparison mode changes. */
  private final Map<String, String> openDocumentText = new ConcurrentHashMap<>();
  /**
   * Per-URI mode overrides set by the user via the status bar command.
   * {@code null} / absent  → auto-detect from file path
   * {@code Boolean.TRUE}   → forced interactive mode
   * {@code Boolean.FALSE}  → forced file mode
   */
  private final Map<String, Boolean> fileModeOverrides = new ConcurrentHashMap<>();

  public KafkaSqlTextDocumentService() {
    this.pipeline = Pipeline.builder()
        .addPhase(new ParsePhase())
        .addPhase(new SemanticPhase())
        .addPhase(new LintPhase())
        .build();
    this.interactivePipeline = Pipeline.builder()
        .addPhase(new ParsePhase())
        .build();
  }

  void setClient(LanguageClient client) {
    this.client = client;
  }

  void setWorkspaceRoot(String root) {
    this.workspaceRoot = root;
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    String uri = params.getTextDocument().getUri();
    try {
      String text = params.getTextDocument().getText();
      openDocumentText.put(uri, text);
      parseAndPublishDiagnostics(uri, text);
    } catch (Throwable t) {
      System.err.println("[kafkasql-lsp] didOpen handler failed: " + t.getMessage());
      t.printStackTrace(System.err);
    }
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    String uri = params.getTextDocument().getUri();
    try {
      String text = params.getContentChanges().get(params.getContentChanges().size() - 1).getText();
      openDocumentText.put(uri, text);
      parseAndPublishDiagnostics(uri, text);
    } catch (Throwable t) {
      System.err.println("[kafkasql-lsp] didChange handler failed: " + t.getMessage());
      t.printStackTrace(System.err);
    }
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    String uri = params.getTextDocument().getUri();
    openDocumentText.remove(uri);
    if (client != null) {
      client.publishDiagnostics(new PublishDiagnosticsParams(uri, new ArrayList<>()));
    }
  }

  @Override
  public void willSave(WillSaveTextDocumentParams params) {
    // no-op
  }

  @Override
  public CompletableFuture<List<TextEdit>> willSaveWaitUntil(WillSaveTextDocumentParams params) {
    return CompletableFuture.completedFuture(Collections.emptyList());
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
    // no-op
  }

  /** Load lint settings for the given file. Tries .proj.toml first, then kafkasql.rules.toml. */
  private LintSettings loadLintSettings(Path filePath, Path workingDir) {
    if (filePath != null) {
      Optional<KafkaSqlProject> projectOpt = KafkaSqlProject.findFor(filePath);
      if (projectOpt.isPresent()) {
        try {
          return LintSettingsLoader.load(projectOpt.get().projectFile());
        } catch (Exception e) {
          System.err.println("[kafkasql-lsp] lint settings from project file: " + e.getMessage());
        }
      }
    }
    if (workingDir != null) {
      Path configFile = workingDir.resolve("kafkasql.rules.toml");
      if (Files.exists(configFile)) {
        try {
          return LintSettingsLoader.load(configFile);
        } catch (Exception e) {
          System.err.println("[kafkasql-lsp] lint settings from kafkasql.rules.toml: " + e.getMessage());
        }
      }
    }
    return LintSettings.defaults();
  }

  private void parseAndPublishDiagnostics(String uri, String text) {

    System.err.println("[kafkasql-lsp] parseAndPublishDiagnostics for " + uri);

    // Resolve includes relative to the file's own directory so that
    // INCLUDE '../foo.kafka' and INCLUDE 'Sibling.kafka' work as expected.
    Path workingDir;
    try {
      workingDir = Path.of(URI.create(uri)).toAbsolutePath().normalize().getParent();
      if (workingDir == null) workingDir = Path.of(this.workspaceRoot);
    } catch (Exception e) {
      workingDir = Path.of(this.workspaceRoot);
    }
    System.err.println("[kafkasql-lsp]   workingDir = " + workingDir);

    // Resolve the file path (used for project lookup and lint settings)
    Path filePath;
    try {
      filePath = Path.of(URI.create(uri)).toAbsolutePath().normalize();
    } catch (Exception e) {
      filePath = null;
    }

    // Determine interactive vs file mode.
    // Priority: explicit per-file override (set by the user via status bar) → path-based auto-detection.
    boolean interactive;
    if (fileModeOverrides.containsKey(uri)) {
      interactive = fileModeOverrides.get(uri);
      System.err.println("[kafkasql-lsp]   interactive=" + interactive + " (override)");
    } else {
      interactive = false;
      if (filePath != null) {
        Optional<KafkaSqlProject> projOpt = KafkaSqlProject.findFor(filePath);
        if (projOpt.isPresent()) {
          interactive = projOpt.get().isInteractiveFile(filePath);
        }
      }
      System.err.println("[kafkasql-lsp]   interactive=" + interactive + " (auto)");
    }

    Input currentInput = new StringInput(uri, text);
    
    // Load lint settings from .proj.toml (if in a project) or kafkasql.rules.toml (if present)
    LintSettings lintSettings = loadLintSettings(filePath, workingDir);

    // Build pipeline context; interactive files skip INCLUDE resolution so that
    // missing model files don't cause file-not-found errors during parsing.
    PipelineContext context = PipelineContext.builder()
        .inputs(List.of(currentInput))
        .workingDir(workingDir)
        .includeResolution(!interactive)
        .verbose(false)
        .lintSettings(lintSettings)
        .build();

    // In interactive mode use the parse-only pipeline so that "object not found" and other
    // semantic errors are not reported — the semantic model lives on the live cluster.
    PipelineResult result = (interactive ? interactivePipeline : pipeline).execute(context);
    System.err.println("[kafkasql-lsp]   pipeline: errors=" + result.diagnostics().hasError()
        + ", warnings=" + result.diagnostics().hasWarning()
        + ", diags=" + result.diagnostics().all().size());

    // Start with parse/semantic/lint diagnostics
    List<org.eclipse.lsp4j.Diagnostic> lspDiags = buildPipelineDiagnostics(result.diagnostics());

    if (interactive) {
      // Gray out any INCLUDE statements — they are ignored in interactive mode.
      lspDiags.addAll(buildInteractiveIncludeHints(text));
    } else {
      // Project-convention check (folder must align with declared context)
      lspDiags.addAll(buildProjectConventionDiagnostics(uri, text));

      // Project object-count check (one CREATE per file)
      lspDiags.addAll(buildProjectObjectCountDiagnostics(uri, text));

      // Compatibility diff against git HEAD (only when parsed cleanly)
      if (!result.diagnostics().hasError()) {
        lspDiags.addAll(buildCompatibilityDiagnostics(uri, text, workingDir));
      } else {
        System.err.println("[kafkasql-lsp]   skipping compat diff (parse/semantic errors present)");
      }
    }

    System.err.println("[kafkasql-lsp]   publishing " + lspDiags.size() + " diagnostic(s) total");
    if (client != null) {
      client.publishDiagnostics(new PublishDiagnosticsParams(uri, lspDiags));
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Interactive mode: gray-out INCLUDE hints
  // ─────────────────────────────────────────────────────────────────────────────

  private static final Pattern INCLUDE_LINE_PATTERN =
      Pattern.compile("(?im)^(\\s*INCLUDE\\s+[^;\\n]+;?)");

  /**
   * In interactive mode, INCLUDE statements are ignored — all types are resolved
   * from the live cluster.  This method emits a {@code Hint/Unnecessary} diagnostic
   * on each INCLUDE line so VS Code dims the text, making it clear those lines have
   * no effect.
   */
  private List<org.eclipse.lsp4j.Diagnostic> buildInteractiveIncludeHints(String text) {
    List<org.eclipse.lsp4j.Diagnostic> diags = new ArrayList<>();
    Matcher m = INCLUDE_LINE_PATTERN.matcher(text);
    while (m.find()) {
      Position start = offsetToPosition(text, m.start(1));
      Position end   = offsetToPosition(text, m.end(1));
      var diag = new org.eclipse.lsp4j.Diagnostic(
          new Range(start, end),
          "INCLUDE is ignored in interactive mode — symbols are resolved from the live cluster.",
          DiagnosticSeverity.Hint,
          "kafkasql-interactive"
      );
      diag.setTags(List.of(DiagnosticTag.Unnecessary));
      diags.add(diag);
    }
    return diags;
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Project convention diagnostics (folder ↔ context alignment)
  // ─────────────────────────────────────────────────────────────────────────────

  private static final Pattern USE_CONTEXT_PATTERN =
      Pattern.compile("(?i)\\bUSE\\s+CONTEXT\\s+([\\w.]+)\\s*;");

  /**
   * Warns when a file's folder path (relative to the project's kafka root) does not
   * match the context declared via {@code USE CONTEXT}.
   *
   * <p>Rule: a file at {@code <kafkaRoot>/a/b/File.kafka} must declare
   * {@code USE CONTEXT a.b} (the last USE CONTEXT in the file is authoritative).
   */
  private List<org.eclipse.lsp4j.Diagnostic> buildProjectConventionDiagnostics(
      String uri, String text) {

    List<org.eclipse.lsp4j.Diagnostic> diags = new ArrayList<>();

    // Resolve file path from URI
    Path filePath;
    try {
      filePath = Path.of(URI.create(uri)).toAbsolutePath().normalize();
    } catch (Exception e) {
      return diags;
    }

    // Find the nearest .proj.toml
    Optional<KafkaSqlProject> projectOpt = KafkaSqlProject.findFor(filePath);
    if (projectOpt.isEmpty()) return diags;
    KafkaSqlProject project = projectOpt.get();
    System.err.println("[kafkasql-lsp] project: " + project);

    // Determine the expected context from the folder path
    Optional<String> expectedCtx = project.expectedContext(filePath);
    if (expectedCtx.isEmpty()) return diags; // root-level file — no constraint

    // Find the last USE CONTEXT statement in the file
    Matcher m = USE_CONTEXT_PATTERN.matcher(text);
    String  lastContext      = null;
    int     lastContextStart = -1; // offset of the context name within text
    while (m.find()) {
      lastContext      = m.group(1);
      lastContextStart = m.start(1);
    }

    if (lastContext == null) {
      // No USE CONTEXT but inside a context folder
      var range = new Range(new Position(0, 0), new Position(0, 0));
      diags.add(new org.eclipse.lsp4j.Diagnostic(
          range,
          "File is in context folder '" + expectedCtx.get() +
              "' but has no USE CONTEXT statement. Expected: USE CONTEXT " + expectedCtx.get() + ";",
          DiagnosticSeverity.Warning,
          "kafkasql-project"
      ));
    } else if (!lastContext.equalsIgnoreCase(expectedCtx.get())) {
      // Context mismatch — highlight the declared context name
      Position start = offsetToPosition(text, lastContextStart);
      Position end   = new Position(start.getLine(), start.getCharacter() + lastContext.length());
      diags.add(new org.eclipse.lsp4j.Diagnostic(
          new Range(start, end),
          "Context '" + lastContext + "' doesn't match folder path '" + expectedCtx.get() +
              "'. Move file to kafka/" + expectedCtx.get().replace('.', '/') + "/ or change the context.",
          DiagnosticSeverity.Warning,
          "kafkasql-project"
      ));
    }

    return diags;
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Project object-count diagnostics (one CREATE per file in project mode)
  // ─────────────────────────────────────────────────────────────────────────────

  private static final Pattern CREATE_STMT_PATTERN =
      Pattern.compile("(?i)^\\s*CREATE\\s+(TYPE|STREAM|CONTEXT)\\b");

  /**
   * In project mode, each file should declare exactly one object.
   * Warns on the second (and each subsequent) CREATE statement found in the file.
   */
  private List<org.eclipse.lsp4j.Diagnostic> buildProjectObjectCountDiagnostics(
      String uri, String text) {

    List<org.eclipse.lsp4j.Diagnostic> diags = new ArrayList<>();

    Path filePath;
    try {
      filePath = Path.of(URI.create(uri)).toAbsolutePath().normalize();
    } catch (Exception e) {
      return diags;
    }
    if (KafkaSqlProject.findFor(filePath).isEmpty()) return diags;

    String[] lines = text.split("\n", -1);
    boolean inBlockComment = false;
    int createCount = 0;

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];

      // Track block comments
      if (inBlockComment) {
        if (line.contains("*/")) inBlockComment = false;
        continue;
      }
      String stripped = line.stripLeading();
      if (stripped.startsWith("/*")) {
        if (!stripped.contains("*/")) inBlockComment = true;
        continue;
      }
      // Strip trailing single-line comment
      String effective = line.replaceFirst("--.*$", "");

      Matcher m = CREATE_STMT_PATTERN.matcher(effective);
      if (m.find()) {
        createCount++;
        if (createCount > 1) {
          int col = effective.toUpperCase().indexOf("CREATE");
          if (col < 0) col = 0;
          String kind = m.group(1).toUpperCase();
          diags.add(new org.eclipse.lsp4j.Diagnostic(
              new Range(new Position(i, col), new Position(i, col + 6)),
              "Project convention: each file should declare exactly one object. " +
                  "Consider moving this " + kind + " to its own file.",
              DiagnosticSeverity.Warning,
              "kafkasql-project"
          ));
        }
      }
    }
    return diags;
  }

  private static Position offsetToPosition(String text, int offset) {
    int line      = 0;
    int lineStart = 0;
    for (int i = 0; i < offset && i < text.length(); i++) {
      if (text.charAt(i) == '\n') {
        line++;
        lineStart = i + 1;
      }
    }
    return new Position(line, offset - lineStart);
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Compatibility diagnostics (baseline diff)
  // ─────────────────────────────────────────────────────────────────────────────

  private List<org.eclipse.lsp4j.Diagnostic> buildCompatibilityDiagnostics(
      String uri, String currentText, Path workingDir) {

    ComparisonMode mode = this.comparisonMode;
    System.err.println("[kafkasql-lsp] compat diff: mode=" + mode);

    return switch (mode) {
      case NONE -> {
        System.err.println("[kafkasql-lsp] compat diff: disabled");
        yield List.of();
      }
      case GIT  -> buildGitBaselineDiagnostics(uri, currentText, workingDir);
      case FILE -> buildFileBaselineDiagnostics(uri, currentText, workingDir);
    };
  }

  private List<org.eclipse.lsp4j.Diagnostic> buildGitBaselineDiagnostics(
      String uri, String currentText, Path workingDir) {

    Path filePath;
    try {
      filePath = Path.of(URI.create(uri));
    } catch (Exception e) {
      System.err.println("[kafkasql-lsp] compat diff: could not parse URI '" + uri + "': " + e.getMessage());
      return List.of();
    }

    Optional<Path> gitRoot = GitBaseline.resolveGitRoot(workingDir);
    System.err.println("[kafkasql-lsp] compat diff: workingDir=" + workingDir
      + ", gitRoot=" + gitRoot.map(p -> p.toString()).orElse("<none — not a git repo>"));

    Optional<String> baseline;
    try {
      baseline = GitBaseline.getContent(workingDir, filePath);
    } catch (Exception e) {
      System.err.println("[kafkasql-lsp] compat diff: git lookup failed for " + filePath + ": " + e.getMessage());
      return List.of();
    }

    if (baseline.isEmpty()) {
      System.err.println("[kafkasql-lsp] compat diff: no git baseline for " + filePath.getFileName() + " (new/untracked — not committed at HEAD)");
      return List.of();
    }
    System.err.println("[kafkasql-lsp] compat diff: git baseline loaded for " + filePath.getFileName()
        + " (" + baseline.get().length() + " chars)");

    return diffAgainstBaseline(baseline.get(), "git:HEAD:" + filePath.getFileName(),
        currentText, uri, workingDir, filePath.getFileName().toString());
  }

  private List<org.eclipse.lsp4j.Diagnostic> buildFileBaselineDiagnostics(
      String uri, String currentText, Path workingDir) {

    String refPath = this.comparisonFilePath;
    if (refPath == null || refPath.isBlank()) {
      System.err.println("[kafkasql-lsp] compat diff: FILE mode selected but no reference file configured");
      return List.of();
    }

    Path filePath;
    try {
      filePath = Path.of(URI.create(uri));
    } catch (Exception e) {
      System.err.println("[kafkasql-lsp] compat diff: could not parse URI '" + uri + "': " + e.getMessage());
      return List.of();
    }

    String baseline;
    try {
      baseline = Files.readString(Path.of(refPath));
    } catch (Exception e) {
      System.err.println("[kafkasql-lsp] compat diff: could not read reference file '" + refPath + "': " + e.getMessage());
      return List.of();
    }
    System.err.println("[kafkasql-lsp] compat diff: file baseline loaded from " + refPath
        + " (" + baseline.length() + " chars)");

    return diffAgainstBaseline(baseline, refPath, currentText, uri, workingDir,
        filePath.getFileName().toString());
  }

  private List<org.eclipse.lsp4j.Diagnostic> diffAgainstBaseline(
      String baselineContent, String baselineUri,
      String currentText, String currentUri,
      Path workingDir, String displayName) {

    try {
      List<DiffEntry> entries = ScriptDiffer.diff(
          baselineContent, baselineUri,
          currentText,     currentUri,
          workingDir,
          /* resolveIncludes= */ true,
          RuleSet.defaults()
      ).flatten();

      List<org.eclipse.lsp4j.Diagnostic> diags = DiffDiagnosticsAdapter.toDiagnostics(entries);
      System.err.println("[kafkasql-lsp] compat diff: " + entries.size() + " change(s), "
          + diags.size() + " diagnostic(s) for " + displayName);
      return diags;

    } catch (IllegalArgumentException e) {
      System.err.println("[kafkasql-lsp] compat diff: baseline parse failed for "
          + displayName + " — skipping: " + e.getMessage());
      return List.of();
    } catch (Exception e) {
      System.err.println("[kafkasql-lsp] compat diff: unexpected error for "
          + displayName + ": " + e.getMessage());
      return List.of();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Pipeline diagnostics
  // ─────────────────────────────────────────────────────────────────────────────

  private List<org.eclipse.lsp4j.Diagnostic> buildPipelineDiagnostics(Diagnostics diags) {
    List<org.eclipse.lsp4j.Diagnostic> result = new ArrayList<>();
    if (diags == null) return result;

    for (DiagnosticEntry entry : diags.all()) {
      // Skip INFO-level diagnostics (e.g., ANTLR ambiguity reports)
      if (entry.severity() == DiagnosticEntry.Severity.INFO) continue;

      kafkasql.runtime.diagnostics.Range r = entry.range();
      int startLine = Math.max(0, r.from().ln() - 1);
      int startChar = Math.max(0, r.from().ch());
      int endLine   = Math.max(startLine, r.to().ln() - 1);
      int endChar   = Math.max(0, r.to().ch());

      org.eclipse.lsp4j.Diagnostic d = new org.eclipse.lsp4j.Diagnostic(
          new org.eclipse.lsp4j.Range(
              new org.eclipse.lsp4j.Position(startLine, startChar),
              new org.eclipse.lsp4j.Position(endLine, endChar)),
          entry.message());

      d.setSeverity(switch (entry.severity()) {
        case WARNING -> DiagnosticSeverity.Warning;
        case ERROR   -> DiagnosticSeverity.Error;
        case FATAL   -> DiagnosticSeverity.Error;
        default      -> DiagnosticSeverity.Error;
      });
      d.setSource("kafkasql");
      result.add(d);
    }

    return result;
  }
}
