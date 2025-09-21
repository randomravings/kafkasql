package kafkasql.lsp;

import java.nio.file.*;

import kafkasql.core.KafkaSqlParser;
import kafkasql.core.ParseArgs;
import kafkasql.core.ParseResult;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import kafkasql.core.Diagnostics;

public class KafkaSqlTextDocumentService implements TextDocumentService {

  private LanguageClient client;
  private String workspaceRoot = null;

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

    ParseArgs args = new ParseArgs(Path.of(this.workspaceRoot), true, false);
    ParseResult pr = null;
    
    pr = KafkaSqlParser.parseText(text, args);
    if (pr.diags().hasErrors()) {
      sendDiagnostics(uri, pr.diags());
      return;
    }

    pr = KafkaSqlParser.validate(pr);
    if (pr.diags().hasErrors()) {
      sendDiagnostics(uri, pr.diags());
      return;
    }

    sendOk(uri);
  }

  private void sendOk(String uri) {
    if (client == null) return;
    client.publishDiagnostics(new PublishDiagnosticsParams(uri, new ArrayList<>()));
  }

  private void sendDiagnostics(String uri, Diagnostics diags) {
    if (client == null || diags == null) return;

    List<org.eclipse.lsp4j.Diagnostic> lspDiags = new ArrayList<>();
    for (kafkasql.core.DiagnosticEntry entry : diags.all()) {
      kafkasql.core.Range r = entry.range();
      // assume Pos has line() and col() 1-based
      int startLine = Math.max(0, r.start().ln() - 1);
      int startChar = Math.max(0, r.start().ch() - 1);
      int endLine = Math.max(startLine, r.end().ln() - 1);
      int endChar = Math.max(startChar, r.end().ch() - 1);

      org.eclipse.lsp4j.Position start = new org.eclipse.lsp4j.Position(startLine, startChar);
      org.eclipse.lsp4j.Position end = new org.eclipse.lsp4j.Position(endLine, endChar);
      org.eclipse.lsp4j.Range lrange = new org.eclipse.lsp4j.Range(start, end);

      org.eclipse.lsp4j.Diagnostic d = new org.eclipse.lsp4j.Diagnostic(lrange, entry.message());
      // map severity
      switch (entry.severity()) {
        case INFO:
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