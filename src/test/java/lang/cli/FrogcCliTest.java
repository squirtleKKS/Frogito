package lang.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class FrogcCliTest {

    private record Proc(int code, String out, String err) {}

    private static Proc runCli(Path workDir, String... args) throws Exception {
        String cp = System.getProperty("java.class.path");

        String[] cmd = new String[4 + args.length];
        cmd[0] = "java";
        cmd[1] = "-cp";
        cmd[2] = cp;
        cmd[3] = "lang.cli.FrogcMain";
        System.arraycopy(args, 0, cmd, 4, args.length);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        Process p = pb.start();

        String out = readAll(p.getInputStream());
        String err = readAll(p.getErrorStream());
        int code = p.waitFor();

        return new Proc(code, out, err);
    }

    private static String readAll(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void build_ok_writes_frogc() throws Exception {
        Path dir = Files.createTempDirectory("frogc-cli-");
        Path src = dir.resolve("hello.frog");

        Files.writeString(src, """
            func int inc(int x) {
                return x + 1;
            }

            var int a = inc(5);
            """, StandardCharsets.UTF_8);


        Proc r = runCli(dir, "build", src.toString());

        assertEquals(0, r.code, "exit code");
        assertTrue(r.out.contains("OK: wrote"), "stdout should contain success message");
        assertTrue(Files.exists(dir.resolve("hello.frogc")), "output .frogc must exist");
        assertTrue(r.err.isBlank(), "stderr should be empty on success");
    }

    @Test
    void build_missing_file_exit2() throws Exception {
        Path dir = Files.createTempDirectory("frogc-cli-");

        Proc r = runCli(dir, "build", "nope.frog");

        assertEquals(2, r.code);
        assertTrue(r.err.contains("io error: file not found"));
    }

    @Test
    void unknown_command_exit2_prints_usage() throws Exception {
        Path dir = Files.createTempDirectory("frogc-cli-");

        Proc r = runCli(dir, "wat");

        assertEquals(2, r.code);
        assertTrue(r.err.contains("Usage:"));
    }

    @Test
    void ast_ok_prints_something() throws Exception {
        Path dir = Files.createTempDirectory("frogc-cli-");
        Path src = dir.resolve("a.frog");
                Files.writeString(src, """
            func int inc(int x) {
                return x + 1;
            }

            var int a = inc(5);
            """, StandardCharsets.UTF_8);

        Proc r = runCli(dir, "ast", src.toString());

        assertEquals(0, r.code);
        assertFalse(r.out.isBlank(), "AST should be printed to stdout");
    }
}
