package kafkasql.lsp;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import kafkasql.core.lex.SqlStreamLexer;
import kafkasql.core.parse.SqlStreamParser;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.WorkDoneProgressCancelParams;

public class Main {

  public static void main(String[] args) {
    // keep all runtime logs on stderr to avoid corrupting the LSP stdio channel
    try {
      KafkaSqlServer server = new KafkaSqlServer();

      // use the process stdin/stdout as the LSP transport
      InputStream in = System.in;
      OutputStream out = System.out;

      Launcher<LanguageClient> launcher = Launcher.createLauncher(server, LanguageClient.class, in, out);
      LanguageClient client = launcher.getRemoteProxy();

      // connect server -> client
      server.connect(client);

      // start listening and block until the server stops
      Future<?> listening = launcher.startListening();
      try {
        listening.get(); // blocks; prevents process exit while client is connected
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        System.err.println("LSP listening interrupted: " + ie.getMessage());
      } catch (ExecutionException ee) {
        System.err.println("LSP listening failed: ");
        ee.getCause().printStackTrace(System.err);
      }
    } catch (Throwable t) {
      // ensure we print diagnostics to stderr (not stdout) so client/extension logs show them
      System.err.println("Fatal error starting language server:");
      t.printStackTrace(System.err);
      // do not call System.out/println here
    }
  }

  public static class KafkaSqlServer implements LanguageServer, LanguageClientAware {
    private LanguageClient client;
    private final KafkaTextDocumentService docs = new KafkaTextDocumentService();

    public void connect(LanguageClient c) { this.client = c; docs.setClient(c); }

    @Override public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
      ServerCapabilities caps = new ServerCapabilities();
      TextDocumentSyncOptions syncOpts = new TextDocumentSyncOptions();
      syncOpts.setOpenClose(true);
      syncOpts.setChange(TextDocumentSyncKind.Full);
      caps.setTextDocumentSync(syncOpts);
      InitializeResult res = new InitializeResult(caps);
      return CompletableFuture.completedFuture(res);
    }

    // accept the client's $/setTrace notification (lsp4j's default throws UnsupportedOperationException)
    @Override
    public void setTrace(SetTraceParams params) {
      // no-op: acknowledge trace setting changes from client
    }

    // accept window/workDoneProgress/cancel notifications too
    @Override
    public void cancelProgress(WorkDoneProgressCancelParams params) {
      // no-op
    }

    @Override public CompletableFuture<Object> shutdown() { return CompletableFuture.completedFuture(null); }
    @Override public void exit() {}
    @Override public TextDocumentService getTextDocumentService() { return docs; }

    // provide a WorkspaceService instance that implements the required method(s)
    @Override public WorkspaceService getWorkspaceService() {
      return new WorkspaceService() {
        @Override
        public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
          // no-op for now
        }
        @Override
        public void didChangeConfiguration(DidChangeConfigurationParams params) {
          // no-op
        }
      };
    }

    // removed the duplicate connect(LanguageClient) override here if present
  }

  public static class KafkaTextDocumentService implements TextDocumentService {
    private LanguageClient client;
    void setClient(LanguageClient client) { this.client = client; }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
      parseAndPublishDiagnostics(params.getTextDocument().getUri(), params.getTextDocument().getText());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
      String text = params.getContentChanges().get(params.getContentChanges().size() - 1).getText();
      parseAndPublishDiagnostics(params.getTextDocument().getUri(), text);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
      client.publishDiagnostics(new PublishDiagnosticsParams(params.getTextDocument().getUri(), new ArrayList<>()));
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
      try {
        CharStream input = CharStreams.fromString(text);
        SqlStreamLexer lexer = new SqlStreamLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SqlStreamParser parser = new SqlStreamParser(tokens);

        List<Diagnostic> diagnostics = new ArrayList<>();

        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        BaseErrorListener listener = new BaseErrorListener() {
          @Override
          public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                  int line, int charPositionInLine, String msg, RecognitionException e) {
            Range r = new Range(new Position(line - 1, charPositionInLine), new Position(line - 1, charPositionInLine + 1));
            Diagnostic d = new Diagnostic(r, msg, DiagnosticSeverity.Error, "antlr");
            diagnostics.add(d);
          }
        };
        lexer.addErrorListener(listener);
        parser.addErrorListener(listener);

        ParseTree tree = parser.script();

        // TODO: run semantic validator / AstBuilder here and append diagnostics

        client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
      } catch (Exception ex) {
        List<Diagnostic> diags = new ArrayList<>();
        Range r = new Range(new Position(0,0), new Position(0,1));
        diags.add(new Diagnostic(r, "Parser crash: " + ex.getMessage(), DiagnosticSeverity.Error, "kafkasql"));
        client.publishDiagnostics(new PublishDiagnosticsParams(uri, diags));
      }
    }
  }
}