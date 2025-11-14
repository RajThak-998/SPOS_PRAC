import java.io.*;
import java.util.*;

/**
 * Pass2FromTables
 *
 * Reads:
 *  - MNT.txt  (format: index <tab><tab> MACRONAME <tab> MDT_index) OR MACRONAME <tab> MDT_index (robust)
 *  - MDT.txt  (format: lineIndex <tab><tab> <mdt_line>)
 *  - ALA.txt  (format produced by Pass1Fixed: blocks "ALA for Macro: <name>" then lines "#0 -> &X" or "#0 &X" etc.)
 *
 * Reads source program from: inputmac2.txt
 * Writes expanded program to: pass2_output.txt
 *
 * Algorithm:
 *  - Build mntMap: macroName -> MDT index
 *  - Build mdtList: index -> raw MDT line (header is at mntMap.get(macroName))
 *  - Build alaList: list indexed by macro-definition order: Map<positionalKey, formalName>
 *    (also keep map from macroName -> alaMap index if present in ALA file)
 *  - For each line in inputmac2.txt (after START), detect macro invocation:
 *      - If opcode equals macroName (or token1 equals macroName), parse actual args
 *      - From MNT get header line at MDT index, parse formal params and defaults
 *      - Map positional formal -> actual (use default if actual missing)
 *      - For each MDT body line between header+1 .. MEND-1: replace formal tokens or ALA keys (#0,#1) with actuals,
 *        and write to output.
 */
public class Pass2FromTables {

    static Map<String, Integer> mnt = new LinkedHashMap<>(); // MACRO -> MDT index
    static List<String> mdt = new ArrayList<>();            // MDT lines (index -> line)
    static Map<String, LinkedHashMap<String, String>> alaByMacroName = new LinkedHashMap<>();
    static Map<Integer, String> mdtIndexLine = new HashMap<>(); // index -> raw MDT line (trimmed)

    public static void main(String[] args) {
        try {
            loadMDT("MDT.txt");
            loadMNT("MNT.txt");
            loadALA("ALA.txt");

            expandProgram("input_macro.txt", "pass2_output.txt");

            System.out.println("Pass-2 expansion finished. Output -> pass2_output.txt");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ---------------------- loaders --------------------------

    // Load MDT into mdtIndexLine and mdt list (we ignore the left index number if present)
    static void loadMDT(String filename) throws IOException {
        File f = new File(filename);
        if (!f.exists()) {
            System.err.println("MDT file not found: " + filename);
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            // We'll store full list in mdt[] so that indices align with what's in MNT.
            // But MDT.txt may contain explicit indexes at line start e.g. "0    COMPUTE &F,&S"
            int maxIndexSeen = -1;
            TreeMap<Integer, String> tmp = new TreeMap<>();
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                // If line starts with a number, split it off
                String trimmed = line.trim();
                String[] parts = trimmed.split("\\s+", 2);
                try {
                    int idx = Integer.parseInt(parts[0]);
                    String rest = (parts.length > 1) ? parts[1] : "";
                    tmp.put(idx, rest);
                    maxIndexSeen = Math.max(maxIndexSeen, idx);
                } catch (NumberFormatException nfe) {
                    // No index given — push sequentially
                    tmp.put(++maxIndexSeen, trimmed);
                }
            }
            // Fill mdt list up to maxIndexSeen
            for (int i = 0; i <= maxIndexSeen; ++i) {
                String val = tmp.getOrDefault(i, "");
                mdtIndexLine.put(i, val);
                mdt.add(val);
            }
        }
        System.out.println("Loaded MDT entries: " + mdt.size());
    }

    // Load MNT into mnt map: assumes lines like "0    MACRONAME    MDT_index" or "MACRONAME    MDT_index"
    static void loadMNT(String filename) throws IOException {
        File f = new File(filename);
        if (!f.exists()) {
            System.err.println("MNT file not found: " + filename);
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String trimmed = line.trim();
                // try formats
                String[] parts = trimmed.split("\\s+");
                if (parts.length >= 3) {
                    // Could be "0  MACRONAME  MDTindex" or "k    MACRONAME  value"
                    // Try to find a token that is macro name (non-numeric)
                    int idxForMdt = -1;
                    String macroName = null;
                    for (int i = 0; i < parts.length; ++i) {
                        if (!isInteger(parts[i])) {
                            // treat next token (if any) as probably macro or maybe the macro is this token
                            // heuristic: macro name will be token that is not an integer and not "->"
                            macroName = parts[i];
                            if (i + 1 < parts.length && isInteger(parts[i + 1])) {
                                idxForMdt = Integer.parseInt(parts[i + 1]);
                            }
                            break;
                        }
                    }
                    if (macroName != null && idxForMdt >= 0) {
                        mnt.put(macroName.toUpperCase(), idxForMdt);
                    } else if (parts.length >= 2) {
                        // fallback: parts[1] macro, last part index
                        String maybeMacro = parts[1];
                        String maybeIdx = parts[parts.length - 1];
                        if (isInteger(maybeIdx)) {
                            mnt.put(maybeMacro.toUpperCase(), Integer.parseInt(maybeIdx));
                        } else {
                            // best-effort: map parts[1] to parts[2] if parts[2] integer
                            if (parts.length >= 3 && isInteger(parts[2])) {
                                mnt.put(parts[1].toUpperCase(), Integer.parseInt(parts[2]));
                            }
                        }
                    }
                } else if (parts.length == 2) {
                    // MACRONAME MDTindex
                    String name = parts[0];
                    String idx = parts[1];
                    if (isInteger(idx)) {
                        mnt.put(name.toUpperCase(), Integer.parseInt(idx));
                    }
                }
            }
        }
        System.out.println("Loaded MNT entries: " + mnt.size());
    }

    // Load ALA: attempt to find for each macro name a positional mapping.
    // Accepts multiple formats produced by various pass1 versions:
    // - blocks starting "ALA for Macro: NAME" followed by lines "#0 -> &X" or "#0 &X" or lines listing actuals.
    // - Blocks that contain macro header then actual mappings.
    static void loadALA(String filename) throws IOException {
        File f = new File(filename);
        if (!f.exists()) {
            System.err.println("ALA file not found: " + filename);
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            String currentMacro = null;
            LinkedHashMap<String, String> curMap = null;

            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    if (currentMacro != null && curMap != null) {
                        alaByMacroName.put(currentMacro.toUpperCase(), curMap);
                    }
                    currentMacro = null;
                    curMap = null;
                    continue;
                }
                String trimmed = line.trim();

                // detect headers: "ALA for Macro: NAME" or "ALA for Macro index NAME"
                String low = trimmed.toLowerCase();
                if (low.startsWith("ala for macro")) {
                    // finalize previous
                    if (currentMacro != null && curMap != null) {
                        alaByMacroName.put(currentMacro.toUpperCase(), curMap);
                    }
                    // extract name if present on same line
                    String[] tokens = trimmed.split(":");
                    String name = null;
                    if (tokens.length >= 2) {
                        name = tokens[1].trim();
                    } else {
                        // fallback: last token
                        String[] parts = trimmed.split("\\s+");
                        name = parts[parts.length - 1];
                    }
                    currentMacro = (name != null) ? name.trim() : null;
                    curMap = new LinkedHashMap<>();
                    continue;
                }

                // "Expanded Code: MACRONAME" -> treat as header for collecting expansion (not ALA mapping)
                if (low.contains("expanded code")) {
                    // we will not parse the expansion body as ALA here
                    // finalize previous ALA map
                    if (currentMacro != null && curMap != null) {
                        alaByMacroName.put(currentMacro.toUpperCase(), curMap);
                    }
                    // extract macro name
                    String[] parts = trimmed.split("\\s+");
                    String macroName = parts[parts.length - 1];
                    currentMacro = macroName;
                    curMap = new LinkedHashMap<>();
                    // Note: expansion body follows but ALA map may not be here; continue to next lines
                    continue;
                }

                // Typical ALA lines: "#0 -> &X" or "#0 &X" or "&X"
                if (trimmed.startsWith("#")) {
                    // "#0 -> &X" or "#0 &X"
                    String[] parts = trimmed.split("\\s+|->");
                    String key = parts[0].trim();
                    String value = "";
                    if (parts.length >= 2) {
                        value = parts[parts.length - 1].trim();
                    }
                    if (curMap == null) curMap = new LinkedHashMap<>();
                    curMap.put(key, value);
                    continue;
                }

                // Another format: line like "&X,&Y,&OP" (header) -> we can map #0->&X, #1->&Y ...
                if (trimmed.startsWith("&")) {
                    String[] params = trimmed.split("\\s*,\\s*");
                    if (curMap == null) curMap = new LinkedHashMap<>();
                    for (int i = 0; i < params.length; ++i) {
                        curMap.put("#" + i, params[i]);
                    }
                    continue;
                }

                // If line looks like a macro header "MACRONAME &X,&Y" we can pick tokens
                String[] tokens = trimmed.split("\\s+");
                if (tokens.length >= 2 && tokens[1].startsWith("&")) {
                    String macroName = tokens[0];
                    String paramStr = trimmed.substring(trimmed.indexOf(tokens[1]));
                    String[] params = paramStr.split("\\s*,\\s*");
                    curMap = new LinkedHashMap<>();
                    for (int i = 0; i < params.length; ++i) {
                        curMap.put("#" + i, params[i].trim());
                    }
                    currentMacro = macroName;
                    continue;
                }

                // fallback: if line contains '->' maybe mapping like "X -> value"
                if (trimmed.contains("->")) {
                    String[] p = trimmed.split("->");
                    if (p.length >= 2) {
                        String left = p[0].trim();
                        String right = p[1].trim();
                        if (curMap == null) curMap = new LinkedHashMap<>();
                        curMap.put(left, right);
                    }
                    continue;
                }
            } // while
            // finalize last
            if (currentMacro != null && curMap != null) {
                alaByMacroName.put(currentMacro.toUpperCase(), curMap);
            }
        }
        System.out.println("Loaded ALA entries for macros: " + alaByMacroName.keySet());
    }

    // ---------------------- expansion ------------------------

    static void expandProgram(String srcFile, String outFile) throws IOException {
        File f = new File(srcFile);
        if (!f.exists()) {
            System.err.println("Source program not found: " + srcFile);
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(f));
             BufferedWriter bw = new BufferedWriter(new FileWriter(outFile))) {

            String line;
            boolean inProgram = false;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    bw.write(System.lineSeparator());
                    continue;
                }

                String trimmed = line.trim();
                if (trimmed.toUpperCase().startsWith("START")) {
                    inProgram = true;
                    bw.write(line);
                    bw.write(System.lineSeparator());
                    continue;
                }
                if (trimmed.toUpperCase().startsWith("END")) {
                    inProgram = false;
                    bw.write(line);
                    bw.write(System.lineSeparator());
                    continue;
                }
                if (!inProgram) {
                    // copy everything before START
                    bw.write(line);
                    bw.write(System.lineSeparator());
                    continue;
                }

                // parse tokens (label optional)
                String[] parts = line.trim().split("\\s+", 3);
                String tok0 = parts.length > 0 ? parts[0] : "";
                String tok1 = parts.length > 1 ? parts[1] : "";
                String rest = parts.length > 2 ? parts[2] : "";

                String invokedMacro = null;
                String labelPart = "";
                String opcodePart = "";
                String operandPart = "";

                // check both positions for macro name
                if (mnt.containsKey(tok0.toUpperCase())) {
                    invokedMacro = tok0.toUpperCase();
                    opcodePart = tok0;
                    operandPart = (parts.length > 1) ? (parts.length > 2 ? parts[2] : "") : "";
                    labelPart = "";
                } else if (mnt.containsKey(tok1.toUpperCase())) {
                    invokedMacro = tok1.toUpperCase();
                    labelPart = tok0;
                    opcodePart = tok1;
                    operandPart = (parts.length > 2) ? parts[2] : "";
                }

                if (invokedMacro == null) {
                    // not a macro invocation, copy line
                    bw.write(line);
                    bw.write(System.lineSeparator());
                    continue;
                }

                // We have a macro invocation. Get MDT start index:
                Integer mdtStartIdx = mnt.get(invokedMacro);
                if (mdtStartIdx == null || mdtStartIdx < 0 || mdtStartIdx >= mdt.size()) {
                    // cannot expand (fallback to copy)
                    System.err.println("Warning: MDT index for macro " + invokedMacro + " invalid.");
                    bw.write(line);
                    bw.write(System.lineSeparator());
                    continue;
                }

                // Parse header line from MDT: it should contain formal parameters after macro name.
                String header = mdt.get(mdtStartIdx);
                // header may look like "MACRONAME &X,&Y" or "COMPUTE &F,&S"
                String[] headerParts = header.trim().split("\\s+", 2);
                String formalList = (headerParts.length > 1) ? headerParts[1].trim() : "";
                List<String> formals = new ArrayList<>();
                List<String> defaults = new ArrayList<>(); // parallel: default value or null

                if (!formalList.isEmpty()) {
                    String[] fparts = formalList.split("\\s*,\\s*");
                    for (String fp : fparts) {
                        if (fp.contains("=")) {
                            String[] kv = fp.split("=", 2);
                            formals.add(kv[0].trim());
                            defaults.add(kv[1].trim());
                        } else {
                            formals.add(fp.trim());
                            defaults.add(null);
                        }
                    }
                }

                // Parse actual arguments from invocation
                List<String> actuals = new ArrayList<>();
                if (operandPart != null && !operandPart.trim().isEmpty()) {
                    String[] actualParts = operandPart.split("\\s*,\\s*");
                    for (String a : actualParts) actuals.add(a.trim());
                }

                // Build mapping from positional keys (#0 -> actual/formal substitution)
                Map<String, String> positionalActual = new HashMap<>();
                for (int i = 0; i < formals.size(); ++i) {
                    String actualVal = null;
                    if (i < actuals.size()) {
                        actualVal = actuals.get(i);
                    } else {
                        actualVal = defaults.get(i); // may be null
                    }
                    // If actualVal still null, set to formal name as fallback
                    if (actualVal == null) actualVal = formals.get(i);
                    positionalActual.put("#" + i, actualVal);
                    // Also map formal name itself to actualVal e.g. &X -> ACT
                    positionalActual.put(formals.get(i), actualVal);
                }

                // If ALA has mapping for macro, merge it to make replacements by #n -> formalName mapping.
                LinkedHashMap<String, String> alaMap = alaByMacroName.get(invokedMacro);
                if (alaMap != null) {
                    // For each #k in alaMap, alaMap.get(#k) returns formal param e.g. &X.
                    // Map #k -> actualVal already done; ensure formal->actual mapping too.
                    for (Map.Entry<String,String> e : alaMap.entrySet()) {
                        String key = e.getKey();   // e.g. "#0"
                        String formal = e.getValue(); // e.g. "&X"
                        if (positionalActual.containsKey(key)) {
                            String act = positionalActual.get(key);
                            positionalActual.put(formal, act);
                        }
                    }
                }

                // Now iterate MDT lines starting from mdtStartIdx+1 until "MEND" and substitute tokens
                int p = mdtStartIdx + 1;
                while (p < mdt.size()) {
                    String mdtLine = mdt.get(p);
                    if (mdtLine == null) { p++; continue; }
                    String mt = mdtLine.trim();
                    if (mt.equalsIgnoreCase("MEND")) break;

                    // Replace occurrences of positional keys or formal names in the mdtLine with actuals.
                    // We should do token-based replace: split by whitespace and commas
                    String[] tokens = mdtLine.split("\\s+|,");
                    StringBuilder outLine = new StringBuilder();
                    int tokIndex = 0;
                    // We'll rebuild preserving first two tokens spacing as in earlier code
                    for (int i = 0; i < tokens.length; ++i) {
                        String tk = tokens[i];
                        if (i == 0) {
                            outLine.append(tk);
                        } else if (i == 1) {
                            outLine.append(" ").append(tk);
                        } else {
                            outLine.append(",");
                            // try direct match to positional key
                            String sub = tk;
                            if (positionalActual.containsKey(tk)) {
                                sub = positionalActual.get(tk);
                            } else {
                                // maybe token is like "&X" with some punctuation - do exact equals
                                if (positionalActual.containsKey(tk.trim())) sub = positionalActual.get(tk.trim());
                            }
                            outLine.append(sub);
                        }
                    }

                    // Write expanded line to output
                    bw.write(outLine.toString());
                    bw.write(System.lineSeparator());
                    p++;
                } // end while MDT body

                // finished expanding this macro invocation; continue to next line in source program
            } // end reading source
        }
    }

    // -------------------- helpers -------------------------

    static boolean isInteger(String s) {
        if (s == null) return false;
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException nfe) {
            return false;
        }
    }
}
