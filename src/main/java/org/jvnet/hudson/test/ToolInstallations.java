/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jvnet.hudson.test;

import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.plugins.gradle.Gradle;
import hudson.plugins.gradle.GradleInstallation;
import hudson.tasks.Ant;
import hudson.tasks.Maven;
import hudson.util.StreamTaskListener;
import hudson.util.jna.GNUCLibrary;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.junit.rules.TemporaryFolder;

/**
 * Utility to install standard tools in the Jenkins under test.
 */
public class ToolInstallations {

    private static final Logger LOGGER = Logger.getLogger(ToolInstallations.class.getName());

    /**
     * Returns the older default Maven, while still allowing specification of
     * other bundled Mavens.
     */
    public static Maven.MavenInstallation configureDefaultMaven() throws Exception {
        return configureDefaultMaven("apache-maven-2.2.1", Maven.MavenInstallation.MAVEN_20);
    }

    public static Maven.MavenInstallation configureMaven3() throws Exception {
        Maven.MavenInstallation mvn = configureDefaultMaven("apache-maven-3.0.1", Maven.MavenInstallation.MAVEN_30);

        Maven.MavenInstallation m3 = new Maven.MavenInstallation("apache-maven-3.0.1", mvn.getHome(), JenkinsRule.NO_PROPERTIES);
        Jenkins.getInstance().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(m3);
        return m3;
    }

    /**
     * Declare "Maven 3.5.0" as the "default" Maven installation in Jenkins and as the Maven installation named "apache-maven-3.5.0".
     * Note that both {@link hudson.tasks.Maven.MavenInstallation} share the same Maven binaries.
     *
     * @return the "apache-maven-3.5.0" Maven {@link hudson.tasks.Maven.MavenInstallation}
     * @throws Exception
     */
    public static Maven.MavenInstallation configureMaven35() throws Exception {
        Maven.MavenInstallation mvn = configureDefaultMaven("apache-maven-3.5.0", Maven.MavenInstallation.MAVEN_30);

        Maven.MavenInstallation maven350 = new Maven.MavenInstallation("apache-maven-3.5.0", mvn.getHome(), JenkinsRule.NO_PROPERTIES);
        Jenkins.getInstance().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(maven350);
        return maven350;
    }


    /**
     * Locates Maven and configure that as the only Maven in the system.
     *
     * @param mavenVersion desired maven version (e.g. {@code apache-maven-3.5.0})
     * @param mavenReqVersion minimum maven version defined using the constants {@link Maven.MavenInstallation#MAVEN_20},
     *    {@link Maven.MavenInstallation#MAVEN_21} and {@link Maven.MavenInstallation#MAVEN_30}
     */
    public static Maven.MavenInstallation configureDefaultMaven(String mavenVersion, int mavenReqVersion) throws Exception {
        // first if we are running inside Maven, pick that Maven, if it meets the criteria we require..
        File buildDirectory = new File(System.getProperty("buildDirectory", "target")); // TODO relative path
        File mvnHome = new File(buildDirectory, mavenVersion);
        if (mvnHome.exists()) {
            Maven.MavenInstallation mavenInstallation = new Maven.MavenInstallation("default", mvnHome.getAbsolutePath(), JenkinsRule.NO_PROPERTIES);
            Jenkins.getInstance().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation);
            return mavenInstallation;
        }

        // Does maven.home point to a Maven installation which satisfies mavenReqVersion?
        String home = System.getProperty("maven.home");
        if (home != null) {
            Maven.MavenInstallation mavenInstallation = new Maven.MavenInstallation("default", home, JenkinsRule.NO_PROPERTIES);
            if (mavenInstallation.meetsMavenReqVersion(new Launcher.LocalLauncher(StreamTaskListener.fromStdout()), mavenReqVersion)) {
                Jenkins.getInstance().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation);
                return mavenInstallation;
            }
        }

        // otherwise extract the copy we have.
        // this happens when a test is invoked from an IDE, for example.
        LOGGER.log(Level.WARNING,"Extracting a copy of Maven bundled in the test harness into {0}. "
                + "To avoid a performance hit, set the system property ''maven.home'' to point to a Maven2 installation.", mvnHome);
        FilePath mvn = Jenkins.getInstance().getRootPath().createTempFile("maven", "zip");
        mvn.copyFrom(JenkinsRule.class.getClassLoader().getResource(mavenVersion + "-bin.zip"));
        mvn.unzip(new FilePath(buildDirectory));
        // TODO: switch to tar that preserves file permissions more easily
        try {
            GNUCLibrary.LIBC.chmod(new File(mvnHome, "bin/mvn").getPath(), 0755);
        } catch (LinkageError x) {
            // skip; TODO 1.630+ can use Functions.isGlibcSupported
        }

        Maven.MavenInstallation mavenInstallation = new Maven.MavenInstallation("default",
                mvnHome.getAbsolutePath(), JenkinsRule.NO_PROPERTIES);
        Jenkins.getInstance().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation);
        return mavenInstallation;
    }

    /**
     * Extracts Ant and configures it.
     */
    public static Ant.AntInstallation configureDefaultAnt(TemporaryFolder tmp) throws Exception {
        Ant.AntInstallation antInstallation;
        if (System.getenv("ANT_HOME") != null) {
            antInstallation = new Ant.AntInstallation("default", System.getenv("ANT_HOME"), JenkinsRule.NO_PROPERTIES);
        } else {
            LOGGER.warning("Extracting a copy of Ant bundled in the test harness. "
                    + "To avoid a performance hit, set the environment variable ANT_HOME to point to an  Ant installation.");
            FilePath ant = Jenkins.getInstance().getRootPath().createTempFile("ant", "zip");
            ant.copyFrom(JenkinsRule.class.getClassLoader().getResource("apache-ant-1.8.1-bin.zip"));
            File antHome = tmp.newFolder("antHome");
            ant.unzip(new FilePath(antHome));
            // TODO: switch to tar that preserves file permissions more easily
            try {
                GNUCLibrary.LIBC.chmod(new File(antHome, "apache-ant-1.8.1/bin/ant").getPath(), 0755);
            } catch (LinkageError x) {
                // skip; TODO 1.630+ can use Functions.isGlibcSupported
            }

            antInstallation = new Ant.AntInstallation("default", new File(antHome, "apache-ant-1.8.1").getAbsolutePath(), JenkinsRule.NO_PROPERTIES);
        }
        Jenkins.getInstance().getDescriptorByType(Ant.DescriptorImpl.class).setInstallations(antInstallation);
        return antInstallation;
    }

    /**
     * Extracts Gradle and configures it.
     */
    public static GradleInstallation configureDefaultGradle(TemporaryFolder tmp) throws Exception {
        GradleInstallation installation;
        if (System.getenv("GRADLE_HOME") != null) {
            installation = new GradleInstallation("default", System.getenv("GRADLE_HOME"), JenkinsRule.NO_PROPERTIES);
        } else {
            LOGGER.warning("Extracting a copy of Gradle bundled in the test harness. "
                    + "To avoid a performance hit, set the environment variable GRADLE_HOME to point to a Gradle installation.");
            FilePath gradle = Jenkins.getInstance().getRootPath().createTempFile("gradle", "zip");
            gradle.copyFrom(JenkinsRule.class.getClassLoader().getResource("gradle-2.13-bin.zip"));
            File gradleHome = tmp.newFolder("gradleHome");
            gradle.unzip(new FilePath(gradleHome));
            // TODO: switch to tar that preserves file permissions more easily
            try {
                GNUCLibrary.LIBC.chmod(new File(gradleHome, "gradle-2.13/bin/gradle").getPath(), 0755);
            } catch (LinkageError x) {
                // skip; TODO 1.630+ can use Functions.isGlibcSupported
            }

            installation = new GradleInstallation("default", new File(gradleHome, "gradle-2.13").getAbsolutePath(), JenkinsRule.NO_PROPERTIES);
        }
        Jenkins.getInstance().getDescriptorByType(Gradle.DescriptorImpl.class).setInstallations(installation);
        return installation;
    }

    private ToolInstallations() {
    }

}
