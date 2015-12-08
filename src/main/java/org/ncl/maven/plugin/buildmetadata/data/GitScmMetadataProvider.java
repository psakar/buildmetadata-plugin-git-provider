package org.ncl.maven.plugin.buildmetadata.data;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import de.smartics.maven.plugin.buildmetadata.common.RevisionHelper;
import de.smartics.maven.plugin.buildmetadata.common.ScmControl;
import de.smartics.maven.plugin.buildmetadata.common.ScmCredentials;
import de.smartics.maven.plugin.buildmetadata.data.AbstractMetaDataProvider;
import de.smartics.maven.plugin.buildmetadata.scm.maven.ScmAccessInfo;
import de.smartics.maven.plugin.buildmetadata.scm.maven.ScmConnectionInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.scm.manager.NoSuchScmProviderException;
import org.apache.maven.scm.provider.ScmProviderRepositoryWithHost;
import org.apache.maven.scm.repository.ScmRepository;
import org.apache.maven.scm.repository.ScmRepositoryException;
import org.codehaus.plexus.util.StringUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import static java.util.Arrays.asList;

public class GitScmMetadataProvider extends AbstractMetaDataProvider
{
    private static final Log LOG = LogFactory.getLog(GitScmMetadataProvider.class);
    private final ScmConnectionLookup scmConnectionLookup;


    private boolean readLocalGitRepositoryInfo = false;

    private boolean removeDomainFromScmConnectionHostname = false;

    public GitScmMetadataProvider() {
        this(new GitScmConnectionLookup());
    }

    GitScmMetadataProvider(ScmConnectionLookup connectionLookup) {
        this.scmConnectionLookup = connectionLookup;
    }

    public void provideBuildMetaData(Properties buildMetaDataProperties) throws MojoExecutionException {
        if (!readLocalGitRepositoryInfo) {
            return;
        }
        ScmControl scmControl = this.scmInfo.getScmControl();
        if(scmControl.isAddScmInfo() && !scmControl.isOffline() && this.project.getScm() != null) {
            try {
                ScmConnectionInfo e = this.loadConnectionInfo();
                ScmAccessInfo scmAccessInfo = this.createScmAccessInfo();
                RevisionHelper helper = new RevisionHelper(this.scmInfo.getScmManager(), e, scmAccessInfo, this.scmInfo.getBuildDatePattern());
                helper.provideScmBuildInfo(buildMetaDataProperties, scmControl);
            } catch (ScmRepositoryException var6) {
                throw new IllegalStateException("Cannot fetch SCM revision information.", var6);
            } catch (NoSuchScmProviderException var7) {
                throw new IllegalStateException("Cannot fetch SCM revision information.", var7);
            }
        } else {
            LOG.debug("Skipping SCM data since addScmInfo=" + scmControl.isAddScmInfo() + ", offline=" + scmControl.isOffline() + ", scmInfoProvided=" + (this.project.getScm() != null) + ".");
        }

    }

    private ScmConnectionInfo loadConnectionInfo() throws IllegalStateException, ScmRepositoryException, NoSuchScmProviderException {
        String scmConnection = this.getConnection();
        ScmCredentials credentials = this.scmInfo.getScmCrendentials();
        if(credentials.getUserName() == null || credentials.getPassword() == null) {
            ScmRepository info = this.scmInfo.getScmManager().makeScmRepository(scmConnection);
            if(info.getProviderRepository() instanceof ScmProviderRepositoryWithHost) {
                ScmProviderRepositoryWithHost repositoryWithHost = (ScmProviderRepositoryWithHost)info.getProviderRepository();
                String host = this.createHostName(repositoryWithHost);
                credentials.configureByServer(host);
            }
        }

        ScmConnectionInfo info = new ScmConnectionInfo();
        info.setUserName(credentials.getUserName());
        info.setPassword(credentials.getPassword());
        info.setPrivateKey(credentials.getPrivateKey());
        info.setScmConnectionUrl(scmConnection);
        info.setTagBase(this.scmInfo.getTagBase());
        info.setRemoteVersion(this.scmInfo.getRemoteVersion());
        return info;
    }

    protected final String getConnection() throws IllegalStateException {
        String scmConnection = scmConnectionLookup.getConnection(new File("."));
        if (scmConnection != null && isRemoveDomainFromScmConnectionHostname()) {
            String hostname = extractHostnameFromScmConnection(scmConnection);
            System.err.println("hostname " + hostname);
            String hostnameWithoutDomain = stripDomainName(hostname);
            System.err.println("hostnameWithoutDomain " + hostnameWithoutDomain);
            scmConnection = scmConnection.replace(hostname, hostnameWithoutDomain);
        }
        return scmConnection;
    }

    protected final String getProjectScmConnection() {
        if(this.project.getScm() == null) {
            return null;
        }

        String scmConnection = this.project.getScm().getConnection();
        String connectionType = this.scmInfo.getConnectionType();
        if(StringUtils.isNotEmpty(scmConnection) && "connection".equals(connectionType.toLowerCase(Locale.ENGLISH))) {
            return scmConnection;
        } else {
            String scmDeveloper = this.project.getScm().getDeveloperConnection();
            if(StringUtils.isNotEmpty(scmDeveloper) && "developerconnection".equals(connectionType.toLowerCase(Locale.ENGLISH))) {
                return scmDeveloper;
            } else {
                throw new IllegalStateException("SCM Connection is not set.");
            }
        }

    }

    private String createHostName(ScmProviderRepositoryWithHost repositoryWithHost) {
        String host = repositoryWithHost.getHost();
        int port = repositoryWithHost.getPort();
        return port > 0?host + ":" + port:host;
    }


    private ScmAccessInfo createScmAccessInfo() {
        ScmAccessInfo accessInfo = new ScmAccessInfo();
        accessInfo.setDateFormat(this.scmInfo.getScmDateFormat());
        accessInfo.setRootDirectory(this.scmInfo.getBasedir());
        accessInfo.setFailOnLocalModifications(this.scmInfo.getScmControl().isFailOnLocalModifications());
        accessInfo.setIgnoreDotFilesInBaseDir(this.scmInfo.getScmControl().isIgnoreDotFilesInBaseDir());
        accessInfo.setQueryRangeInDays(this.scmInfo.getQueryRangeInDays());
        return accessInfo;
    }

    public boolean isRemoveDomainFromScmConnectionHostname() {
        return removeDomainFromScmConnectionHostname;
    }

    static interface ScmConnectionLookup {
        public String getConnection(File localGitRepoFolder);
    }

    static class GitScmConnectionLookup implements ScmConnectionLookup {

        private final Log logger = LogFactory.getLog(getClass());

        private final String remoteName = "origin";

        public String getConnection(File localGitRepoFolder) {
            logger.debug("Find connection url of local git repo " + localGitRepoFolder.getAbsolutePath());
            String connection = null;
            File gitConfigFile = new File(new File(localGitRepoFolder, ".git"), "config");
            try {
                List<String> lines = readTextFileToList(gitConfigFile);
                int startOfRemoteSection = findIndexOfLineStartingWith(lines,
                        "[remote \"" + remoteName + "\"]");
                if (startOfRemoteSection < 0) {
                    throw new IllegalArgumentException("Remote " + remoteName + " was not found in " + gitConfigFile.getAbsolutePath());
                }
                if (startOfRemoteSection + 1 >= lines.size()) {
                    throw new IllegalArgumentException("Url of remote " + remoteName + " was not found in " + gitConfigFile.getAbsolutePath());
                }
                String lineWithUrl = lines.get(startOfRemoteSection + 1);
                int index = lineWithUrl.indexOf("url = ");
                if (index < 0 || lineWithUrl.length() <= index + 6) {
                    throw new IllegalArgumentException("Url of remote " + remoteName + " was not found in " + gitConfigFile.getAbsolutePath());
                }
                connection = lineWithUrl.substring(index + 6).trim();
                if (connection.length() == 0) {
                    throw new IllegalArgumentException("Url of remote " + remoteName + " was not specified in " + gitConfigFile.getAbsolutePath());
                }
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        "Can not read url of remote " + remoteName + " from git config " + gitConfigFile.getAbsolutePath() + " - " + e.getMessage(), e);
            }
            return connection == null ? null : "scm:git:" + connection;
        }

        /* inlined from commons-io FileUtils.readLines */
        private List<String> readTextFileToList(File file) throws IOException {
            InputStream in = null;
            try {
                if (file.exists()) {
                    if (file.isDirectory()) {
                        throw new IOException(
                                "File '" + file.getAbsolutePath() + "' exists but is a directory");
                    }
                    if (file.canRead() == false) {
                        throw new IOException(
                                "File '" + file.getAbsolutePath() + "' cannot be read");
                    }
                } else {
                    throw new FileNotFoundException(
                            "File '" + file.getAbsolutePath() + "' does not exist");
                }
                in = new FileInputStream(file);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(in, Charset.forName("utf-8")));
                List<String> list = new ArrayList<String>();
                String line = reader.readLine();
                while (line != null) {
                    list.add(line);
                    line = reader.readLine();
                }
                return list;
            } finally {
                Closeable closeable = (Closeable) in;
                try {
                    if (closeable != null) {
                        closeable.close();
                    }
                } catch (IOException ioe) {
                    // ignore
                }
            }
        }

        private int findIndexOfLineStartingWith(List<String> lines, String string) {
            int index = 0;
            for (String line : lines) {
                if (line != null && line.startsWith(string)) {
                    return index;
                }
                index++;
            }
            return -1;
        }
    }


    // @VisibleForTesting
    String stripDomainName(String hostname) {
        if (isBlank(hostname)) {
            return null;
        }
        int index = hostname.indexOf(".");
        String beforeDot = index < 0 ? hostname : hostname.substring(0, index);
        return isBlank(beforeDot) ? null : beforeDot.trim();
    }

    // inlined commons StringUtils.isBlank
    private boolean isBlank(String string) {
        int strLen;
        if (string == null || (strLen = string.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(string.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    static final String SCM_GIT = "scm:git:";

    // @VisibleForTesting
    String extractHostnameFromScmConnection(String scmConnection) {
        if (isBlank(scmConnection)) {
            return null;
        }
        if (!scmConnection.startsWith(SCM_GIT)) {
            return null;
        }
        scmConnection = scmConnection.substring(SCM_GIT.length());
        List<String> prefixes = asList("git://", "http://", "https://");
        for (String prefix : prefixes) {
            if (scmConnection.startsWith(prefix)) {
                return stripTypeAndAfterHostname(scmConnection, prefix);
            }
        }

        int index = scmConnection.indexOf(":");
        if (index > 0) {
            String string = scmConnection.substring(0, index);
            index = string.indexOf("@");
            return index >= 0 && index + 1 < string.length() ? string.substring(index + 1) : string;
        }

        return null;
    }

    private String stripTypeAndAfterHostname(String scmConnection, String prefix) {
        String withoutType = scmConnection.substring(prefix.length());
        int index = withoutType.indexOf("/");
        return index < 0 ? withoutType : withoutType.substring(0, index);
    }

}
