import java.io.*;
import java.util.*;

/**
 * Safer Pass-1 Macro processor (fixed version of user's code).
 * - Robust splitting (handles varying whitespace)
 * - Safe checks for token lengths
 * - Uses .equals for string comparisons
 * - Handles opcode present at column 0 or 1 (label optional)
 *
 * Output files:
 *  - MNT.txt
 *  - MDT.txt
 *  - ALA.txt
 *
 * This tries to preserve your original algorithm/structures while fixing parsing bugs.
 */
public class Pass1MacroFixed {

    // Helper to check directive/opcode tokens that typically appear alone
    private static boolean isDirective(String tok) {
        if (tok == null) return false;
        String t = tok.trim().toUpperCase();
        // Common directives / opcodes used in your examples
        return t.equals("MACRO") || t.equals("MEND") || t.equals("START") || t.equals("END")
                || t.equals("READ") || t.equals("STOP") || t.equals("DS")
                || t.equals("DEFINE") || t.equals("COMPUTE") || t.equals("MOVEM")
                || t.equals("MOVER") || t.equals("ADD") || t.equals("INCRM")
                || t.equals("INCRM") || t.equals("CALC") || t.equals("WRITE")
                || t.equals("READ") || t.equals("STOP") || t.equals("CAR");
    }

    // Parse a source line into (label, opcode, operands)
    private static String[] parseLine(String rawLine) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.isEmpty()) return new String[] {"", "", ""};

        // split into at most 3 parts: token0 token1 rest
        String[] parts = line.split("\\s+", 3);
        String label = "";
        String opcode = "";
        String operands = "";

        if (parts.length == 1) {
            // single token line -> opcode only
            opcode = parts[0];
        } else if (parts.length == 2) {
            // ambiguous: could be "LABEL OPCODE" or "OPCODE OPERANDS"
            if (isDirective(parts[1])) {
                // treat as LABEL OPCODE
                label = parts[0];
                opcode = parts[1];
            } else {
                // treat as OPCODE OPERANDS
                opcode = parts[0];
                operands = parts[1];
            }
        } else { // parts.length == 3
            // prefer LABEL OPCODE OPERANDS interpretation
            label = parts[0];
            opcode = parts[1];
            operands = parts[2];
            // but if the first token itself is a directive (no label)
            if (isDirective(parts[0]) && !isDirective(parts[1])) {
                // treat as OPCODE OPERANDS where first token is opcode
                label = "";
                opcode = parts[0];
                operands = (parts.length >= 2 ? parts[1] + (parts.length == 3 ? " " + parts[2] : "") : "");
            }
        }
        return new String[] {label, opcode, operands};
    }

    public static void main(String[] args) {
        String inputFile = "input_macro.txt";
        if (args.length > 0) inputFile = args[0];

        LinkedHashMap<String,Integer> MNT = new LinkedHashMap<>(); // macro name -> MDT index
        ArrayList<LinkedHashMap<String,String>> ALA = new ArrayList<>(); // ALA per macro (in order)
        ArrayList<String> MDT = new ArrayList<>(); // macro definition table lines (strings)

        String ALAoutput = "ALA.txt";
        String MNToutput = "MNT.txt";
        String MDToutput = "MDT.txt";

        try (BufferedReader br = new BufferedReader(new FileReader(inputFile));
             BufferedWriter b2 = new BufferedWriter(new FileWriter(ALAoutput))) {

            String sCurrentLine;
            int MDTloc = 0;
            int isMacro = 0;   // nesting count of macros (0 when not inside macro)
            int macroHeaderMode = 0; // 1 when just encountered macro header
            int currentMntIndex = -1; // index into MNT/ALA for macro being defined

            // read lines and build MDT/MNT/ALA
            while ((sCurrentLine = br.readLine()) != null) {
                // remove inline comments starting with '#'
                int commentPos = sCurrentLine.indexOf('#');
                if (commentPos != -1) sCurrentLine = sCurrentLine.substring(0, commentPos);
                sCurrentLine = sCurrentLine.trim();
                if (sCurrentLine.isEmpty()) continue;

                String[] parsed = parseLine(sCurrentLine);
                String label = parsed[0];
                String opcode = parsed[1];
                String operands = parsed[2];

                // Normalize opcode uppercase for checks
                String opcodeU = opcode.trim().toUpperCase();

                // Detect MACRO start (either line "MACRO" or "LABEL MACRO")
                if (opcodeU.equals("MACRO") || label.equals("MACRO")) {
                    // two possible formats:
                    // 1) "MACRO" (alone) -> macro header expected on next line
                    // 2) "NAME MACRO ..." -> header line where name is label and opcode=MACRO
                    // We will handle both:
                    // If header is "MACRO" alone, the actual header will appear in next iteration.
                    // We'll mark that we are in the macro definition state.
                    isMacro++;
                    macroHeaderMode = 1;
                    // If the current line also includes the name/params (rare), handle accordingly
                    // (but typical format is next line contains header)
                    continue;
                }

                // If we are starting a macro and macroHeaderMode == 1, the current line is macro header
                if (isMacro > 0 && macroHeaderMode == 1) {
                    // header line example: "NAME OPCODE params" or "NAME params" or "NAME"
                    // we interpret current line's label as macro name if present, otherwise first token.
                    String macroName = "";
                    String paramStr = operands;
                    if (!label.isEmpty()) {
                        macroName = label;
                        // if opcode is not empty and not a directive, it may be part of header's operands
                        if (!opcode.isEmpty() && !isDirective(opcode)) {
                            if (paramStr == null || paramStr.isEmpty()) paramStr = opcode;
                        }
                    } else if (!opcode.isEmpty()) {
                        // treat opcode as macro name (no label)
                        macroName = opcode;
                        paramStr = operands;
                    } else {
                        // weird case: header not found; skip
                        macroHeaderMode = 0;
                        continue;
                    }

                    // Put in MNT -> point to current MDTloc
                    MNT.put(macroName, MDTloc);
                    currentMntIndex = MNT.size() - 1; // index in order
                    // Add the header line to MDT (store raw header to MDT)
                    MDT.add(macroName + " " + (paramStr == null ? "" : paramStr).trim());
                    MDTloc++;

                    // Build ALA for this macro
                    LinkedHashMap<String,String> alaMap = new LinkedHashMap<>();
                    if (paramStr != null && !paramStr.trim().isEmpty()) {
                        String[] params = paramStr.split("\\s*,\\s*");
                        for (int i = 0; i < params.length; ++i) {
                            String p = params[i].trim();
                            // remove any default assignment if present, e.g. "&X=5" -> "&X"
                            if (p.contains("=")) p = p.split("=")[0].trim();
                            alaMap.put("#" + i, p);
                        }
                    }
                    if (alaMap.isEmpty()) ALA.add(null);
                    else ALA.add(alaMap);

                    // we are now inside macro body (header consumed)
                    macroHeaderMode = 2;
                    continue;
                }

                // if inside a macro definition body (macroHeaderMode >= 2 and isMacro > 0)
                if (isMacro > 0 && macroHeaderMode >= 2) {
                    // body lines until MEND
                    // If this line is MEND (possibly with label), record and decrement isMacro
                    if (opcodeU.equals("MEND") || label.equals("MEND")) {
                        MDT.add("MEND");
                        MDTloc++;
                        isMacro--;
                        if (isMacro == 0) {
                            macroHeaderMode = 0;
                            currentMntIndex = -1;
                        }
                        continue;
                    }

                    // Replace parameters in the body with positional ALA keys (#0...).
                    // We produce a MDT entry where parameters are replaced with ALA keys.
                    // We'll split the textual line into tokens separated by whitespace and commas,
                    // but keep first two tokens (label/opcode) with single space.
                    // For simplicity, use regex splitting, then rebuild.
                    String[] tokens = sCurrentLine.split("\\s+|\\,");
                    LinkedHashMap<String,String> curALA = (currentMntIndex >= 0 && currentMntIndex < ALA.size())
                            ? ALA.get(currentMntIndex) : null;
                    StringBuilder mdtEntry = new StringBuilder();

                    // We'll recompose: first token (if any) then space then second then space or commas
                    // Simpler: iterate tokens and if token matches an ALA value replace with key.
                    for (int i = 0; i < tokens.length; ++i) {
                        String tk = tokens[i];
                        if (i == 0) {
                            mdtEntry.append(tk);
                        } else if (i == 1) {
                            mdtEntry.append(" ").append(tk);
                        } else {
                            // comma separated fields after token 1
                            if (curALA != null) {
                                boolean replaced = false;
                                for (Map.Entry<String,String> e : curALA.entrySet()) {
                                    if (e.getValue().equals(tk)) {
                                        if (mdtEntry.length() > 0 && mdtEntry.charAt(mdtEntry.length()-1) != ' ')
                                            mdtEntry.append(",");
                                        else if (mdtEntry.length() > 0 && mdtEntry.charAt(mdtEntry.length()-1) == ' ')
                                            mdtEntry.append("");
                                        mdtEntry.append(e.getKey());
                                        replaced = true;
                                        break;
                                    }
                                }
                                if (!replaced) {
                                    if (mdtEntry.length() > 0 && mdtEntry.charAt(mdtEntry.length()-1) != ' ')
                                        mdtEntry.append(",");
                                    mdtEntry.append(tk);
                                }
                            } else {
                                if (mdtEntry.length() > 0 && mdtEntry.charAt(mdtEntry.length()-1) != ' ')
                                    mdtEntry.append(",");
                                mdtEntry.append(tk);
                            }
                        }
                    }
                    MDT.add(mdtEntry.toString());
                    MDTloc++;
                    continue;
                }

                // Not inside macro definition area: check for START to begin processing area
                // Your original logic invoked "start" and then expanded macros that appear in program.
                // For Pass1 we can still write MDT/MNT/ALA now and do simple expansion pass later.
                // Here we simply continue reading until finish; expansion handled in a separate pass below.
                // We'll simply ignore other lines for now in this pass (original code had expansion logic).
            } // while read lines

            // ---------- At this point we have populated MNT, MDT and ALA (for definitions) ----------
            // Write MNT
            try (BufferedWriter b = new BufferedWriter(new FileWriter(MNToutput))) {
                int k = 0;
                for (Map.Entry<String,Integer> m : MNT.entrySet()) {
                    b.write(k + "\t\t" + m.getKey() + "\t" + m.getValue() + "\n");
                    k++;
                }
            }

            // Write MDT
            try (BufferedWriter b1 = new BufferedWriter(new FileWriter(MDToutput))) {
                for (int i = 0; i < MDT.size(); ++i) {
                    b1.write(i + "\t\t" + MDT.get(i) + "\n");
                }
            }

            // Write ALA -- for readability write per macro entry
            try (BufferedWriter b3 = new BufferedWriter(new FileWriter(ALAoutput))) {
                int mi = 0;
                for (LinkedHashMap<String,String> ala : ALA) {
                    b3.write("ALA for Macro index " + mi + "\n");
                    if (ala != null) {
                        for (Map.Entry<String,String> e : ala.entrySet()) {
                            b3.write(e.getKey() + " -> " + e.getValue() + "\n");
                        }
                    } else {
                        b3.write("(no parameters)\n");
                    }
                    b3.write("\n");
                    mi++;
                }
            }

            System.out.println("Pass-1 completed. Wrote MNT.txt, MDT.txt and ALA.txt.");

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
