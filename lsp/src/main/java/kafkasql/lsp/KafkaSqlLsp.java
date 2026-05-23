package kafkasql.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.*;

import kafkasql.lang.semantic.symbol.SymbolTable;
import kafkasql.lang.syntax.ast.decl.*;
import kafkasql.runtime.Name;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import kafkasql.lang.KafkaSqlArgs;
import kafkasql.lang.KafkaSqlParser;
import kafkasql.lang.ParseResult;
import kafkasql.lang.compare.DiffEntry;
import kafkasql.lang.compare.RuleSet;
import kafkasql.lang.compare.ScriptDiff;
import kafkasql.lang.compare.ScriptDiffer;
import kafkasql.lang.input.StringInput;
import kafkasql.lang.printer.SourceWriter;
import kafkasql.lang.syntax.ast.Script;
import kafkasql.engine.KafkaSqlEngine;
import kafkasql.engine.KafkaSqlEngine.StreamRecord;
import kafkasql.io.ValueCodec;
import kafkasql.lang.semantic.SemanticModel;
import kafkasql.io.SchemaMarker;
import kafkasql.lang.syntax.ast.stmt.CreateStmt;
import kafkasql.lang.syntax.ast.stmt.ReadMode;
import kafkasql.lang.syntax.ast.stmt.ReadStmt;
import kafkasql.lang.syntax.ast.stmt.StopAfter;
import kafkasql.lang.syntax.ast.stmt.Stmt;
import kafkasql.lang.syntax.ast.stmt.UseStmt;
import kafkasql.lang.syntax.ast.stmt.WriteStmt;
import kafkasql.runtime.type.StructType;
import kafkasql.lang.syntax.ast.use.ContextUse;
import kafkasql.persistence.EventLogWriter;
import kafkasql.runtime.value.StructValue;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.TopicPartition;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import com.google.gson.JsonElement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.WorkDoneProgressCancelParams;
import org.eclipse.lsp4j.jsonrpc.Endpoint;
import java.util.function.Consumer;

public class KafkaSqlLsp implements LanguageServer, LanguageClientAware {
  private final KafkaSqlTextDocumentService docs = new KafkaSqlTextDocumentService();
  private String workspaceRoot = null;
  /** Set to true by kafkasql.cancelExecution; reset at the start of each execution. */
  private final AtomicBoolean executionCancelled = new AtomicBoolean(false);
  private LanguageClient languageClient;

  @Override
  public void connect(LanguageClient c) {
    System.err.println("[kafkasql-lsp] connect() called, client = " + (c == null ? "null" : c.getClass().getName()));
    this.languageClient = c;
    this.docs.setClient(c);
  }

  /** Send a single record row to the extension as a streaming notification. */
  private void notifyRecord(Map<String, Object> row) {
    if (languageClient instanceof Endpoint ep) {
      ep.notify("kafkasql/record", row);
    }
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    try {
      if (params.getWorkspaceFolders() == null || params.getWorkspaceFolders().isEmpty()) {
        System.err.println("[kafkasql-lsp] initialize: no workspace folders — proceeding without a root");
      } else {
        String folderUri = params.getWorkspaceFolders().get(0).getUri();
        // Use java.net.URI to correctly decode percent-encoded paths and strip scheme/authority
        workspaceRoot = java.net.URI.create(folderUri).getPath();
        // Trim any trailing slash so Paths.get() resolves consistently
        if (workspaceRoot != null && workspaceRoot.endsWith("/") && workspaceRoot.length() > 1) {
          workspaceRoot = workspaceRoot.substring(0, workspaceRoot.length() - 1);
        }
        if (workspaceRoot == null || !Files.isDirectory(Paths.get(workspaceRoot), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
          System.err.println("[kafkasql-lsp] initialize warning: workspace root is not a directory: " + workspaceRoot);
        }
        docs.setWorkspaceRoot(workspaceRoot);
        System.err.println("[kafkasql-lsp] initialize: workspaceRoot=" + workspaceRoot);
      }
    } catch (Exception e) {
      System.err.println("[kafkasql-lsp] initialize error resolving workspace root: " + e.getMessage());
      e.printStackTrace(System.err);
    }

    ServerCapabilities caps = new ServerCapabilities();
    TextDocumentSyncOptions syncOpts = new TextDocumentSyncOptions();
    syncOpts.setOpenClose(true);
    syncOpts.setChange(TextDocumentSyncKind.Full);
    caps.setTextDocumentSync(syncOpts);
    // Only advertise commands that the LanguageClient itself routes to the server
    // via its ExecuteCommandFeature. Commands that the extension already registers
    // as VS Code commands (diffWithCluster, deployToCluster) must NOT appear here,
    // or the LanguageClient will try to re-register them and throw "already exists".
    caps.setExecuteCommandProvider(
        new ExecuteCommandOptions(List.of(
            "kafkasql.semanticDiff",
            "kafkasql.setComparisonMode",
            "kafkasql.setFileMode",
            "kafkasql.liveModel"
            // kafkasql.cancelExecution is intentionally omitted — it is registered
            // as a VS Code command by the extension directly and called via
            // sendRequest('workspace/executeCommand', ...), so advertising it here
            // would cause the LSP client to try to re-register it and throw
            // "command already exists".
        )));
    return CompletableFuture.completedFuture(new InitializeResult(caps));
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
        if ("kafkasql.setFileMode".equals(params.getCommand())) {
          return CompletableFuture.supplyAsync(() -> handleSetFileMode(params.getArguments()));
        }
        if ("kafkasql.liveModel".equals(params.getCommand())) {
          return CompletableFuture.supplyAsync(() -> handleLiveModel(params.getArguments()));
        }
        if ("kafkasql.diffWithCluster".equals(params.getCommand())) {
          return CompletableFuture.supplyAsync(() -> handleDiffWithCluster(params.getArguments()));
        }
        if ("kafkasql.deployToCluster".equals(params.getCommand())) {
          return CompletableFuture.supplyAsync(() -> handleDeployToCluster(params.getArguments()));
        }
        if ("kafkasql.diffProjectWithCluster".equals(params.getCommand())) {
          return CompletableFuture.supplyAsync(() -> handleDiffProjectWithCluster(params.getArguments()));
        }
        if ("kafkasql.cancelExecution".equals(params.getCommand())) {
          executionCancelled.set(true);
          return CompletableFuture.completedFuture(null);
        }
        if ("kafkasql.executeStatement".equals(params.getCommand())) {
          return CompletableFuture.supplyAsync(() -> handleExecuteStatement(params.getArguments()));
        }
        if ("kafkasql.deployProjectToCluster".equals(params.getCommand())) {
          return CompletableFuture.supplyAsync(() -> handleDeployProjectToCluster(params.getArguments()));
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

  private Object handleSetFileMode(List<Object> args) {
    try {
      if (args == null || args.size() < 2) {
        return Map.of("error", "Expected arguments: [documentUri, mode]");
      }
      String uri  = ((JsonElement) args.get(0)).getAsString();
      String mode = ((JsonElement) args.get(1)).getAsString();
      docs.setFileMode(uri, mode);
      return null;
    } catch (Exception e) {
      System.err.println("[kafkasql-lsp] setFileMode error: " + e.getMessage());
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

      Properties props = conn.baseProperties();
      props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   ByteArrayDeserializer.class.getName());
      props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
      props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

      SymbolTable symbols = new SymbolTable();
      try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(props)) {
        kafkasql.runtime.stream.StreamReader<sys.schema.SymbolEventLog> reader =
            new kafkasql.persistence.ReplayStream<>(
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

  // ── Diff with Cluster ────────────────────────────────────────────────────────

  private Object handleDiffWithCluster(List<Object> args) {
    try {
      if (args == null || args.size() < 3) {
        return Map.of("error", "Expected arguments: [projectFilePath, connectionName, localFilePath]");
      }
      String projectFilePath = ((JsonElement) args.get(0)).getAsString();
      String connectionName  = ((JsonElement) args.get(1)).getAsString();
      String localFilePath   = ((JsonElement) args.get(2)).getAsString();

      ConnectionConfig conn = resolveConnection(projectFilePath, connectionName);
      if (conn == null) {
        return Map.of("error", "Connection '" + connectionName + "' not found in connections.toml");
      }

      LiveEventState live = readLiveEventState(conn);
      String remoteScript = buildRemoteScript(live.stateMap());

      Path localPath = Path.of(localFilePath).normalize();
      String localContent = Files.readString(localPath);
      Path root = Path.of(workspaceRoot);

      ScriptDiff diff = ScriptDiffer.diff(
          remoteScript, "cluster",
          localContent, localPath.toString(),
          root, true, RuleSet.defaults()
      );
      List<DiffEntry> entries = diff.flatten();
      System.err.println("[kafkasql-lsp] diffWithCluster '" + connectionName + "' returned " + entries.size() + " entries");
      return entries;
    } catch (Exception e) {
      System.err.println("[kafkasql-lsp] diffWithCluster error: " + e.getMessage());
      e.printStackTrace(System.err);
      return Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
    }
  }

  // ── Deploy to Cluster ────────────────────────────────────────────────────────

  private Object handleDeployToCluster(List<Object> args) {
    try {
      if (args == null || args.size() < 3) {
        return Map.of("error", "Expected arguments: [projectFilePath, connectionName, localFilePath]");
      }
      String projectFilePath = ((JsonElement) args.get(0)).getAsString();
      String connectionName  = ((JsonElement) args.get(1)).getAsString();
      String localFilePath   = ((JsonElement) args.get(2)).getAsString();

      ConnectionConfig conn = resolveConnection(projectFilePath, connectionName);
      if (conn == null) {
        return Map.of("error", "Connection '" + connectionName + "' not found in connections.toml");
      }

      // 1. Read remote state from Kafka
      LiveEventState live = readLiveEventState(conn);
      Map<Name, String>  stateMap   = live.stateMap();
      Map<Name, Integer> versionMap = new HashMap<>(live.versionMap()); // mutable copy

      // 2. Build local symbol table by parsing the local file
      Path localPath = Path.of(localFilePath).normalize();
      String localContent = Files.readString(localPath);
      Path root = Path.of(workspaceRoot);
      Map<Name, Decl> localDecls = buildLocalDecls(localContent, localPath.toString(), root);

      // 3. Produce Kafka events for the differences
      Properties producerProps = conn.baseProperties();
      producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   ByteArraySerializer.class.getName());
      producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
      producerProps.put(ProducerConfig.ACKS_CONFIG, "all");

      int eventCount = 0;
      List<String> operations = new ArrayList<>();

      try (KafkaProducer<byte[], byte[]> producer = new KafkaProducer<>(producerProps);
           kafkasql.persistence.TopicProvisioner provisioner = new kafkasql.persistence.TopicProvisioner(conn.baseProperties())) {
        kafkasql.runtime.stream.StreamWriter<sys.schema.SymbolEventLog> streamWriter =
            new kafkasql.io.WriteStream<>(conn.topic(), producer,
                msg -> { var baos = new java.io.ByteArrayOutputStream(); msg.writeTo(baos); return baos.toByteArray(); },
                msg -> ((sys.schema.SymbolEventLog.SymbolEvent) msg).ObjectName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        EventLogWriter logWriter = new EventLogWriter(streamWriter, "kafkasql-lsp");

        // Drop symbols removed from the local file
        for (Name name : stateMap.keySet()) {
          if (!localDecls.containsKey(name)) {
            int nextVersion = versionMap.getOrDefault(name, 1) + 1;
            Decl remoteDecl = parseDdlToDecl(stateMap.get(name));
            SourceWriter sw = new SourceWriter();
            sw.writeDrop(remoteDecl);
            logWriter.writeDrop(name, sw.toString() + ";", nextVersion);
            versionMap.put(name, nextVersion);
            eventCount++;
            operations.add("DROP " + name.fullName());
          }
        }

        // Create new / update changed symbols
        for (Map.Entry<Name, Decl> entry : localDecls.entrySet()) {
          Name name = entry.getKey();
          Decl decl = entry.getValue();
          SourceWriter sw = new SourceWriter();
          sw.writeCreate(decl);
          String createDdl = sw.toString() + ";";

          if (!stateMap.containsKey(name)) {
            // New symbol — provision a Kafka topic for STREAM declarations
            if (decl instanceof kafkasql.lang.syntax.ast.decl.StreamDecl) {
              provisioner.ensureTopic(name.fullName());
            }
            logWriter.writeCreate(name, decl, createDdl);
            eventCount++;
            operations.add("CREATE " + name.fullName());
          } else {
            // Existing symbol — compare DDL text; write ALTER only when changed
            String remoteDdl = stateMap.get(name).trim();
            if (!remoteDdl.equals(createDdl.trim())) {
              int nextVersion = versionMap.getOrDefault(name, 1) + 1;
              versionMap.put(name, nextVersion);
              logWriter.writeAlter(name, decl, createDdl, nextVersion);
              eventCount++;
              operations.add("ALTER " + name.fullName());
            }
          }
        }

        logWriter.flush();
      }

      System.err.println("[kafkasql-lsp] deployToCluster '" + connectionName + "' wrote " + eventCount + " events");
      return Map.of("deployed", eventCount, "operations", operations);
    } catch (Exception e) {
      System.err.println("[kafkasql-lsp] deployToCluster error: " + e.getMessage());
      e.printStackTrace(System.err);
      return Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
    }
  }

  // ── Private helpers ──────────────────────────────────────────────────────────
  // ── Diff Project with Cluster ──────────────────────────────────────

  private Object handleDiffProjectWithCluster(List<Object> args) {
    try {
      if (args == null || args.size() < 2) {
        return Map.of("error", "Expected arguments: [projectFilePath, connectionName]");
      }
      String projectFilePath = ((JsonElement) args.get(0)).getAsString();
      String connectionName  = ((JsonElement) args.get(1)).getAsString();

      ConnectionConfig conn = resolveConnection(projectFilePath, connectionName);
      if (conn == null) {
        return Map.of("error", "Connection '" + connectionName + "' not found in connections.toml");
      }

      Map<Name, Decl> localDecls = buildProjectDecls(projectFilePath);
      LiveEventState  live       = readLiveEventState(conn);
      String remoteScript = buildRemoteScript(live.stateMap());
      String localScript  = buildScriptFromDecls(localDecls);
      Path   root         = Path.of(workspaceRoot);

      ScriptDiff diff = ScriptDiffer.diff(
          remoteScript, "cluster",
          localScript,  "project",
          root, false, RuleSet.defaults()
      );
      List<DiffEntry> entries = diff.flatten();
      System.err.println("[kafkasql-lsp] diffProjectWithCluster '" + connectionName + "' returned " + entries.size() + " entries");
      return entries;
    } catch (Exception e) {
      System.err.println("[kafkasql-lsp] diffProjectWithCluster error: " + e.getMessage());
      e.printStackTrace(System.err);
      return Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
    }
  }

  // ── Execute Statement ────────────────────────────────────────────────────────

  private Object handleExecuteStatement(List<Object> args) {
    executionCancelled.set(false);
    try {
      if (args == null || args.size() < 3) {
        return Map.of("error", "Expected arguments: [activeFilePath, connectionName, statementText]");
      }
      String activeFilePath  = ((JsonElement) args.get(0)).getAsString();
      String connectionName  = ((JsonElement) args.get(1)).getAsString();
      String statementText   = ((JsonElement) args.get(2)).getAsString();
      // Optional 4th arg: "interactive" disables INCLUDE resolution so that misc
      // scripts outside the model root can run without local source files.
      boolean interactive = args.size() >= 4
          && "interactive".equalsIgnoreCase(((JsonElement) args.get(3)).getAsString());

      ConnectionConfig conn = resolveConnection(activeFilePath, connectionName);
      if (conn == null) {
        return Map.of("error", "Connection '" + connectionName + "' not found in connections.toml");
      }

      // Read current state from Kafka
      LiveEventState live = readLiveEventState(conn);
      Map<Name, String>  stateMap   = live.stateMap();
      Map<Name, Integer> versionMap = new HashMap<>(live.versionMap());

      // Parse the statement text; interactive mode skips INCLUDE resolution
      Path root = Path.of(activeFilePath).normalize().getParent();
      Map<Name, Decl> localDecls = buildLocalDecls(statementText, "<editor>", root, !interactive);
      StatementKinds statementKinds = parseStatementKinds(statementText);

      Properties producerProps = conn.baseProperties();
      producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   ByteArraySerializer.class.getName());
      producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
      producerProps.put(ProducerConfig.ACKS_CONFIG, "all");

      // Pre-flight: reject the entire batch if any object already exists.
      // CREATE is strict — use ALTER explicitly to change an existing object.
      List<String> conflicts = new ArrayList<>();
      for (Map.Entry<Name, Decl> entry : localDecls.entrySet()) {
        Name name = entry.getKey();
        if (stateMap.containsKey(name)) {
          String kind = entry.getValue() instanceof StreamDecl ? "STREAM" : "TYPE";
          conflicts.add(kind + " '" + name.fullName() + "' already exists");
        }
      }
      if (!conflicts.isEmpty()) {
        return Map.of("error", String.join("\n", conflicts));
      }

      int eventCount = 0;
      List<String> operations = new ArrayList<>();

      try (KafkaProducer<byte[], byte[]> producer = new KafkaProducer<>(producerProps);
           kafkasql.persistence.TopicProvisioner provisioner = new kafkasql.persistence.TopicProvisioner(conn.baseProperties())) {
        kafkasql.runtime.stream.StreamWriter<sys.schema.SymbolEventLog> streamWriter =
            new kafkasql.io.WriteStream<>(conn.topic(), producer,
                msg -> { var baos = new java.io.ByteArrayOutputStream(); msg.writeTo(baos); return baos.toByteArray(); },
                msg -> ((sys.schema.SymbolEventLog.SymbolEvent) msg).ObjectName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        EventLogWriter logWriter = new EventLogWriter(streamWriter, "kafkasql-lsp");

        for (Map.Entry<Name, Decl> entry : localDecls.entrySet()) {
          Name name = entry.getKey();
          Decl decl = entry.getValue();
          SourceWriter sw = new SourceWriter();
          sw.writeCreate(decl);
          String createDdl = sw.toString() + ";";

          if (decl instanceof StreamDecl) {
            provisioner.ensureTopic(name.fullName());
          }
          logWriter.writeCreate(name, decl, createDdl);
          eventCount++;
          operations.add("CREATE " + name.fullName());
        }
        logWriter.flush();

        // Handle WRITE TO and/or READ FROM statements via the execution engine
        if (statementKinds.hasWrite() || statementKinds.hasRead() || statementKinds.hasUserOrAcl()) {
          String remoteScript = buildRemoteScript(stateMap);
          int[] writeCount = {0};
          Map<String, Integer> streamWriteCounts = new LinkedHashMap<>();
          List<Map<String, Object>> queryRecords = new ArrayList<>();

          // Helper: convert a StreamRecord to a notification row and send it live.
          Consumer<StreamRecord> onRecord = sr -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("typeName", sr.typeName());
            Map<String, Object> jsonFields = new LinkedHashMap<>();
            for (var fe : sr.value().fields().entrySet()) {
              jsonFields.put(fe.getKey(), toJsonValue(fe.getValue()));
            }
            row.put("fields", jsonFields);
            queryRecords.add(row);
            notifyRecord(row);
          };

        // Extract consumer clauses keyed by stream name for use in readRecords
          Map<String, ReadMode> consumerByStream = new LinkedHashMap<>();
          Map<String, StopAfter> stopAfterByStream = new LinkedHashMap<>();
          if (statementKinds.parseResult() != null) {
            for (Script sc2 : statementKinds.parseResult().scripts()) {
              for (Stmt st2 : sc2.statements()) {
                if (st2 instanceof ReadStmt rs) {
                  if (rs.mode().isPresent()) {
                    consumerByStream.put(rs.stream().fullName(), rs.mode().get());
                  }
                  if (rs.stopAfter().isPresent()) {
                    stopAfterByStream.put(rs.stream().fullName(), rs.stopAfter().get());
                  }
                }
              }
            }
          }

          KafkaSqlEngine engine = new KafkaSqlEngine() {
            @Override
            protected void writeRecord(Name streamName, String typeName, StructValue value) {
              try {
                String topic = streamName.fullName();
                byte[] valueBytes = ValueCodec.toByteArray(value);
                byte[] keyBytes = typeName.getBytes(StandardCharsets.UTF_8);
                var rec = new ProducerRecord<byte[], byte[]>(topic, keyBytes, valueBytes);
                rec.headers().add(new RecordHeader("typeName", keyBytes));
                producer.send(rec).get();
                writeCount[0]++;
                streamWriteCounts.merge(topic, 1, Integer::sum);
              } catch (Exception ex) {
                throw new RuntimeException("Failed to write record to topic: " + streamName.fullName(), ex);
              }
            }

            @Override
            protected List<StreamRecord> readRecords(Name streamName) {
              String topic = streamName.fullName();
              Map<String, StructType> typeMap = buildStreamTypeMap(getLastModel(), streamName);
              ReadMode mode = consumerByStream.get(topic);
              StopAfter stopAfter = stopAfterByStream.get(topic);

              Properties baseProps = conn.baseProperties();
              baseProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
              baseProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
              baseProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

              List<StreamRecord> records = new ArrayList<>();
              try {
                if (mode instanceof ReadMode.FromGroup jg) {
                  // subscribe() path — group-managed offsets.
                  // If committed offsets exist for the group+partition they are always used.
                  // BEGINNING / END only take effect when NO committed offsets exist yet,
                  // via auto.offset.reset — which is exactly what Kafka's config is for.
                  //   FROM GROUP 'x'           → auto.offset.reset=earliest (new group reads all)
                  //   FROM GROUP 'x' BEGINNING → auto.offset.reset=earliest
                  //   FROM GROUP 'x' END       → auto.offset.reset=latest
                  String offsetReset = jg.resetToBeginning().map(b -> b ? "earliest" : "latest")
                      .orElse("latest");

                  Properties groupProps = new Properties(baseProps);
                  groupProps.putAll(baseProps);
                  groupProps.put(ConsumerConfig.GROUP_ID_CONFIG, jg.groupId());
                  groupProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offsetReset);

                  try (KafkaConsumer<String, byte[]> gc = new KafkaConsumer<>(groupProps)) {
                    gc.subscribe(List.of(topic));
                    // Trigger partition assignment via a short poll.
                    gc.poll(java.time.Duration.ofMillis(500));
                    records.addAll(drainSubscribed(gc, typeMap, stopAfter, executionCancelled, onRecord));
                    // Commit the positions reached so the next run resumes from here.
                    gc.commitSync();
                  }
                } else {
                  // assign() path — all FROM variants (FROM BEGINNING, FROM END, etc.)
                  try (KafkaConsumer<String, byte[]> kafkaConsumer = new KafkaConsumer<>(baseProps)) {
                    List<TopicPartition> allPartitions = kafkaConsumer.partitionsFor(topic).stream()
                        .map(pi -> new TopicPartition(pi.topic(), pi.partition()))
                        .collect(java.util.stream.Collectors.toList());
                    if (mode instanceof ReadMode.FromOffsets fo) {
                      List<TopicPartition> selected = fo.specs().stream()
                          .map(s -> new TopicPartition(topic, s.partition()))
                          .collect(java.util.stream.Collectors.toList());
                      kafkaConsumer.assign(selected);
                      seekOffsets(kafkaConsumer, fo.specs());
                    } else if (mode instanceof ReadMode.FromTimestamps fts) {
                      List<TopicPartition> selected = fts.specs().stream()
                          .map(s -> new TopicPartition(topic, s.partition()))
                          .collect(java.util.stream.Collectors.toList());
                      kafkaConsumer.assign(selected);
                      seekTimestamps(kafkaConsumer, fts.specs());
                    } else {
                      kafkaConsumer.assign(allPartitions);
                      if (mode instanceof ReadMode.FromTimestamp ft) {
                        long ts = java.time.Instant.parse(ft.timestamp()).toEpochMilli();
                        Map<TopicPartition, Long> tsMap = new java.util.HashMap<>();
                        for (TopicPartition tp : allPartitions) tsMap.put(tp, ts);
                        Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndTimestamp> offsets =
                            kafkaConsumer.offsetsForTimes(tsMap);
                        for (TopicPartition tp : allPartitions) {
                          var oat = offsets.get(tp);
                          if (oat != null) kafkaConsumer.seek(tp, oat.offset());
                          else kafkaConsumer.seekToEnd(List.of(tp));
                        }
                      } else if (mode instanceof ReadMode.FromEnd) {
                        kafkaConsumer.seekToEnd(allPartitions);
                      } else {
                        // FromBeginning or null → default to beginning
                        kafkaConsumer.seekToBeginning(allPartitions);
                      }
                    }
                    List<TopicPartition> assignedPartitions = new ArrayList<>(kafkaConsumer.assignment());
                    Map<TopicPartition, Long> endOffsets = kafkaConsumer.endOffsets(assignedPartitions);
                    records.addAll(drainAssigned(kafkaConsumer, assignedPartitions, endOffsets, typeMap, stopAfter, executionCancelled, onRecord));
                  }
                }
              } catch (Exception ex) {
                throw new RuntimeException("Failed to read from topic: " + topic, ex);
              }
              return records;
            }

            @Override
            protected void handleQueryResult(List<StreamRecord> records) {
              // Records were already sent as kafkasql/record notifications via onRecord.
              // queryRecords is populated there too; nothing to do here.
            }

            @Override
            protected void executeUser(kafkasql.lang.syntax.ast.stmt.UserStmt stmt) {
              Properties adminProps = conn.baseProperties();
              try (org.apache.kafka.clients.admin.AdminClient admin = org.apache.kafka.clients.admin.AdminClient.create(adminProps)) {
                switch (stmt) {
                  case kafkasql.lang.syntax.ast.stmt.UserStmt.CreateUser cu -> {
                    String password = cu.password().orElseThrow(
                        () -> new IllegalArgumentException("CREATE USER requires PASSWORD option"));
                    var info = new org.apache.kafka.clients.admin.ScramCredentialInfo(
                        org.apache.kafka.clients.admin.ScramMechanism.SCRAM_SHA_256, 4096);
                    var upsert = new org.apache.kafka.clients.admin.UserScramCredentialUpsertion(
                        cu.username(), info, password);
                    admin.alterUserScramCredentials(List.of(upsert)).all().get();
                    operations.add("CREATE USER '" + cu.username() + "'");
                  }
                  case kafkasql.lang.syntax.ast.stmt.UserStmt.AlterUser au -> {
                    // Fetch existing mechanisms so we update all mechanisms already assigned to this user.
                    List<org.apache.kafka.clients.admin.ScramMechanism> targetMechs = new ArrayList<>();
                    try {
                      var desc = admin.describeUserScramCredentials(List.of(au.username())).all().get();
                      var userDesc = desc.get(au.username());
                      if (userDesc != null) {
                        userDesc.credentialInfos().forEach(ci -> targetMechs.add(ci.mechanism()));
                      }
                    } catch (Exception ignored) {}
                    if (targetMechs.isEmpty()) {
                      targetMechs.add(org.apache.kafka.clients.admin.ScramMechanism.SCRAM_SHA_256);
                    }
                    List<org.apache.kafka.clients.admin.UserScramCredentialAlteration> alterations = new ArrayList<>();
                    for (var mech : targetMechs) {
                      var info = new org.apache.kafka.clients.admin.ScramCredentialInfo(mech, 4096);
                      alterations.add(new org.apache.kafka.clients.admin.UserScramCredentialUpsertion(
                          au.username(), info, au.password()));
                    }
                    admin.alterUserScramCredentials(alterations).all().get();
                    operations.add("ALTER USER '" + au.username() + "'");
                  }
                  case kafkasql.lang.syntax.ast.stmt.UserStmt.DropUser du -> {
                    List<org.apache.kafka.clients.admin.UserScramCredentialAlteration> deletions = new ArrayList<>();
                    try {
                      var desc = admin.describeUserScramCredentials(List.of(du.username())).all().get();
                      var userDesc = desc.get(du.username());
                      if (userDesc != null) {
                        for (var cred : userDesc.credentialInfos()) {
                          deletions.add(new org.apache.kafka.clients.admin.UserScramCredentialDeletion(
                              du.username(), cred.mechanism()));
                        }
                      }
                    } catch (Exception ignored) {}
                    if (!deletions.isEmpty()) {
                      admin.alterUserScramCredentials(deletions).all().get();
                    }
                    operations.add("DROP USER '" + du.username() + "'");
                  }
                }
              } catch (Exception ex) {
                throw new RuntimeException("User management failed: " + ex.getMessage(), ex);
              }
            }

            @Override
            protected List<String> listUsers(java.util.Optional<String> filter) {
              Properties adminProps = conn.baseProperties();
              try (org.apache.kafka.clients.admin.AdminClient admin = org.apache.kafka.clients.admin.AdminClient.create(adminProps)) {
                // Fetch all SCRAM users from Kafka
                var desc = admin.describeUserScramCredentials(List.of()).all().get();
                if (desc.isEmpty()) {
                  return List.of("(no SCRAM users)");
                }
                // Build glob predicate for the optional filter pattern
                java.util.function.Predicate<String> namePredicate;
                if (filter.isPresent()) {
                  String pattern = filter.get();
                  // Convert glob * to regex .* (case-insensitive)
                  String regex = java.util.regex.Pattern.quote(pattern).replace("\\*", "\\E.*\\Q");
                  java.util.regex.Pattern compiled = java.util.regex.Pattern.compile(
                      regex, java.util.regex.Pattern.CASE_INSENSITIVE);
                  namePredicate = name -> compiled.matcher(name).matches();
                } else {
                  namePredicate = name -> true;
                }
                // If the filter has no wildcard and matches exactly one user, show full detail
                boolean isExact = filter.isPresent() && !filter.get().contains("*");
                if (isExact) {
                  String name = filter.get();
                  var userDesc = desc.get(name);
                  if (userDesc == null || userDesc.credentialInfos().isEmpty()) {
                    return List.of("USER " + name + ": no SCRAM credentials");
                  }
                  List<String> lines = new java.util.ArrayList<>();
                  lines.add("USER " + name);
                  for (var cred : userDesc.credentialInfos()) {
                    lines.add("  mechanism  : " + cred.mechanism().mechanismName());
                    lines.add("  iterations : " + cred.iterations());
                  }
                  return lines;
                }
                // Otherwise return a summary list, applying the glob filter
                return desc.entrySet().stream()
                  .filter(e -> namePredicate.test(e.getKey()))
                  .sorted(java.util.Map.Entry.comparingByKey())
                  .map(e -> {
                    var mechs = e.getValue().credentialInfos().stream()
                        .map(ci -> ci.mechanism().mechanismName())
                        .collect(java.util.stream.Collectors.joining(", "));
                    return e.getKey() + " [" + mechs + "]";
                  })
                  .collect(java.util.stream.Collectors.toList());
              } catch (Exception ex) {
                throw new RuntimeException("SHOW USERS failed: " + ex.getMessage(), ex);
              }
            }

            @Override
            protected void executeGrant(kafkasql.lang.syntax.ast.stmt.AclStmt stmt) {
              Properties adminProps = conn.baseProperties();
              try (org.apache.kafka.clients.admin.AdminClient admin = org.apache.kafka.clients.admin.AdminClient.create(adminProps)) {
                boolean isGrant = stmt instanceof kafkasql.lang.syntax.ast.stmt.AclStmt.Grant;
                kafkasql.lang.syntax.ast.stmt.AclStmt.Privilege privilege = switch (stmt) {
                  case kafkasql.lang.syntax.ast.stmt.AclStmt.Grant  g -> g.privilege();
                  case kafkasql.lang.syntax.ast.stmt.AclStmt.Revoke r -> r.privilege();
                };
                kafkasql.lang.syntax.ast.stmt.AclStmt.Target target = switch (stmt) {
                  case kafkasql.lang.syntax.ast.stmt.AclStmt.Grant  g -> g.target();
                  case kafkasql.lang.syntax.ast.stmt.AclStmt.Revoke r -> r.target();
                };
                kafkasql.lang.syntax.ast.misc.QName resource = switch (stmt) {
                  case kafkasql.lang.syntax.ast.stmt.AclStmt.Grant  g -> g.resource();
                  case kafkasql.lang.syntax.ast.stmt.AclStmt.Revoke r -> r.resource();
                };
                String principal = switch (stmt) {
                  case kafkasql.lang.syntax.ast.stmt.AclStmt.Grant  g -> g.principal();
                  case kafkasql.lang.syntax.ast.stmt.AclStmt.Revoke r -> r.principal();
                };

                // Derive topic name from QName (dot-separated)
                String topicName = resource.fullName();

                // STREAM → LITERAL pattern; CONTEXT → PREFIXED pattern
                org.apache.kafka.common.resource.PatternType patternType =
                    target == kafkasql.lang.syntax.ast.stmt.AclStmt.Target.CONTEXT
                        ? org.apache.kafka.common.resource.PatternType.PREFIXED
                        : org.apache.kafka.common.resource.PatternType.LITERAL;

                // Map privilege to Kafka ACL operations
                List<org.apache.kafka.common.acl.AclOperation> ops = switch (privilege) {
                  case READ   -> List.of(org.apache.kafka.common.acl.AclOperation.READ);
                  case WRITE  -> List.of(org.apache.kafka.common.acl.AclOperation.WRITE);
                  case CREATE -> List.of(org.apache.kafka.common.acl.AclOperation.CREATE);
                  case MODIFY -> List.of(org.apache.kafka.common.acl.AclOperation.ALTER);
                  case ALL    -> List.of(
                      org.apache.kafka.common.acl.AclOperation.READ,
                      org.apache.kafka.common.acl.AclOperation.WRITE);
                };

                var resourcePattern = new org.apache.kafka.common.resource.ResourcePattern(
                    org.apache.kafka.common.resource.ResourceType.TOPIC, topicName, patternType);
                String kafkaPrincipal = principal.contains(":") ? principal : "User:" + principal;

                if (isGrant) {
                  List<org.apache.kafka.common.acl.AclBinding> bindings = new ArrayList<>();
                  for (var op : ops) {
                    var entry = new org.apache.kafka.common.acl.AccessControlEntry(
                        kafkaPrincipal, "*",
                        op, org.apache.kafka.common.acl.AclPermissionType.ALLOW);
                    bindings.add(new org.apache.kafka.common.acl.AclBinding(resourcePattern, entry));
                  }
                  admin.createAcls(bindings).all().get();
                  operations.add("GRANT " + privilege + " ON " + target + " " + topicName + " TO " + principal);
                } else {
                  List<org.apache.kafka.common.acl.AclBindingFilter> filters = new ArrayList<>();
                  for (var op : ops) {
                    var entryFilter = new org.apache.kafka.common.acl.AccessControlEntryFilter(
                        kafkaPrincipal, "*",
                        op, org.apache.kafka.common.acl.AclPermissionType.ALLOW);
                    filters.add(new org.apache.kafka.common.acl.AclBindingFilter(
                        resourcePattern.toFilter(), entryFilter));
                  }
                  admin.deleteAcls(filters).all().get();
                  operations.add("REVOKE " + privilege + " ON " + target + " " + topicName + " FROM " + principal);
                }
              } catch (Exception ex) {
                throw new RuntimeException("ACL management failed: " + ex.getMessage(), ex);
              }
            }

            @Override
            protected Map<Integer, Long> writeSchemaMarker(Name streamName, String typeName) {
              return Map.of();
            }
          };

          engine.executeAll(remoteScript, statementText);
          eventCount += writeCount[0];
          for (var we : streamWriteCounts.entrySet()) {
            operations.add("WRITE " + we.getValue() + " record(s) to " + we.getKey());
          }
          if (statementKinds.hasRead()) {
            return Map.of("executed", eventCount, "operations", operations, "records", queryRecords);
          }
        }
      }

      System.err.println("[kafkasql-lsp] executeStatement '" + connectionName + "' wrote " + eventCount + " event(s)");
      return Map.of("executed", eventCount, "operations", operations);
    } catch (Exception e) {
      System.err.println("[kafkasql-lsp] executeStatement error: " + e.getMessage());
      e.printStackTrace(System.err);
      return Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
    }
  }

  // ── Deploy Project to Cluster ────────────────────────────────────

  private Object handleDeployProjectToCluster(List<Object> args) {
    try {
      if (args == null || args.size() < 2) {
        return Map.of("error", "Expected arguments: [projectFilePath, connectionName]");
      }
      String projectFilePath = ((JsonElement) args.get(0)).getAsString();
      String connectionName  = ((JsonElement) args.get(1)).getAsString();

      ConnectionConfig conn = resolveConnection(projectFilePath, connectionName);
      if (conn == null) {
        return Map.of("error", "Connection '" + connectionName + "' not found in connections.toml");
      }

      Map<Name, Decl> localDecls = buildProjectDecls(projectFilePath);
      LiveEventState  live       = readLiveEventState(conn);
      Map<Name, String>  stateMap   = live.stateMap();
      Map<Name, Integer> versionMap = new HashMap<>(live.versionMap());

      Properties producerProps = conn.baseProperties();
      producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   ByteArraySerializer.class.getName());
      producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
      producerProps.put(ProducerConfig.ACKS_CONFIG, "all");

      int eventCount = 0;
      List<String> operations = new ArrayList<>();

      try (KafkaProducer<byte[], byte[]> producer = new KafkaProducer<>(producerProps);
           kafkasql.persistence.TopicProvisioner provisioner = new kafkasql.persistence.TopicProvisioner(conn.baseProperties())) {
        kafkasql.runtime.stream.StreamWriter<sys.schema.SymbolEventLog> streamWriter =
            new kafkasql.io.WriteStream<>(conn.topic(), producer,
                msg -> { var baos = new java.io.ByteArrayOutputStream(); msg.writeTo(baos); return baos.toByteArray(); },
                msg -> ((sys.schema.SymbolEventLog.SymbolEvent) msg).ObjectName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        EventLogWriter logWriter = new EventLogWriter(streamWriter, "kafkasql-lsp");

        for (Name name : stateMap.keySet()) {
          if (!localDecls.containsKey(name)) {
            int nextVersion = versionMap.getOrDefault(name, 1) + 1;
            Decl remoteDecl = parseDdlToDecl(stateMap.get(name));
            SourceWriter sw = new SourceWriter();
            sw.writeDrop(remoteDecl);
            logWriter.writeDrop(name, sw.toString() + ";", nextVersion);
            versionMap.put(name, nextVersion);
            eventCount++;
            operations.add("DROP " + name.fullName());
          }
        }

        for (Map.Entry<Name, Decl> entry : localDecls.entrySet()) {
          Name name = entry.getKey();
          Decl decl = entry.getValue();
          SourceWriter sw = new SourceWriter();
          sw.writeCreate(decl);
          String createDdl = sw.toString() + ";";

          if (!stateMap.containsKey(name)) {
            // New symbol — provision a Kafka topic for STREAM declarations
            if (decl instanceof kafkasql.lang.syntax.ast.decl.StreamDecl) {
              provisioner.ensureTopic(name.fullName());
            }
            logWriter.writeCreate(name, decl, createDdl);
            eventCount++;
            operations.add("CREATE " + name.fullName());
          } else {
            String remoteDdl = stateMap.get(name).trim();
            if (!remoteDdl.equals(createDdl.trim())) {
              int nextVersion = versionMap.getOrDefault(name, 1) + 1;
              versionMap.put(name, nextVersion);
              logWriter.writeAlter(name, decl, createDdl, nextVersion);
              eventCount++;
              operations.add("ALTER " + name.fullName());
            }
          }
        }

        logWriter.flush();
      }

      System.err.println("[kafkasql-lsp] deployProjectToCluster '" + connectionName + "' wrote " + eventCount + " events");
      return Map.of("deployed", eventCount, "operations", operations);
    } catch (Exception e) {
      System.err.println("[kafkasql-lsp] deployProjectToCluster error: " + e.getMessage());
      e.printStackTrace(System.err);
      return Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
    }
  }

  /**
   * Walks all {@code .kafka} and {@code .kafkasql} files in the project's kafka root
   * directory and returns the combined map of fully-qualified {@link Name} → {@link Decl}.
   * Includes files are already followed transitively by the parser (includeEnabled = true).
   */
  private Map<Name, Decl> buildProjectDecls(String projectFilePath) throws Exception {
    Path projFile = Path.of(projectFilePath).normalize();
    KafkaSqlProject project = KafkaSqlProject.findFor(projFile)
        .orElseThrow(() -> new IllegalArgumentException(
            "No .proj.toml found at or above: " + projFile));
    Path kafkaRoot = project.kafkaRoot();
    if (!Files.isDirectory(kafkaRoot)) {
      throw new IllegalArgumentException("Kafka root directory not found: " + kafkaRoot);
    }

    List<StringInput> inputs = new ArrayList<>();
    try (var stream = Files.walk(kafkaRoot)) {
      stream
          .filter(p -> !Files.isDirectory(p))
          .filter(p -> {
            String n = p.getFileName().toString();
            return n.endsWith(".kafka") || n.endsWith(".kafkasql");
          })
          .sorted()
          .forEach(p -> {
            try {
              inputs.add(new StringInput(p.toString(), Files.readString(p)));
            } catch (Exception e) {
              System.err.println("[kafkasql-lsp] buildProjectDecls: failed to read " + p + ": " + e.getMessage());
            }
          });
    }

    if (inputs.isEmpty()) return new LinkedHashMap<>();

    Path root = Path.of(workspaceRoot);
    KafkaSqlArgs parseArgs = new KafkaSqlArgs(root, true, false);
    List<kafkasql.lang.input.Input> inputList = inputs.stream()
        .map(i -> (kafkasql.lang.input.Input) i)
        .collect(java.util.stream.Collectors.toList());
    ParseResult result = KafkaSqlParser.parse(inputList, parseArgs);
    if (result.diags().hasError()) {
      String errors = result.diags().all().stream()
          .map(Object::toString)
          .collect(java.util.stream.Collectors.joining("; "));
      throw new IllegalArgumentException("Parse errors in project: " + errors);
    }

    Map<Name, Decl> decls = new LinkedHashMap<>();
    String currentCtx = "";
    for (Script script : result.scripts()) {
      // Reset context per file so each file's USE CONTEXT is authoritative
      currentCtx = "";
      for (Stmt stmt : script.statements()) {
        switch (stmt) {
          case UseStmt use -> {
            if (use.target() instanceof ContextUse cu) {
              currentCtx = cu.qname().fullName();
            }
          }
          case CreateStmt create -> {
            Name name = Name.of(currentCtx, create.decl().name().name());
            decls.put(name, create.decl());
          }
          default -> {}
        }
      }
    }
    return decls;
  }

  /**
   * Reconstructs a single consolidated DDL script string from a map of declarations.
   * Used to feed the {@link ScriptDiffer} when the local input is the whole project
   * rather than a single file.
   */
  private String buildScriptFromDecls(Map<Name, Decl> decls) {
    StringBuilder sb = new StringBuilder();
    String currentCtx = "";
    for (Map.Entry<Name, Decl> entry : decls.entrySet()) {
      String ctx = entry.getKey().context();
      if (!ctx.equals(currentCtx)) {
        if (!ctx.isEmpty()) sb.append("USE CONTEXT ").append(ctx).append(";\n");
        currentCtx = ctx;
      }
      SourceWriter sw = new SourceWriter();
      sw.writeCreate(entry.getValue());
      sb.append(sw).append(";\n");
    }
    return sb.toString();
  }
  private record LiveEventState(Map<Name, String> stateMap, Map<Name, Integer> versionMap) {}

  private ConnectionConfig resolveConnection(String projectFilePath, String connectionName) {
    try {
      Path dir = Path.of(projectFilePath).normalize().getParent();
      while (dir != null) {
        Path toml = dir.resolve(ConnectionsLoader.FILENAME);
        if (Files.exists(toml)) {
          return ConnectionsLoader.load(dir).stream()
              .filter(c -> c.name().equals(connectionName))
              .findFirst()
              .orElse(null);
        }
        Path parent = dir.getParent();
        if (parent == null || parent.equals(dir)) break;
        dir = parent;
      }
      return null;
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Reads all Kafka events from the connection's topic and returns the latest DDL
   * state text and object version for each currently-active symbol.
   */
  private LiveEventState readLiveEventState(ConnectionConfig conn) throws Exception {
    Map<Name, String>  stateMap   = new LinkedHashMap<>();
    Map<Name, Integer> versionMap = new HashMap<>();

    Properties props = conn.baseProperties();
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,     ByteArrayDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,   ByteArrayDeserializer.class.getName());
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,         "false");

    try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(props)) {
      kafkasql.runtime.stream.StreamReader<sys.schema.SymbolEventLog> reader =
          new kafkasql.persistence.ReplayStream<>(
              conn.topic(), consumer,
              bytes -> sys.schema.SymbolEventLog.readFrom(new java.io.ByteArrayInputStream(bytes))
          );
      sys.schema.SymbolEventLog evt;
      while ((evt = reader.read()) != null) {
        if (evt instanceof sys.schema.SymbolEventLog.SymbolEvent e) {
          Name name = Name.of(e.ObjectName());
          switch (e.EventType()) {
            case CREATE_STMT, ALTER_STMT -> {
              if (e.State() != null) {
                stateMap.put(name, e.State());
                versionMap.put(name, e.ObjectVersion());
              }
            }
            case DROP_STMT -> {
              stateMap.remove(name);
              versionMap.remove(name);
            }
          }
        }
      }
    }
    return new LiveEventState(stateMap, versionMap);
  }

  /**
   * Reconstructs a parseable KafkaSQL script from the symbol state map, grouping
   * declarations by context and emitting USE CONTEXT statements as needed.
   */
  private String buildRemoteScript(Map<Name, String> stateMap) {
    // Sort: root context (empty string) first, then alphabetically
    Map<String, List<String>> byContext = new TreeMap<>(
        (a, b) -> a.isEmpty() ? (b.isEmpty() ? 0 : -1) : (b.isEmpty() ? 1 : a.compareTo(b))
    );
    for (Map.Entry<Name, String> e : stateMap.entrySet()) {
      byContext.computeIfAbsent(e.getKey().context(), k -> new ArrayList<>()).add(e.getValue());
    }
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, List<String>> e : byContext.entrySet()) {
      if (!e.getKey().isEmpty()) {
        sb.append("USE CONTEXT ").append(e.getKey()).append(";\n");
      }
      for (String ddl : e.getValue()) {
        String normalised = ddl.trim();
        sb.append(normalised);
        if (!normalised.endsWith(";")) sb.append(";");
        sb.append("\n");
      }
    }
    return sb.toString();
  }

  /**
   * Parses a KafkaSQL source file and returns a Name→Decl map for all CREATE
   * declarations, using USE CONTEXT statements to resolve fully-qualified names.
   * Files in file mode follow INCLUDE statements; interactive mode skips them.
   */
  private Map<Name, Decl> buildLocalDecls(String content, String uri, Path root) {
    return buildLocalDecls(content, uri, root, true);
  }

  private Map<Name, Decl> buildLocalDecls(String content, String uri, Path root,
                                           boolean includeEnabled) {
    StringInput input = new StringInput(uri, content);
    KafkaSqlArgs parseArgs = new KafkaSqlArgs(root, includeEnabled, false);
    ParseResult result = KafkaSqlParser.parse(List.of(input), parseArgs);
    if (result.diags().hasError()) {
      throw new IllegalArgumentException("Parse errors in local file: " +
          result.diags().all().stream().map(Object::toString)
                .collect(java.util.stream.Collectors.joining(", ")));
    }
    Map<Name, Decl> decls = new LinkedHashMap<>();
    String currentCtx = "";
    for (Script script : result.scripts()) {
      for (Stmt stmt : script.statements()) {
        switch (stmt) {
          case UseStmt use -> {
            if (use.target() instanceof ContextUse cu) {
              currentCtx = cu.qname().fullName();
            }
          }
          case CreateStmt create -> {
            Name name = Name.of(currentCtx, create.decl().name().name());
            decls.put(name, create.decl());
          }
          default -> { /* ignore other statement types */ }
        }
      }
    }
    return decls;
  }

  private record StatementKinds(boolean hasWrite, boolean hasRead, boolean hasUserOrAcl,
                                ParseResult parseResult) {}

  /**
   * Parses the script once (without INCLUDE resolution) and reports whether it
   * contains WRITE, READ, and USER/ACL statements.
   */
  private StatementKinds parseStatementKinds(String content) {
    try {
      StringInput input = new StringInput("<editor>", content);
      KafkaSqlArgs args = new KafkaSqlArgs(Path.of(""), false, false);
      ParseResult result = KafkaSqlParser.parse(List.of(input), args);
      boolean hasWrite = false;
      boolean hasRead = false;
      boolean hasUserOrAcl = false;
      for (Script script : result.scripts()) {
        for (Stmt stmt : script.statements()) {
          if (stmt instanceof WriteStmt) hasWrite = true;
          if (stmt instanceof ReadStmt) hasRead = true;
          if (stmt instanceof kafkasql.lang.syntax.ast.stmt.UserStmt
              || stmt instanceof kafkasql.lang.syntax.ast.stmt.AclStmt) {
            hasUserOrAcl = true;
          }
        }
      }
      return new StatementKinds(hasWrite, hasRead, hasUserOrAcl, result);
    } catch (Exception e) {
      return new StatementKinds(false, false, false, null);
    }
  }

  /**
   * Returns a typeName → StructType map for all members of the given stream,
   * by looking up the inline StructDecl bindings from the last bound semantic model.
   */
  private static Map<String, StructType> buildStreamTypeMap(SemanticModel model, Name streamName) {
    if (model == null) return Map.of();
    Optional<StreamDecl> streamOpt = model.symbols().lookupStream(streamName);
    if (streamOpt.isEmpty()) return Map.of();
    Map<String, StructType> result = new LinkedHashMap<>();
    for (StreamMemberDecl member : streamOpt.get().streamTypes()) {
      TypeDecl td = member.memberDecl();
      String memberName = td.name().name();
      if (td.kind() instanceof StructDecl sd) {
        StructType type = model.bindings().getOrNull(sd, StructType.class);
        if (type != null) result.put(memberName, type);
      }
    }
    return result;
  }

  /**
   * Decodes a raw Kafka consumer record into a StreamRecord.
   * Uses ValueCodec when the StructType is available, otherwise falls back to
   * the custom binary format.
   */
  private static StreamRecord lspDecodeRecord(
      ConsumerRecord<String, byte[]> rec,
      Map<String, StructType> typeMap
  ) {
    try {
      String typeName = rec.key() != null ? rec.key() : "";
      StructType type = typeMap.get(typeName);
      if (type != null && rec.value() != null) {
        StructValue sv = (StructValue) ValueCodec.fromByteArray(type, rec.value());
        return new StreamRecord(typeName, sv);
      }
      return lspDeserializeRecord(rec);
    } catch (Exception e) {
      return lspDeserializeRecord(rec);
    }
  }

  private static List<KafkaSqlEngine.StreamRecord> drainAssigned(
      KafkaConsumer<String, byte[]> consumer,
      List<TopicPartition> partitions,
      Map<TopicPartition, Long> endOffsets,
      Map<String, StructType> typeMap,
      StopAfter stopAfter,
      AtomicBoolean cancelled,
      Consumer<KafkaSqlEngine.StreamRecord> onRecord) {
    List<KafkaSqlEngine.StreamRecord> records = new ArrayList<>();
    long deadline = stopAfter instanceof StopAfter.Seconds s
        ? System.currentTimeMillis() + s.seconds() * 1000L : Long.MAX_VALUE;
    boolean done = false;
    while (!done && !cancelled.get()) {
      ConsumerRecords<String, byte[]> batch = consumer.poll(Duration.ofMillis(500));
      for (ConsumerRecord<String, byte[]> cr : batch) {
        if (SchemaMarker.isMarker(cr)) continue;
        KafkaSqlEngine.StreamRecord sr = lspDecodeRecord(cr, typeMap);
        if (sr != null) {
          records.add(sr);
          onRecord.accept(sr);
        }
        if (stopAfter instanceof StopAfter.Records r && records.size() >= r.count()) { done = true; break; }
      }
      if (done) break;
      if (System.currentTimeMillis() >= deadline) break;
      // Only terminate at end-of-topic when there is an explicit STOP AFTER condition.
      // Without one, keep polling for new records until cancelled.
      if (stopAfter != null) {
        done = partitions.stream().allMatch(tp ->
            consumer.position(tp) >= endOffsets.getOrDefault(tp, 0L));
      }
    }
    return records;
  }

  private static List<KafkaSqlEngine.StreamRecord> drainSubscribed(
      KafkaConsumer<String, byte[]> consumer,
      Map<String, StructType> typeMap,
      StopAfter stopAfter,
      AtomicBoolean cancelled,
      Consumer<KafkaSqlEngine.StreamRecord> onRecord) {
    List<KafkaSqlEngine.StreamRecord> records = new ArrayList<>();
    long deadline = stopAfter instanceof StopAfter.Seconds s
        ? System.currentTimeMillis() + s.seconds() * 1000L : Long.MAX_VALUE;
    long idleDeadline = stopAfter instanceof StopAfter.SecondsIdle si
        ? System.currentTimeMillis() + si.seconds() * 1000L : Long.MAX_VALUE;
    // No STOP AFTER → loop indefinitely until cancelled.
    // STOP AFTER SECONDS or SECONDS IDLE → keep looping until time condition.
    // STOP AFTER RECORDS → handled per-record below.
    int maxEmptyPolls = (stopAfter == null
        || stopAfter instanceof StopAfter.Seconds
        || stopAfter instanceof StopAfter.SecondsIdle)
        ? Integer.MAX_VALUE : 5;
    int emptyPolls = 0;
    while (emptyPolls < maxEmptyPolls && !cancelled.get()) {
      ConsumerRecords<String, byte[]> batch = consumer.poll(Duration.ofMillis(500));
      if (batch.isEmpty()) {
        emptyPolls++;
      } else {
        emptyPolls = 0;
        if (stopAfter instanceof StopAfter.SecondsIdle si2)
          idleDeadline = System.currentTimeMillis() + si2.seconds() * 1000L;
        for (ConsumerRecord<String, byte[]> cr : batch) {
          if (SchemaMarker.isMarker(cr)) continue;
          KafkaSqlEngine.StreamRecord sr = lspDecodeRecord(cr, typeMap);
          if (sr != null) {
            records.add(sr);
            onRecord.accept(sr);
          }
          if (stopAfter instanceof StopAfter.Records r && records.size() >= r.count()) return records;
        }
      }
      if (System.currentTimeMillis() >= deadline) break;
      if (System.currentTimeMillis() >= idleDeadline) break;
    }
    return records;
  }

  private static void seekOffsets(
      KafkaConsumer<String, byte[]> consumer,
      Iterable<ReadMode.OffsetSpec> specs) {
    List<TopicPartition> beginList = new ArrayList<>();
    List<TopicPartition> endList = new ArrayList<>();
    Map<TopicPartition, Long> offsetMap = new java.util.HashMap<>();
    for (ReadMode.OffsetSpec spec : specs) {
      TopicPartition tp = new TopicPartition(
          consumer.assignment().isEmpty() ? "" : consumer.assignment().iterator().next().topic(),
          spec.partition());
      // resolve the topic name from the existing assignment
      tp = consumer.assignment().stream()
          .filter(a -> a.partition() == spec.partition())
          .findFirst().orElse(tp);
      switch (spec.position()) {
        case ReadMode.OffsetPosition.Beginning ignored -> beginList.add(tp);
        case ReadMode.OffsetPosition.End ignored       -> endList.add(tp);
        case ReadMode.OffsetPosition.Offset o          -> offsetMap.put(tp, o.offset());
      }
    }
    if (!beginList.isEmpty()) consumer.seekToBeginning(beginList);
    if (!endList.isEmpty())   consumer.seekToEnd(endList);
    offsetMap.forEach(consumer::seek);
  }

  private static void seekTimestamps(
      KafkaConsumer<String, byte[]> consumer,
      Iterable<ReadMode.TimestampSpec> specs) {
    Map<TopicPartition, Long> tsMap = new java.util.HashMap<>();
    for (ReadMode.TimestampSpec spec : specs) {
      TopicPartition tp = consumer.assignment().stream()
          .filter(a -> a.partition() == spec.partition())
          .findFirst().orElse(null);
      if (tp != null)
        tsMap.put(tp, java.time.Instant.parse(spec.timestamp()).toEpochMilli());
    }
    if (!tsMap.isEmpty()) {
      consumer.offsetsForTimes(tsMap).forEach((tp, oat) -> {
        if (oat != null) consumer.seek(tp, oat.offset());
        else consumer.seekToEnd(List.of(tp));
      });
    }
  }

  /**
   * Deserializes a raw Kafka consumer record into a StreamRecord using the
   * same type-tagged binary format written by KafkaEngine in integration-tests.
   */
  private static StreamRecord lspDeserializeRecord(ConsumerRecord<String, byte[]> rec) {
    try {
      String typeName = rec.key() != null ? rec.key() : "";
      DataInputStream dis = new DataInputStream(new ByteArrayInputStream(rec.value()));
      int numFields = dis.readInt();
      LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
      for (int i = 0; i < numFields; i++) {
        String fieldName = dis.readUTF();
        Object fieldValue = lspReadFieldValue(dis);
        fields.put(fieldName, fieldValue);
      }
      StructType type = new StructType(Name.of(typeName), new LinkedHashMap<>(), List.of(), Optional.empty());
      return new StreamRecord(typeName, new StructValue(type, fields));
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Reads a single type-tagged field value from a DataInputStream.
   */
  private static Object lspReadFieldValue(DataInputStream dis) throws Exception {
    byte marker = dis.readByte();
    if (marker == 0) return null;
    byte type = dis.readByte();
    return switch (type) {
      case 'I' -> dis.readInt();
      case 'L' -> dis.readLong();
      case 'S' -> dis.readUTF();
      case 'B' -> dis.readBoolean();
      case 'F' -> dis.readFloat();
      case 'D' -> dis.readDouble();
      case 'H' -> dis.readShort();
      case 'Y' -> dis.readByte();
      default  -> throw new IllegalStateException("Unknown type tag: " + (char) type);
    };
  }

  /**
   * Converts a StructValue field value to a JSON-serializable type.
   * Primitives and strings pass through; complex values become strings.
   */
  private static Object toJsonValue(Object v) {
    if (v == null) return null;
    if (v instanceof String || v instanceof Number || v instanceof Boolean) return v;
    return v.toString();
  }

  /**
   * Parses a DDL state text (a single CREATE statement) back to a {@link Decl}.
   * Used to reconstruct the declaration when generating DROP statements.
   */
  private Decl parseDdlToDecl(String ddl) {
    StringInput input = new StringInput("ddl-text", ddl);
    KafkaSqlArgs args = new KafkaSqlArgs(Path.of(""), false, false);
    ParseResult result = KafkaSqlParser.parse(List.of(input), args);
    for (Script s : result.scripts()) {
      for (Stmt stmt : s.statements()) {
        if (stmt instanceof CreateStmt create) {
          return create.decl();
        }
      }
    }
    throw new IllegalStateException("Could not parse DDL state to Decl: " + ddl);
  }
}
