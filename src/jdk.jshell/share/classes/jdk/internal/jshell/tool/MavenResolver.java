package jdk.internal.jshell.tool;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MavenResolver {
    final Path maven;
    final Path mavenRepo;

    public MavenResolver(String mavenHome, String mavenRepo) {
        this.maven = mavenHome == null ? Path.of("mvn") : Path.of(mavenHome, "bin", "mvn");
        this.mavenRepo = mavenRepo == null ? Path.of(System.getProperty("user.home"), ".m2") : Path.of(mavenRepo);
    }

    public MavenResolver() {
        this(System.getenv("MAVEN_HOME"), System.getenv("MAVEN_REPO"));
    }

    public List<String> resolveToClasspath(String artifact) throws IOException {
        dependencyGet(artifact);

        Path cpPath = Files.createTempFile("maven-cp", null);
        Process s = Runtime.getRuntime().exec(new String[]{
                maven.toString(),
                "-f",
                toPomFile(artifact).toString(),
                "dependency:build-classpath",
                "-Dmdep.outputFile=" + cpPath.toAbsolutePath().toString(),
        });

        s.toHandle().onExit().join();
        int ev = s.exitValue();
        if (ev != 0) {
            System.out.println(new String(s.getErrorStream().readAllBytes()));
            System.out.println(new String(s.getInputStream().readAllBytes()));
            throw new IOException("Failed");
        }

        List<String> jars = new ArrayList<>(List.of(Files.readString(cpPath).split(":")));
        jars.add(toJarFile(artifact).toString());
        return jars;
    }

    void dependencyGet(String artifact) throws IOException {
        Process s = Runtime.getRuntime().exec(new String[]{
                maven.toString(),
                "dependency:get",
                "-Dartifact=" + artifact,
        });

        s.toHandle().onExit().join();
        int ev = s.exitValue();
        if (ev != 0) {
            System.out.println(new String(s.getErrorStream().readAllBytes()));
            System.out.println(new String(s.getInputStream().readAllBytes()));
            throw new IOException("Failed");
        }
    }

    static final String SEP = FileSystems.getDefault().getSeparator();

    Path toPomFile(String artifact) {
        String[] components = artifact.split(":");
        String groupId = components[0];
        String artifactId = components[1];
        String version = components[2];

        return mavenRepo.resolve(Path.of("repository",
                groupId.replace(".", SEP),
                artifactId,
                version,
                artifactId + "-" + version + ".pom"));
    }

    Path toJarFile(String artifact) {
        String[] components = artifact.split(":");
        String groupId = components[0];
        String artifactId = components[1];
        String version = components[2];

        return mavenRepo.resolve(Path.of("repository",
                groupId.replace(".", SEP),
                artifactId,
                version,
                artifactId + "-" + version + ".jar"));
    }
}
