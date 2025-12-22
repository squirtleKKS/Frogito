package lang.cli;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import lang.bytecodeGenerator.BytecodeGenerator;
import lang.bytecodeGenerator.Disassembler;
import lang.bytecodeGenerator.FrogcWriter;
import lang.lexer.Lexer;
import lang.lexer.LexingException;
import lang.lexer.token.Token;
import lang.optimizer.AstOptimizer;
import lang.parser.ParseException;
import lang.parser.Parser;
import lang.semantic.ast.node.Program;
import lang.semantic.ast.printer.ASTPrinter;
import lang.semantic.bytecode.BytecodeModule;
import lang.semantic.bytecode.ConstantPool;
import lang.semantic.bytecode.FunctionInfo;
import lang.semantic.bytecode.Instruction;
import lang.semantic.bytecode.OpCode;
import lang.semantic.symbols.FrogType;

public final class FrogcMain {

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        try {
            if (looksLikeScriptInvocation(args)) {
                ScriptArgs sa = parseScriptInvocation(args);
                compileSourceStringToFile(sa.sourceCode, sa.outputPath);
                return;
            }

            String command = args[0];
            switch (command) {
                case "build" -> handleBuild(args);
                case "run" -> handleRun(args);
                case "disasm" -> handleDisasm(args);
                case "ast" -> handleAst(args);
                case "opt-ast" -> handleOptAst(args);
                default -> {
                    printUsage();
                    System.exit(2);
                }
            }
        } catch (LexingException e) {
            printLexingError(e);
            System.exit(1);
        } catch (ParseException e) {
            printParseError(e);
            System.exit(1);
        } catch (RuntimeException e) {
            printGenericCompileError(e);
            System.exit(1);
        } catch (IOException e) {
            printIoError(e);
            System.exit(2);
        }
    }

    // =========================
    // Command: build
    // =========================

    private static void handleBuild(String[] args) throws IOException {
        if (args.length < 2 || args.length > 4) {
            printUsage();
            System.exit(2);
        }

        String inputPath = args[1];
        String outputPath;

        if (args.length == 2) {
            outputPath = deriveOutputPath(inputPath);
        } else if (args.length == 4 && "-o".equals(args[2])) {
            outputPath = args[3];
        } else {
            printUsage();
            System.exit(2);
            return;
        }

        String source = readSource(Path.of(inputPath));
        compileSourceStringToFile(source, outputPath);
        System.out.println("OK: wrote " + outputPath);
    }

    // =========================
    // Command: run (E2E)
    // =========================

    private static void handleRun(String[] args) throws IOException {
        if (args.length < 2) {
            printUsage();
            System.exit(2);
        }

        String inputPath = args[1];
        Path input = Path.of(inputPath);

        List<String> vmFlags = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--trace", "--jit-log", "--gc-log" -> vmFlags.add(a);
                default -> {
                    System.err.println("unknown flag: " + a);
                    printUsage();
                    System.exit(2);
                }
            }
        }

        String source = readSource(input);
        String outputPath = deriveOutputPath(inputPath);
        compileSourceStringToFile(source, outputPath);

        int code = runVm(outputPath, vmFlags);
        System.exit(code);
    }

    private static int runVm(String frogcPath, List<String> vmFlags) throws IOException {
        Path vm = detectVmBinary();

        List<String> cmd = new ArrayList<>();
        cmd.add(vm.toString());
        cmd.add("run");
        cmd.add(frogcPath);
        cmd.addAll(vmFlags);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();

        try {
            Process p = pb.start();
            return p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while running VM", e);
        }
    }

    private static Path detectVmBinary() {
        Path win = Path.of("frogitovm", "build", "frogvm.exe");
        if (Files.exists(win)) return win;

        Path unix = Path.of("frogitovm", "build", "frogvm");
        if (Files.exists(unix)) return unix;

        System.err.println("runtime error: frogvm binary not found. Expected one of:");
        System.err.println("  frogitovm/build/frogvm.exe");
        System.err.println("  frogitovm/build/frogvm");
        System.exit(1);
        return unix;
    }

    // =========================
    // Command: disasm
    // =========================

    private static void handleDisasm(String[] args) throws IOException {
        if (args.length != 2) {
            printUsage();
            System.exit(2);
        }
        BytecodeModule module = readModule(Path.of(args[1]));
        Disassembler.dump(module);
    }

    // =========================
    // Command: ast / opt-ast
    // =========================

    private static void handleAst(String[] args) throws IOException {
        if (args.length != 2) {
            printUsage();
            System.exit(2);
        }
        String source = readSource(Path.of(args[1]));
        Program program = parseProgram(source);
        new ASTPrinter().printProgram(program);
    }

    private static void handleOptAst(String[] args) throws IOException {
        if (args.length != 2) {
            printUsage();
            System.exit(2);
        }
        String source = readSource(Path.of(args[1]));
        Program program = parseProgram(source);
        Program optimized = new AstOptimizer().optimize(program);
        new ASTPrinter().printProgram(optimized);
    }

    private static boolean looksLikeScriptInvocation(String[] args) {
        if (args.length == 2) {
            String first = args[0];
            String second = args[1];

            if (isKnownCommand(first)) return false;

            return second.endsWith(".frogc");
        }

        if (args.length == 1) {
            String s = args[0].trim();
            return s.contains(".frogc") && s.contains(" ");
        }

        return false;
    }

    private static boolean isKnownCommand(String s) {
        return switch (s) {
            case "build", "run", "disasm", "ast", "opt-ast" -> true;
            default -> false;
        };
    }

    private static ScriptArgs parseScriptInvocation(String[] args) {
        if (args.length == 2) {
            return new ScriptArgs(args[0], args[1]);
        }

        String s = args[0].trim();
        int boundary = lastWhitespaceBoundary(s);
        if (boundary < 0) {
            throw new IllegalArgumentException("expected: <code> <output.frogc>");
        }

        String codePart = s.substring(0, boundary).trim();
        String outPart = s.substring(boundary).trim();

        codePart = stripOuterQuotes(codePart);
        outPart = stripOuterQuotes(outPart);

        return new ScriptArgs(codePart, outPart);
    }

    private static int lastWhitespaceBoundary(String s) {
        // Find start index of last token (output path), return index where code part ends.
        // We want split: [0..boundary) and [boundary..end)
        int i = s.length() - 1;
        while (i >= 0 && Character.isWhitespace(s.charAt(i))) i--;
        if (i < 0) return -1;

        // move to start of last token
        int end = i;
        while (i >= 0 && !Character.isWhitespace(s.charAt(i))) i--;
        int startLastToken = i + 1;

        if (startLastToken <= 0) return -1;
        return startLastToken;
    }

    private static String stripOuterQuotes(String s) {
        if (s.length() >= 2) {
            char a = s.charAt(0);
            char b = s.charAt(s.length() - 1);
            if ((a == '\'' && b == '\'') || (a == '"' && b == '"')) {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }

    private record ScriptArgs(String sourceCode, String outputPath) {}

    // =========================
    // Compilation pipeline
    // =========================

    private static void compileSourceStringToFile(String source, String outputPath) throws IOException {
        Program program = parseProgram(source);
        Program optimized = new AstOptimizer().optimize(program);

        BytecodeModule module = new BytecodeGenerator().generate(optimized);

        Path out = Path.of(outputPath);
        Path parent = out.getParent();
        if (parent != null) Files.createDirectories(parent);

        try (OutputStream os = new FileOutputStream(out.toFile())) {
            FrogcWriter.write(module, os);
        } catch (FileNotFoundException e) {
            System.err.println("io error: file not found: " + outputPath);
            System.exit(2);
        }
    }

    private static String readSource(Path path) throws IOException {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            System.err.println("io error: file not found: " + path);
            System.exit(2);
            return "";
        }
    }

    private static Program parseProgram(String source) {
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.tokenize();
        Parser parser = new Parser(tokens);
        return parser.parseProgram();
    }

    // =========================
    // .frogc reader (matches FrogcWriter)
    // =========================

    private static BytecodeModule readModule(Path path) throws IOException {
        try (InputStream is = new FileInputStream(path.toFile());
             DataInputStream d = new DataInputStream(is)) {

            byte[] magic = new byte[4];
            d.readFully(magic);
            if (magic[0] != 'F' || magic[1] != 'R' || magic[2] != 'O' || magic[3] != 'G') {
                throw new IOException("invalid .frogc header");
            }

            int version = d.readUnsignedShort();
            if (version != 1) {
                throw new IOException("unsupported .frogc version: " + version);
            }

            int constCount = d.readInt();
            int funcCount = d.readInt();
            int codeSize = d.readInt(); // instruction count

            ConstantPool consts = new ConstantPool();

            for (int i = 0; i < constCount; i++) {
                int tag = d.readUnsignedByte();
                switch (tag) {
                    case 1 -> consts.addInt(d.readInt());
                    case 2 -> consts.addFloat(d.readDouble());
                    case 3 -> consts.addBool(d.readUnsignedByte() != 0);
                    case 4 -> {
                        int len = d.readInt();
                        byte[] bytes = new byte[len];
                        d.readFully(bytes);
                        consts.addString(new String(bytes, StandardCharsets.UTF_8));
                    }
                    default -> throw new IOException("unknown const tag: " + tag);
                }
            }

            List<FunctionInfo> functions = new ArrayList<>();
            for (int i = 0; i < funcCount; i++) {
                int nameIdx = d.readInt();
                int paramCount = d.readUnsignedShort();
                int localCount = d.readUnsignedShort();
                int entryIp = d.readInt();

                FrogType returnType = readType(d.readUnsignedByte());
                List<FrogType> paramTypes = new ArrayList<>();
                for (int p = 0; p < paramCount; p++) {
                    paramTypes.add(readType(d.readUnsignedByte()));
                }

                functions.add(new FunctionInfo(
                        nameIdx,
                        paramCount,
                        localCount,
                        entryIp,
                        returnType,
                        paramTypes
                ));
            }

            List<Instruction> code = new ArrayList<>();
            for (int i = 0; i < codeSize; i++) {
                int opByte = d.readUnsignedByte();
                OpCode op = OpCode.values()[opByte];

                int flags = d.readUnsignedByte();
                boolean hasA = (flags & 1) != 0;
                boolean hasB = (flags & 2) != 0;

                int a = 0;
                int b = 0;
                if (hasA) a = d.readInt();
                if (hasB) b = d.readShort();

                Instruction ins;
                if (hasA && hasB) ins = Instruction.ab(op, a, b);
                else if (hasA) ins = Instruction.a(op, a);
                else if (hasB) ins = Instruction.b(op, b);
                else ins = Instruction.of(op);

                code.add(ins);
            }

            return new BytecodeModule(consts, functions, code);

        } catch (FileNotFoundException e) {
            System.err.println("io error: file not found: " + path);
            System.exit(2);
            return null;
        }
    }

    private static FrogType readType(int code) {
        return switch (code) {
            case 1 -> FrogType.INT;
            case 2 -> FrogType.FLOAT;
            case 3 -> FrogType.BOOL;
            case 4 -> FrogType.STRING;
            case 5 -> FrogType.VOID;
            case 6 -> FrogType.arrayOf(FrogType.VOID); // must match your typeToByte ARRAY mapping
            default -> FrogType.VOID;
        };
    }

    // =========================
    // Misc
    // =========================

    private static String deriveOutputPath(String inputPath) {
        Path p = Path.of(inputPath);
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(0, dot) : name;
        String outName = base + ".frogc";
        Path parent = p.getParent();
        return parent == null ? outName : parent.resolve(outName).toString();
    }

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("<code-string> <output.frogc>");
        System.err.println("build <input.frog> [-o <output.frogc>]");
        System.err.println("run <input.frog> [--trace] [--jit-log] [--gc-log]");
        System.err.println("disasm <input.frogc>");
        System.err.println("ast <input.frog>");
        System.err.println("opt-ast <input.frog>");
    }

    private static void printLexingError(LexingException e) {
        int line = e.getLine();
        int col = e.getColumn();
        String msg = e.getMessage();
        if (msg == null) msg = "";
        String lexeme = "?";
        String prefix = "Unexpected character: '";
        int start = msg.indexOf(prefix);
        if (start >= 0) {
            int after = start + prefix.length();
            int end = msg.indexOf('\'', after);
            if (end > after) lexeme = msg.substring(after, end);

            String suffix = " at " + line + ":" + col;
            int idx = msg.indexOf(suffix, end);
            if (idx >= 0) msg = msg.substring(0, idx);
        }
        System.err.println("error at " + line + ":" + col + " near '" + lexeme + "': " + msg);
    }

    private static void printParseError(ParseException e) {
        Token t = e.getToken();
        if (t == null) {
            printGenericCompileError(e);
            return;
        }
        int line = t.getLine();
        int col = t.getColumn();
        String lexeme = t.getLexeme();
        String msg = e.getMessage();
        if (msg == null) msg = "";
        String suffix = " at " + line + ":" + col + " near '" + lexeme + "'";
        if (msg.endsWith(suffix)) msg = msg.substring(0, msg.length() - suffix.length());
        System.err.println("error at " + line + ":" + col + " near '" + lexeme + "': " + msg);
    }

    private static void printGenericCompileError(RuntimeException e) {
        String msg = e.getMessage();
        if (msg == null || msg.isEmpty()) msg = e.toString();
        System.err.println("error: " + msg);
    }

    private static void printIoError(IOException e) {
        String msg = e.getMessage();
        if (msg == null || msg.isEmpty()) msg = e.toString();
        System.err.println("io error: " + msg);
    }

    private FrogcMain() {}
}
