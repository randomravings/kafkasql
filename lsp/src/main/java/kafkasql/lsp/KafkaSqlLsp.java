package kafkasql.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.*;

import kafkasql.lang.semantic.symbol.SymbolTable;
import kafkasql.lang.syntax.ast.decl.*;
import kafkasql.persistence.ModelStore;
import kafkasql.runtime.Name;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;

import kafkasql.lang.compare.DiffEntry;
import kafkasql.lang.compare.ScriptDiff;
import kafkasql.lang.compare.ScriptDiffer;

import com.google.gson.JsonElement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
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
    caps.setExecuteCommandProvider(
        new ExecuteCommandOptions(List.of("kafkasql.semanticDiff", "kafkasql.setComparisonMode")));
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

      @Override
      public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        if ("kafkasql.semanticDiff".equals(params.getCommand())) {
          return CompletableFuture.supplyAsync(() -> handleSemanticDiff(params.getArguments()));
        }
        if ("kafkasql.setComparisonMode".equals(params.getCommand())) {
          return CompletableFuture.supplyAsync(() -> handleSetComparisonMode(params.getArguments()));
        }
        if ("kafkasql.liveModel".equals(params.getCommand())) {
          return CompletableFuture.supplyAsync(() -> handleLiveModel(params.getArguments()));
        }
        return CompletableFuture.completedFuture(null);
      }
    };
  }

  private Object handleSetComparisonMode(List<Object> args) {
    try {
      String mode     = args.size() > 0 ? ((JsonElement) args.get(0)).getAsString() : "GIT";
      String filePath = args.size() > 1 && !((JsonElement) args.get(1)).isJsonNull()
                        ? ((JsonElement) args.get(1)).getAsString() : null;
      docs.setComparisonMode(mode, filePath);
      return null;
    } catch (Exception e) {
      System.err.println("[kafkasql-lsp] setComparisonMode error: " + e.getMessage());
      return Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
    }
  }

  private Object handleSemanticDiff(List<Object> args) {
    try {
      if (args == null || args.size() < 2) {
        return Map.of("error", "Expected arguments: [leftPath, rightPath]");
      }
      // lsp4j deserializes executeCommand arguments via Gson as JsonElement, not String
      String leftPath  = ((JsonElement) args.get(0)).getAsString();
      String rightPath = ((JsonElement) args.get(1)).getAsString();
      Path root  = Path.of(workspaceRoot);
      Path left  = Path.of(leftPath).normalize();
      Path right = Path.of(rightPath).normalize();
      ScriptDiff diff = ScriptDiffer.diff(left, right, root, true, true);
      List<DiffEntry> entries = diff.flatten();
      System.err.println("[kafkasql-lsp] semanticDiff returned " + entries.size() + " entries");
      return entries;
    } catch (Exception e) {
      System.err.println("[kafkasql-lsp] semanticDiff error: " + e.getMessage());
      e.printStackTrace(System.err);
      return Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
    }
  }

  private Object handleLiveModel(List<Object> args) {
    try {
      if (args == null || args.size() < 2) {
        return Map.of("error", "Expected arguments: [projectFilePath, connectionName]");
      }
      String projectFilePath = ((JsonElement) args.get(0)).getAsString();
      String connectionName  = ((JsonElement) args.get(1)).getAsString();

      Path projectFile = Path.of(projectFilePath).normalize();
      Path projectDir  = projectFile.getParent();
      List<ConnectionConfig> connections = ConnectionsLoader.load(projectDir);
      ConnectionConfig conn = connections.stream()
          .filter(c -> c.name().equals(connectionName))
          .findFirst()
          .orElse(null);
      if (conn == null) {
        return Map.of("error", "Connection '" + connectionName + "' not found in connections.toml");
      }

      Properties props = new Properties();
      props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  conn.bootstrapServers());
      props.put(ConsumerConfig.GROUP_ID_CONFIG,            "kafkasql-lsp-" + UUID.randomUUID());
      props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   ByteArrayDeserializer.class.getName());
      props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
      props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  "earliest");
      props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

      SymbolTable symbols = new SymbolTable();
      try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(props)) {
        kafkasql.runtime.stream.StreamReader<sys.schema.SymbolEventLog> reader =
            new kafkasql.io.ReadStream<>(
                conn.topic(),
                consumer,
                bytes -> sys.schema.SymbolEventLog.readFrom(new java.io.ByteArrayInputStream(bytes))
            );
        kafkasql.persistence.EventLogReader logReader =
            new kafkasql.persistence.EventLogReader(reader, symbols);
        logReader.replayAll();
      }

      // Convert symbol table entries to a serializable list
      List<Map<String, String>> result = new ArrayList<>();
      for (Map.Entry<Name, kafkasql.lang.syntax.ast.decl.Decl> entry : symbols._decl.entrySet()) {
        Name name = entry.getKey();
        kafkasql.lang.syntax.ast.decl.Decl decl = entry.getValue();
        String kind = switch (decl) {
          case ContextDecl ignored                      -> "CONTEXT";
          case StreamDecl  ignored                      -> "STREAM";
          case TypeDecl    td when td.kind() instanceof EnumDecl        -> "TYPE_ENUM";
          case TypeDecl    td when td.kind() instanceof StructDecl      -> "TYPE_STRUCT";
          case TypeDecl    td when td.kind() instanceof ScalarDecl      -> "TYPE_SCALAR";
          case TypeDecl    td when td.kind() instanceof UnionDecl       -> "TYPE_UNION";
          case TypeDecl    td when td.kind() instanceof DerivedTypeDecl -> "TYPE_DERIVED";
          default                                                        -> "UNKNOWN";
        };
        result.add(Map.of("name", name.name(), "context", name.context(), "kind", kind));
      }
      System.err.println("[kafkasql-lsp] liveModel '" + connectionName + "' returned " + result.size() + " symbols");
      return result;
    } catch (Exception e) {
      System.err.println("[kafkasql-lsp] liveModel error: " + e.getMessage());
      e.printStackTrace(System.err);
      return Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
    }
  }
}
