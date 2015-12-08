import org.apache.commons.io.FileUtils

File gitTemplateFolder = new File( basedir, "../../../src/test/files/git1" );
File gitFolder = new File( basedir, ".git");

if (gitFolder.exists()) {
    FileUtils.cleanDirectory(gitFolder);
}
FileUtils.copyDirectory(gitTemplateFolder, gitFolder);
