package kafkasql.lsp;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.*;

import java.io.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class Main {

  public static void main(String[] args) {
    // keep all runtime logs on stderr to avoid corrupting the LSP stdio channel
    try {
      KafkaSqlLsp server = new KafkaSqlLsp();

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
}