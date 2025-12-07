package kafkasql.lsp;

import java.nio.file.*;

import kafkasql.runtime.diagnostics.Diagnostics;
import kafkasql.runtime.diagnostics.DiagnosticEntry;
import kafkasql.lang.input.Input;
import kafkasql.lang.input.StringInput;
import kafkasql.pipeline.Pipeline;
import kafkasql.pipeline.PipelineContext;
import kafkasql.pipeline.PipelineResult;
import kafkasql.pipeline.phases.LintPhase;
import kafkasql.pipeline.phases.ParsePhase;
import kafkasql.pipeline.phases.SemanticPhase;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class KafkaSqlTextDocumentService implements TextDocumentService {

  private LanguageClient client;
  private String workspaceRoot = null;
  private final Pipeline pipeline;

  public KafkaSqlTextDocumentService() {
    // Build pipeline once and reuse for all document changes
    this.pipeline = Pipeline.builder()
        .addPhase(new ParsePhase())
        .addPhase(new SemanticPhase())
        .addPhase(new LintPhase())
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
      parseAndPublishDiagnostics(uri, params.getTextDocument().getText());
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
      parseAndPublishDiagnostics(uri, text);
    } catch (Throwable t) {
      System.err.println("[kafkasql-lsp] didChange handler failed: " + t.getMessage());
      t.printStackTrace(System.err);
    }
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    if (client != null) {
      client.publishDiagnostics(new PublishDiagnosticsParams(params.getTextDocument().getUri(), new ArrayList<>()));
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

  private void parseAndPublishDiagnostics(String uri, String text) {

    System.err.println("[kafkasql-lsp] parseAndPublishDiagnostics for " + uri);

    Path workingDir = Path.of(this.workspaceRoot);
    Input currentInput = new StringInput(uri, text);
    
    // Build pipeline context
    PipelineContext context = PipelineContext.builder()
        .inputs(List.of(currentInput))
        .workingDir(workingDir)
        .includeResolution(true)
        .verbose(false)
        .build();
    
    // Execute pipeline
    PipelineResult result = pipeline.execute(context);
    
    // Send diagnostics
    if (result.diagnostics().all().isEmpty()) {
      sendOk(uri);
    } else {
      sendDiagnostics(uri, result.diagnostics());
    }
  }

  private void sendOk(String uri) {
    if (client == null) return;
    client.publishDiagnostics(new PublishDiagnosticsParams(uri, new ArrayList<>()));
  }

  private void sendDiagnostics(String uri, Diagnostics diags) {
    if (client == null || diags == null) return;

    List<org.eclipse.lsp4j.Diagnostic> lspDiags = new ArrayList<>();
    for (kafkasql.runtime.diagnostics.DiagnosticEntry entry : diags.all()) {
      
      // Skip INFO-level diagnostics (e.g., ANTLR ambiguity reports)
      if (entry.severity() == DiagnosticEntry.Severity.INFO) {
        continue;
      }
      
      kafkasql.runtime.diagnostics.Range r = entry.range();
      int startLine = Math.max(0, r.from().ln() - 1);
      int startChar = Math.max(0, r.from().ch());
      int endLine = Math.max(startLine, r.to().ln() - 1);
      int endChar = Math.max(0, r.to().ch());

      org.eclipse.lsp4j.Position start = new org.eclipse.lsp4j.Position(startLine, startChar);
      org.eclipse.lsp4j.Position end = new org.eclipse.lsp4j.Position(endLine, endChar);
      org.eclipse.lsp4j.Range lrange = new org.eclipse.lsp4j.Range(start, end);

      org.eclipse.lsp4j.Diagnostic d = new org.eclipse.lsp4j.Diagnostic(lrange, entry.message());
      // map severity
      switch (entry.severity()) {
        case INFO:
          // Already filtered above, but keep case for completeness
          d.setSeverity(org.eclipse.lsp4j.DiagnosticSeverity.Information);
          break;
        case WARNING:
          d.setSeverity(org.eclipse.lsp4j.DiagnosticSeverity.Warning);
          break;
        case ERROR:
          d.setSeverity(org.eclipse.lsp4j.DiagnosticSeverity.Error);
          break;
        case FATAL:
          d.setSeverity(org.eclipse.lsp4j.DiagnosticSeverity.Error);
          break;
        default:
          d.setSeverity(org.eclipse.lsp4j.DiagnosticSeverity.Error);
      }
      d.setSource("kafkasql");
      lspDiags.add(d);
    }

    client.publishDiagnostics(new PublishDiagnosticsParams(uri, lspDiags));
  }
}