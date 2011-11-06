/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.util;

import groovy.lang.Closure;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.taskdefs.Tar;
import org.apache.tools.ant.taskdefs.Zip;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.DeleteAction;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.internal.file.copy.DeleteActionImpl;
import org.gradle.os.OperatingSystem;
import org.gradle.os.PosixUtil;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.DefaultExecAction;
import org.gradle.process.internal.ExecAction;
import org.hamcrest.Matcher;
import org.jruby.ext.posix.POSIX;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static org.junit.Assert.*;

public class TestFile extends File implements TestFileContext {
    private boolean useNativeTools;

    public TestFile(File file, Object... path) {
        super(join(file, path).getAbsolutePath());
    }

    public TestFile(URI uri) {
        this(new File(uri));
    }

    public TestFile(String path) {
        this(new File(path));
    }

    public TestFile(URL url) {
        this(toUri(url));
    }

    public TestFile getTestDir() {
        return this;
    }

    public TestFile usingNativeTools() {
        useNativeTools = true;
        return this;
    }

    private static URI toUri(URL url) {
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static File join(File file, Object[] path) {
        File current = file.getAbsoluteFile();
        for (Object p : path) {
            current = new File(current, p.toString());
        }
        return GFileUtils.canonicalise(current);
    }

    public TestFile file(Object... path) {
        try {
            return new TestFile(this, path);
        } catch (RuntimeException e) {
            throw new UncheckedIOException(String.format("Could not locate file '%s' relative to '%s'.", Arrays.toString(path), this), e);
        }
    }

    public List<TestFile> files(Object... paths) {
        List<TestFile> files = new ArrayList<TestFile>();
        for (Object path : paths) {
            files.add(file(path));
        }
        return files;
    }

    public TestFile writelns(String... lines) {
        return writelns(Arrays.asList(lines));
    }

    public TestFile write(Object content) {
        try {
            FileUtils.writeStringToFile(this, content.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not write to test file '%s'", this), e);
        }
        return this;
    }

    public TestFile leftShift(Object content) {
        getParentFile().mkdirs();
        try {
            DefaultGroovyMethods.leftShift(this, content);
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not append to test file '%s'", this), e);
        }
    }

    public String getText() {
        assertIsFile();
        try {
            return FileUtils.readFileToString(this);
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not read from test file '%s'", this), e);
        }
    }

    public Map<String, String> getProperties() {
        assertIsFile();
        Properties properties = new Properties();
        try {
            FileInputStream inStream = new FileInputStream(this);
            try {
                properties.load(inStream);
            } finally {
                inStream.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<String, String> map = new HashMap<String, String>();
        for (Object key : properties.keySet()) {
            map.put(key.toString(), properties.getProperty(key.toString()));
        }
        return map;
    }

    public Manifest getManifest() {
        assertIsFile();
        try {
            JarFile jarFile = new JarFile(this);
            try {
                return jarFile.getManifest();
            } finally {
                jarFile.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<String> linesThat(Matcher<? super String> matcher) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(this));
            try {
                List<String> lines = new ArrayList<String>();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (matcher.matches(line)) {
                        lines.add(line);
                    }
                }
                return lines;
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void unzipTo(File target) {
        assertIsFile();
        new TestFileHelper(this).unzipTo(target, useNativeTools);
    }

    public void untarTo(File target) {
        assertIsFile();

        new TestFileHelper(this).untarTo(target, useNativeTools);
    }

    public void copyTo(File target) {
        if (isDirectory()) {
            try {
                FileUtils.copyDirectory(this, target);
            } catch (IOException e) {
                throw new UncheckedIOException(String.format("Could not copy test directory '%s' to '%s'", this,
                        target), e);
            }
        } else {
            try {
                FileUtils.copyFile(this, target);
            } catch (IOException e) {
                throw new UncheckedIOException(String.format("Could not copy test file '%s' to '%s'", this, target), e);
            }
        }
    }

    public void copyFrom(File target) {
        new TestFile(target).copyTo(this);
    }

    public void copyFrom(URL resource) {
        try {
            FileUtils.copyURLToFile(resource, this);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public TestFile linkTo(File target) {
        return linkTo(target.getAbsolutePath());
    }

    public TestFile linkTo(String target) {
        getParentFile().createDir();
        POSIX posix = PosixUtil.current();
        int retval = posix.symlink(target, getAbsolutePath());
        if (retval != 0) {
            throw new UncheckedIOException(String.format("Could not create link from '%s' to '%s'. Errno: %s", this, target, posix.errno()));
        }
        return this;
    }

    public TestFile touch() {
        try {
            FileUtils.touch(this);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertIsFile();
        return this;
    }

    /**
     * Creates a directory structure specified by the given closure.
     * <pre>
     * dir.create {
     *     subdir1 {
     *        file 'somefile.txt'
     *     }
     *     subdir2 { nested { file 'someFile' } }
     * }
     * </pre>
     */
    public TestFile create(Closure structure) {
        assertTrue(isDirectory() || mkdirs());
        new TestDirHelper(this).apply(structure);
        return this;
    }

    @Override
    public TestFile getParentFile() {
        return super.getParentFile() == null ? null : new TestFile(super.getParentFile());
    }

    @Override
    public String toString() {
        return getPath();
    }

    public TestFile writelns(Iterable<String> lines) {
        Formatter formatter = new Formatter();
        for (String line : lines) {
            formatter.format("%s%n", line);
        }
        return write(formatter);
    }

    public TestFile assertExists() {
        assertTrue(String.format("%s does not exist", this), exists());
        return this;
    }

    public TestFile assertIsFile() {
        assertTrue(String.format("%s is not a file", this), isFile());
        return this;
    }

    public TestFile assertIsDir() {
        assertTrue(String.format("%s is not a directory", this), isDirectory());
        return this;
    }

    public TestFile assertDoesNotExist() {
        assertFalse(String.format("%s should not exist", this), exists());
        return this;
    }

    public TestFile assertContents(Matcher<String> matcher) {
        assertThat(getText(), matcher);
        return this;
    }

    public TestFile assertIsCopyOf(TestFile other) {
        assertIsFile();
        other.assertIsFile();
        assertEquals(String.format("%s is not the same length as %s", this, other), other.length(), this.length());
        assertTrue(String.format("%s does not have the same content as %s", this, other), Arrays.equals(HashUtil.createHash(this), HashUtil.createHash(other)));
        return this;
    }

    public TestFile assertPermissions(Matcher<String> matcher) {
        if (OperatingSystem.current().isUnix()) {
            assertThat(String.format("mismatched permissions for '%s'", this), getPermissions(), matcher);
        }
        return this;
    }

    public String getPermissions() {
        assertExists();
        return new TestFileHelper(this).getPermissions();
    }

    public TestFile setPermissions(String permissions) {
        assertExists();
        new TestFileHelper(this).setPermissions(permissions);
        return this;
    }

    /**
     * Asserts that this file contains exactly the given set of descendants.
     */
    public TestFile assertHasDescendants(String... descendants) {
        Set<String> actual = new TreeSet<String>();
        assertIsDir();
        visit(actual, "", this);
        Set<String> expected = new TreeSet<String>(Arrays.asList(descendants));

        Set<String> extras = new TreeSet<String>(actual);
        extras.removeAll(expected);
        Set<String> missing = new TreeSet<String>(expected);
        missing.removeAll(actual);

        assertEquals(String.format("For dir: %s, extra files: %s, missing files: %s, expected: %s", this, extras, missing, expected), expected, actual);

        return this;
    }

    public TestFile assertIsEmptyDir() {
        if (exists()) {
            assertIsDir();
            assertHasDescendants();
        }
        return this;
    }

    private void visit(Set<String> names, String prefix, File file) {
        for (File child : file.listFiles()) {
            if (child.isFile()) {
                names.add(prefix + child.getName());
            } else if (child.isDirectory()) {
                visit(names, prefix + child.getName() + "/", child);
            }
        }
    }

    public boolean isSelfOrDescendent(File file) {
        if (file.getAbsolutePath().equals(getAbsolutePath())) {
            return true;
        }
        return file.getAbsolutePath().startsWith(getAbsolutePath() + File.separatorChar);
    }

    public TestFile createDir() {
        assertTrue(isDirectory() || mkdirs());
        return this;
    }

    public TestFile createDir(Object path) {
        return new TestFile(this, path).createDir();
    }

    public TestFile deleteDir() {
        DeleteAction delete = new DeleteActionImpl(new IdentityFileResolver());
        delete.delete(this);
        return this;
    }

    /**
     * Attempts to delete this directory, ignoring failures to do so.
     * @return this
     */
    public TestFile maybeDeleteDir() {
        try {
            deleteDir();
        } catch (UncheckedIOException e) {
            // Ignore
        }
        return this;
    }

    public TestFile createFile() {
        new TestFile(getParentFile()).createDir();
        try {
            assertTrue(isFile() || createNewFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    public TestFile createFile(Object path) {
        return file(path).createFile();
    }

    public TestFile createZip(Object path) {
        Zip zip = new Zip();
        zip.setWhenempty((Zip.WhenEmpty) Zip.WhenEmpty.getInstance(Zip.WhenEmpty.class, "create"));
        TestFile zipFile = file(path);
        zip.setDestFile(zipFile);
        zip.setBasedir(this);
        zip.setExcludes("**");
        AntUtil.execute(zip);
        return zipFile;
    }

    public TestFile zipTo(TestFile zipFile) {
        Zip zip = new Zip();
        zip.setBasedir(this);
        zip.setDestFile(zipFile);
        AntUtil.execute(zip);
        return this;
    }

    public TestFile tarTo(TestFile zipFile) {
        Tar tar = new Tar();
        tar.setBasedir(this);
        tar.setDestFile(zipFile);
        AntUtil.execute(tar);
        return this;
    }

    public Snapshot snapshot() {
        assertIsFile();
        return new Snapshot();
    }

    public void assertHasChangedSince(Snapshot snapshot) {
        Snapshot now = snapshot();
        assertTrue(now.modTime != snapshot.modTime || !Arrays.equals(now.hash, snapshot.hash));
    }

    public void assertHasNotChangedSince(Snapshot snapshot) {
        Snapshot now = snapshot();
        assertEquals(String.format("last modified time of %s has changed", this), snapshot.modTime, now.modTime);
        assertArrayEquals(String.format("contents of %s has changed", this), snapshot.hash, now.hash);
    }

    public void writeProperties(Map<?, ?> properties) {
        Properties props = new Properties();
        props.putAll(properties);
        try {
            FileOutputStream stream = new FileOutputStream(this);
            try {
                props.store(stream, "comment");
            } finally {
                stream.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public class Snapshot {
        private final long modTime;
        private final byte[] hash;

        public Snapshot() {
            modTime = lastModified();
            hash = HashUtil.createHash(TestFile.this);
        }
    }

    public class TestFileExec {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final ByteArrayOutputStream err = new ByteArrayOutputStream();

        private final ExecAction action;

        public TestFileExec() {
            action = new DefaultExecAction();
            action.setErrorOutput(err);
            action.setStandardOutput(out);
            action.workingDir(TestFile.this.getParentFile());
            action.executable(TestFile.this);
        }

        public TestFileExec workingDir(File file) {
            action.workingDir(file);
            return this;
        }

        public TestFileExec args(Object... args) {
            action.args(args);
            return this;
        }

        public TestFileExec input(String input) {
            return input(new ByteArrayInputStream(input.getBytes()));
        }

        public TestFileExec input(String enc, String input) {
            try {
                return input(new ByteArrayInputStream(input.getBytes(enc)));
            } catch (java.io.UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
            
        }

        public TestFileExec input(InputStream input) {
            action.setStandardInput(input);
            return this;
        }

        public TestFileExecResult execute() {
            return new TestFileExecResult(action.execute(), out, err);
        }
    }

    public static class TestFileExecResult {
        private final ExecResult result;
        private final ByteArrayOutputStream out;
        private final ByteArrayOutputStream err;

        public TestFileExecResult(ExecResult result, ByteArrayOutputStream out, ByteArrayOutputStream err) {
            this.result = result;
            this.out = out;
            this.err = err;
        }

        public ExecResult getResult() {
            return result;
        }

        public String getOut() {
            return out.toString();
        }

        public String getErr() {
            return err.toString();
        }
    }

    TestFileExecResult exec(Object... args) {
        return exec(args, null);
    }

    TestFileExecResult exec(Object[] args, Closure configure) {
        TestFileExec exec = new TestFileExec().args(args);
        if (configure != null) {
            ConfigureUtil.configure(configure, exec);
        }
        return exec.execute();
    }
}
