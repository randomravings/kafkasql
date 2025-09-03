package streamsql;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public final class IncludeResolver {

  private static final Pattern INC_PATTERN =
      Pattern.compile("(?i)^\\s*INCLUDE\\s+'([^']+)'");

  private enum State { UNVISITED, IN_PROGRESS, DONE }

  public static final class Result {
    public final List<Path> orderedFiles;
    Result(List<Path> ordered) { this.orderedFiles = ordered; }
  }

  public static Result resolve(Diagnostics diags, Path workingDir, Path... roots) {
    if (workingDir == null) workingDir = Paths.get(".");
    workingDir = workingDir.toAbsolutePath().normalize();

    LinkedHashSet<Path> ordered = new LinkedHashSet<>();
    Map<Path, State> state = new HashMap<>();
    Deque<Path> stack = new ArrayDeque<>();

    for (Path r : roots) {
      if (r == null) continue;
      Path start = resolveRoot(workingDir, r);
      dfs(start, diags, ordered, state, stack, workingDir);
      if (diags.hasErrors()) break;
    }
    if (diags.hasErrors()) return new Result(List.of());
    return new Result(List.copyOf(ordered));
  }

  private static Path resolveRoot(Path workingDir, Path raw) {
    if (raw.isAbsolute()) return normalize(raw);
    Path rel = normalize(workingDir.resolve(raw.toString().replace('\\','/')));
    if (Files.exists(rel)) return rel;
    Path asGiven = normalize(raw);
    if (Files.exists(asGiven)) return asGiven;

    // If raw already starts with workingDir path, strip it
    Path wd = workingDir.toAbsolutePath().normalize();
    Path rawAbs = normalize(wd.resolve(raw.toString()));
    if (rawAbs.startsWith(wd)) {
      Path relToWd = wd.relativize(rawAbs);
      Path stripped = normalize(wd.resolve(relToWd));
      if (Files.exists(stripped)) return stripped;
    }
    return rel;
  }

  private static void dfs(Path file,
                          Diagnostics diags,
                          LinkedHashSet<Path> ordered,
                          Map<Path, State> state,
                          Deque<Path> stack,
                          Path workingDir) {
    State st = state.getOrDefault(file, State.UNVISITED);
    if (st == State.DONE || diags.hasErrors()) return;

    if (st == State.IN_PROGRESS) {
      List<Path> cyc = new ArrayList<>();
      boolean cap = false;
      for (Path p : stack) {
        if (p.equals(file)) cap = true;
        if (cap) cyc.add(p);
      }
      cyc.add(file);
      diags.error("Include cycle detected: " + cycleString(cyc));
      return;
    }

    state.put(file, State.IN_PROGRESS);
    stack.push(file);

    String content;
    try {
      content = Files.readString(file);
    } catch (Exception e) {
      diags.error("Failed to read " + file + ": " + e.getMessage());
      stack.pop();
      state.put(file, State.DONE);
      return;
    }

    for (String line : content.split("\\R")) {
      Matcher m = INC_PATTERN.matcher(line);
      if (m.find()) {
        String incRaw = m.group(1).trim().replace('\\','/');
        Path incPath = normalize(workingDir.resolve(incRaw));
        dfs(incPath, diags, ordered, state, stack, workingDir);
        if (diags.hasErrors()) {
          stack.pop();
          return;
        }
      }
    }

    stack.pop();
    state.put(file, State.DONE);
    ordered.add(file);
  }

  private static String cycleString(List<Path> cyc) {
    // Show just file names for clarity
    return String.join(" -> ",
        cyc.stream().map(p -> p.getFileName().toString()).toList());
  }

  private static Path normalize(Path p) {
    return p.toAbsolutePath().normalize();
  }

  private IncludeResolver() {}
}