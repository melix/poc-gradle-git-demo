package poc.git;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates a Git info class. Note that you probably don't
 * want to call this every time during development, since every
 * time you commit, this would generate a new class which would
 * invalidate caches. This means you probably want to do something
 * smarter which uses a dummy sha for development, and Git on CI.
 */
public abstract class GenerateGitInfo extends DefaultTask {
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Input
    public abstract Property<String> getClassName();

    @Input
    public Provider<String> getSha() {
        return executeGitCommand("git", "rev-parse", "HEAD");
    }

    @Input
    public Provider<String> getBranch() {
        return executeGitCommand("git", "rev-parse", "--abbrev-ref", "HEAD");
    }

    /**
     * Returns a provider which executes somme command line
     * @param args the command line to execute
     * @return the provider which returns the output of the command
     */
    private Provider<String> executeGitCommand(String... args) {
        return getProject().getProviders().provider( () -> {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ExecResult result = getExecOperations().exec(spec -> {
                spec.commandLine((Object[]) args);
                spec.setStandardOutput(bos);
            });
            if (result.getExitValue() == 0) {
                return bos.toString(StandardCharsets.UTF_8).trim();
            }
            return null;
        });
    }

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void generate() throws IOException {
        Path outputPath = getOutputDirectory().get().getAsFile().toPath();
        String fqn = getClassName().get();
        Path sourcePath = outputPath.resolve(fqn.replace('.', '/') + ".java");
        Files.createDirectories(sourcePath.getParent());
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(sourcePath))) {
            writer.println("package " + fqn.substring(0, fqn.lastIndexOf('.')) + ";");
            writer.println();
            writer.println("public abstract class " + fqn.substring(fqn.lastIndexOf('.') + 1) + " {");
            writer.println("    public static final String SHA = \"" + getSha().get() + "\";");
            writer.println("    public static final String BRANCH = \"" + getBranch().get() + "\";");
            writer.println("}");
        }
    }
}
