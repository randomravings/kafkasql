package kafkasql.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.WorkDoneProgressCancelParams;

public class KafkaSqlLsp implements LanguageServer, LanguageClientAware {
  private final KafkaSqlTextDocumentService docs = new KafkaSqlTextDocumentService();
  private String workspaceRoot = null;

  @Override
  public void connect(LanguageClient c) {
    System.err.println("[kafkasql-lsp] connect() called, client = " + (c == null ? "null" : c.getClass().getName()));
    this.docs.setClient(c);
    // also set client->server in case the docs need to call back
    // (optional) keep a reference for server-initiated messages
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    if (params.getWorkspaceFolders().isEmpty())
      throw new IllegalArgumentException("No workspace folders provided in initialize params");
    workspaceRoot = params.getWorkspaceFolders().get(0).getUri().replace("file:/", "").replace("//", "/");
    if(!Files.isDirectory(Paths.get(workspaceRoot), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException("Workspace root is not a directory: " + workspaceRoot);
    }
    docs.setWorkspaceRoot(workspaceRoot);

    ServerCapabilities caps = new ServerCapabilities();
    TextDocumentSyncOptions syncOpts = new TextDocumentSyncOptions();
    syncOpts.setOpenClose(true);
    syncOpts.setChange(TextDocumentSyncKind.Full);
    caps.setTextDocumentSync(syncOpts);
    InitializeResult res = new InitializeResult(caps);
    return CompletableFuture.completedFuture(res);
  }

  // accept the client's $/setTrace notification (lsp4j's default throws
  // UnsupportedOperationException)
  @Override
  public void setTrace(SetTraceParams params) {
    // no-op for now
  }

  // accept window/workDoneProgress/cancel notifications too
  @Override
  public void cancelProgress(WorkDoneProgressCancelParams params) {
    // no-op for now
  }

  @Override
  public CompletableFuture<Object> shutdown() {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void exit() {
  }

  @Override
  public TextDocumentService getTextDocumentService() {
    return docs;
  }

  // provide a WorkspaceService instance that implements the required method(s)
  @Override
  public WorkspaceService getWorkspaceService() {
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
