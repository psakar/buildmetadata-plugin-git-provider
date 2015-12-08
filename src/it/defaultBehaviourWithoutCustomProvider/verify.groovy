import java.nio.file.Files

import static java.util.stream.Collectors.toList

File propertiesFile = new File( basedir, "target/classes/META-INF/build.properties" );

List<String> lines = Files.lines(propertiesFile.toPath()).collect(toList());
assert lines.contains("build.scmRevision.url=scm\\:git\\:http\\://127.0.0.1/my-project");
//assert lines.contains("build.scmRevision.url=scm\\:svn\\:https\\://smartics.info/svn/smartics/maven/buildmetadata-maven-plugin/trunk")
