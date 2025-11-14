import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Two-Pass Macroprocessor
 *
 * Pass-I:
 *   - Reads source.txt
 *   - Builds MNT (Macro Name Table), MDT (Macro Definition Table)
 *   - Writes:
 *       mnt.txt   : index, name, mdtStart, paramCount, keyDefaultCount
 *       mdt.txt   : index, lineWithFormalsReplaced (#1, #2, ...)
 *       intermediate.txt : original program without MACRO..MEND blocks
 *
 * Pass-II:
 *   - Reads intermediate.txt, mnt.txt, mdt.txt
 *   - Expands macro invocations with positional/keyword args (+ defaults)
 *   - Handles nested macro calls during expansion
 *   - Writes expanded.txt
 *
 * Macro syntax:
 *   MACRO
 *   NAME &A,&B=1,&C=5
 *     ... body ...
 *   MEND
 *
 * Call examples:
 *   NAME X, Y, 10              ; positional
 *   NAME &B=CREG, &A=AREG      ; keyword (order-free); defaults used if missing
 *   NAME AREG                  ; uses defaults for others
 * 
 */

public class MacroProcessor {

    // ---------- File names ----------
    static final String SRC = "input_macro.txt";
    static final String MNT_FILE = "MNT.txt";
    static final String MDT_FILE = "MDT.txt";
    static final String INTERMEDIATE_FILE = "ALA.txt";
    static final String EXPANDED_FILE = "expanded.txt";

    public static void main(String[] args) {
        try {
            System.out.println("Pass-I: Building MNT/MDT + intermediate...");
            new Pass1().run();
            System.out.println("Pass-I done: mnt.txt, mdt.txt, intermediate.txt");

            System.out.println("Pass-II: Expanding macros...");
            new Pass2().run();
            System.out.println("Pass-II done: expanded.txt");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------- Data structures for Pass-I (and reused by Pass-II) ----------
    static class MNTEntry {
        int index;                 // 1-based
        String name;               // macro name
        int mdtStart;              // 1-based index into MDT
        List<String> formals;      // ["&A","&B","&C"]
        Map<String, Integer> pos;  // "&A"->1, "&B"->2 ...
        Map<String, String> defaults; // "&B"->"1" if &B=1
        int paramCount() { return formals.size(); }
        int defaultCount() { return defaults.size(); }
    }

    // MDT is just a 1-based list of strings. Each string may include #1, #2 placeholders.
    // We'll write it as lines with an index in a text file.

    // ---------- Utilities ----------
    static String sanitize(String s) {
        if (s == null) return "";
        // strip comments starting with ';' or '//'
        int p = s.indexOf(';'); if (p >= 0) s = s.substring(0, p);
        p = s.indexOf("//");   if (p >= 0) s = s.substring(0, p);
        return s.trim();
    }

    static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    static List<String> readLines(String file) throws IOException {
        if (!Files.exists(Path.of(file)))
            throw new FileNotFoundException("Missing input file: " + file);
        return Files.readAllLines(Path.of(file));
    }

    static void writeLines(String file, List<String> lines) throws IOException {
        Files.write(Path.of(file), lines);
    }

    // ============================================================
    //                          PASS - I
    // ============================================================
    static class Pass1 {
        List<String> mdt = new ArrayList<>(); // 1-based (we'll pad a dummy entry at index 0)
        List<MNTEntry> mnt = new ArrayList<>(); // 1-based (dummy at 0)

        void run() throws IOException {
            List<String> src = readLines(SRC);
            List<String> intermediate = new ArrayList<>();

            // 1-based indexing by adding a dummy first element
            mdt.add("<dummy>");
            mnt.add(null);

            int i = 0;
            while (i < src.size()) {
                String line = sanitize(src.get(i++));
                if (isBlank(line)) continue;

                if (line.equalsIgnoreCase("MACRO")) {
                    // Next line is macro header
                    if (i >= src.size())
                        throw new RuntimeException("MACRO without header");
                    String header = sanitize(src.get(i++));
                    if (isBlank(header))
                        throw new RuntimeException("Empty macro header");

                    MNTEntry entry = parseHeader(header);

                    // MDT start
                    entry.mdtStart = mdt.size(); // first body line will go here

                    // Read body until MEND
                    while (i < src.size()) {
                        String b = sanitize(src.get(i++));
                        if (isBlank(b)) continue;
                        if (b.equalsIgnoreCase("MEND")) {
                            mdt.add("MEND");
                            break;
                        }
                        // Replace all occurrences of formals (&A, &B ...) with #position
                        String replaced = replaceFormalsWithPos(b, entry.pos);
                        mdt.add(replaced);
                    }

                    // Store entry
                    entry.index = mnt.size();
                    mnt.add(entry);
                } else {
                    // Not inside a macro definition => goes to intermediate program
                    intermediate.add(line);
                }
            }

            // Write tables
            writeMNT();
            writeMDT();
            writeLines(INTERMEDIATE_FILE, intermediate);
        }

        private MNTEntry parseHeader(String header) {
            // Example: "INCR &REG,&VAL=1"
            // Split first token (name), rest are params (comma-separated)
            String[] parts = header.split("\\s+", 2);
            if (parts.length < 1) throw new RuntimeException("Bad header: " + header);
            String name = parts[0].trim().toUpperCase();
            String paramStr = (parts.length == 2) ? parts[1].trim() : "";

            // Parse parameter list
            List<String> formals = new ArrayList<>();
            Map<String, Integer> pos = new HashMap<>();
            Map<String, String> defaults = new HashMap<>();

            if (!paramStr.isEmpty()) {
                for (String raw : paramStr.split(",")) {
                    String t = raw.trim();
                    if (t.isEmpty()) continue;
                    String formal, def = null;
                    int eq = t.indexOf('=');
                    if (eq >= 0) {
                        formal = t.substring(0, eq).trim();
                        def = t.substring(eq + 1).trim();
                    } else {
                        formal = t.trim();
                    }
                    if (!formal.startsWith("&"))
                        throw new RuntimeException("Formal must start with &: " + formal);

                    formals.add(formal);
                    pos.put(formal, formals.size()); // 1-based
                    if (def != null && !def.isEmpty()) {
                        defaults.put(formal, def);
                    }
                }
            }

            MNTEntry e = new MNTEntry();
            e.name = name;
            e.formals = formals;
            e.pos = pos;
            e.defaults = defaults;
            return e;
        }

        private String replaceFormalsWithPos(String bodyLine, Map<String, Integer> pos) {
            // Replace tokens starting with & that match formals
            // Use regex to find &NAME style tokens (letters/digits/_)
            String out = bodyLine;
            for (Map.Entry<String, Integer> en : pos.entrySet()) {
                String formal = Pattern.quote(en.getKey());
                int p = en.getValue();
                // Replace both "&A" and "&A," etc. We'll do a word-boundary-ish replace:
                out = out.replaceAll("(?i)(?<![A-Za-z0-9_])" + formal + "(?![A-Za-z0-9_])", "#" + p);
            }
            return out;
        }

        private void writeMNT() throws IOException {
            List<String> out = new ArrayList<>();
            out.add(String.format("%-4s %-12s %-8s %-8s %-8s", "IDX", "NAME", "MDTST", "PARAMS", "DEFKEYS"));
            for (int i = 1; i < mnt.size(); i++) {
                MNTEntry e = mnt.get(i);
                out.add(String.format("%-4d %-12s %-8d %-8d %-8d",
                        e.index, e.name, e.mdtStart, e.paramCount(), e.defaultCount()));
                // Also write a comment line with param order and defaults (handy for Pass-II)
                out.add(String.format("#PARAMS %s", String.join(",", e.formals)));
                if (!e.defaults.isEmpty()) {
                    List<String> d = new ArrayList<>();
                    for (String f : e.formals) {
                        if (e.defaults.containsKey(f)) d.add(f + "=" + e.defaults.get(f));
                    }
                    out.add("#DEFAULTS " + String.join(",", d));
                } else {
                    out.add("#DEFAULTS");
                }
            }
            writeLines(MNT_FILE, out);
        }

        private void writeMDT() throws IOException {
            List<String> out = new ArrayList<>();
            out.add(String.format("%-6s %s", "INDEX", "LINE"));
            for (int i = 1; i < mdt.size(); i++) {
                out.add(String.format("%-6d %s", i, mdt.get(i)));
            }
            writeLines(MDT_FILE, out);
        }
    }

    // ============================================================
    //                          PASS - II
    // ============================================================
    static class Pass2 {

        // Reconstructed tables
        List<MNTEntry> mnt = new ArrayList<>(); // 1-based (dummy at 0)
        List<String> mdt = new ArrayList<>();         // 1-based (dummy at 0)
        Map<String, MNTEntry> macroByName = new HashMap<>();

        void run() throws IOException {
            readMNT();
            readMDT();

            List<String> input = readLines(INTERMEDIATE_FILE);
            Deque<String> work = new ArrayDeque<>();
            for (String s : input) {
                String line = sanitize(s);
                if (!isBlank(line)) work.add(line);
            }

            List<String> output = new ArrayList<>();

            while (!work.isEmpty()) {
                String line = work.pollFirst();
                if (isMacroCall(line)) {
                    List<String> expanded = expandOne(line);
                    // Support nested expansions: push expanded lines to front
                    for (int k = expanded.size() - 1; k >= 0; k--) {
                        work.addFirst(expanded.get(k));
                    }
                } else {
                    output.add(line);
                }
            }

            writeLines(EXPANDED_FILE, output);
        }

        private boolean isMacroCall(String line) {
            // A macro call starts with a known macro name (case-insensitive)
            String first = firstToken(line);
            return first != null && macroByName.containsKey(first.toUpperCase());
        }

        private String firstToken(String line) {
            String[] p = line.trim().split("\\s+", 2);
            return p.length > 0 ? p[0] : null;
        }

        private List<String> expandOne(String callLine) {
            // Example call:  INCR AREG,5  OR  INCR &VAL=10,&REG=CREG
            String[] parts = callLine.trim().split("\\s+", 2);
            String name = parts[0].toUpperCase();
            String argStr = (parts.length == 2) ? parts[1].trim() : "";

            MNTEntry e = macroByName.get(name);
            if (e == null) {
                // Should not happen due to isMacroCall() check, but be safe.
                return List.of(callLine);
            }

            // Build ALA (Actual -> positional): #1 -> actualValue etc.
            String[] actuals = buildActualList(argStr, e);

            // Walk MDT from e.mdtStart until "MEND", substitute #i with actuals[i-1]
            List<String> out = new ArrayList<>();
            int i = e.mdtStart;
            while (i < mdt.size()) {
                String body = mdt.get(i++);
                if (body.equalsIgnoreCase("MEND")) break;
                String expanded = substituteActuals(body, actuals);
                out.add(expanded);
            }
            return out;
        }

        private String[] buildActualList(String argStr, MNTEntry e) {
            int n = e.formals.size();
            String[] actuals = new String[n]; // null means not provided yet

            Map<String, String> keyed = new HashMap<>();
            List<String> posArgs = new ArrayList<>();

            if (!isBlank(argStr)) {
                String[] parts = argStr.split(",");
                for (String raw : parts) {
                    String t = raw.trim();
                    if (t.isEmpty()) continue;
                    int eq = t.indexOf('=');
                    if (eq >= 0) {
                        String key = t.substring(0, eq).trim();
                        String val = t.substring(eq + 1).trim();
                        // key may be "&A" or "A"; normalize to "&A"
                        if (!key.startsWith("&")) key = "&" + key;
                        keyed.put(key.toUpperCase(), val);
                    } else {
                        posArgs.add(t);
                    }
                }
            }

            // Fill positional first
            int pi = 0;
            for (int k = 0; k < n && pi < posArgs.size(); k++) {
                actuals[k] = posArgs.get(pi++);
            }
            // Fill keyword overrides
            for (int k = 0; k < n; k++) {
                String f = e.formals.get(k).toUpperCase();
                if (keyed.containsKey(f)) {
                    actuals[k] = keyed.get(f);
                }
            }
            // Fill defaults
            for (int k = 0; k < n; k++) {
                if (actuals[k] == null) {
                    String f = e.formals.get(k);
                    if (e.defaults.containsKey(f)) actuals[k] = e.defaults.get(f);
                }
            }
            // Any remaining unset -> empty string
            for (int k = 0; k < n; k++) if (actuals[k] == null) actuals[k] = "";

            return actuals;
        }

        private String substituteActuals(String body, String[] actuals) {
            String out = body;
            // Replace #1, #2, ...
            for (int i = 0; i < actuals.length; i++) {
                String placeholder = "#" + (i + 1);
                // Replace occurrences not embedded in larger tokens of digits
                out = out.replace(placeholder, actuals[i]);
            }
            return out;
        }

        // --------- Read Tables produced by Pass-I ----------
        void readMNT() throws IOException {
            List<String> lines = readLines(MNT_FILE);
            // Expect blocks:
            // IDX NAME MDTST PARAMS DEFKEYS
            // #PARAMS &A,&B,&C
            // #DEFAULTS &B=1,&C=5
            mnt.add(null); // 1-based
            for (int i = 1; i < lines.size();) {
                String s = sanitize(lines.get(i));
                if (isBlank(s)) { i++; continue; }
                if (s.startsWith("#")) { i++; continue; }
                if (s.toUpperCase().startsWith("IDX")) { i++; continue; }

                String[] cols = s.trim().split("\\s+");
                if (cols.length < 5) { i++; continue; }

                MNTEntry e = new MNTEntry();
                e.index = Integer.parseInt(cols[0]);
                e.name  = cols[1].toUpperCase();
                e.mdtStart = Integer.parseInt(cols[2]);

                // Read the next two meta lines (#PARAMS, #DEFAULTS)
                String paramsLine = (i + 1 < lines.size()) ? sanitize(lines.get(i + 1)) : "";
                String defaultsLine = (i + 2 < lines.size()) ? sanitize(lines.get(i + 2)) : "";
                i += 3;

                e.formals = new ArrayList<>();
                e.pos = new HashMap<>();
                e.defaults = new HashMap<>();

                if (paramsLine.toUpperCase().startsWith("#PARAMS")) {
                    String p = paramsLine.substring("#PARAMS".length()).trim();
                    if (!p.isEmpty()) {
                        if (p.startsWith("&")) {
                            for (String tok : p.split(",")) {
                                String f = tok.trim();
                                if (!f.isEmpty()) {
                                    e.formals.add(f);
                                    e.pos.put(f.toUpperCase(), e.formals.size());
                                }
                            }
                        }
                    }
                }

                if (defaultsLine.toUpperCase().startsWith("#DEFAULTS")) {
                    String d = defaultsLine.substring("#DEFAULTS".length()).trim();
                    if (!d.isEmpty()) {
                        for (String tok : d.split(",")) {
                            String t = tok.trim();
                            if (t.isEmpty()) continue;
                            int eq = t.indexOf('=');
                            if (eq >= 0) {
                                String f = t.substring(0, eq).trim();
                                String val = t.substring(eq + 1).trim();
                                e.defaults.put(f, val);
                            }
                        }
                    }
                }

                mnt.add(e);
                macroByName.put(e.name, e);
            }
        }

        void readMDT() throws IOException {
            List<String> lines = readLines(MDT_FILE);
            mdt.add("<dummy>"); // 1-based
            for (int i = 1; i < lines.size(); i++) {
                String s = lines.get(i);
                if (s.trim().isEmpty()) continue;
                // Format: "INDEX  LINE..."
                // split once on whitespace after index field
                int firstSpace = s.indexOf(' ');
                if (firstSpace < 0) continue;
                String rest = s.substring(firstSpace).trim();
                // rest begins with the line content
                mdt.add(rest);
            }
        }
    }
}
