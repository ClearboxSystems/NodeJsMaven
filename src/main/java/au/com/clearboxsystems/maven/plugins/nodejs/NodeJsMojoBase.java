package au.com.clearboxsystems.maven.plugins.nodejs;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *			http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.javascript.jscomp.CommandLineRunner;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.Os;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.Arg;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public abstract class NodeJsMojoBase extends AbstractMojo {

	public static class NodeInstallInformation {
		public URL url;
		public File archive;
		public File executable;
	}

	public static interface TaskFilter {
		boolean accept(Task t);
	}

	@Parameter
	protected String nodeJsURL;

	@Parameter(defaultValue = "0.8.19")
	protected String nodeJsVersion;

	@Parameter
	protected List<Task> tasks;

	@Parameter
	protected boolean stopOnError;

	/**
	 * Default location where nodejs will be extracted to and run from
	 */
	@Parameter(defaultValue = "${java.io.tmpdir}/nodejs")
	protected File nodeJsDirectory;

	protected static NodeInstallInformation getNodeInstallationInformation(String version, File directory) throws MojoExecutionException {
		String baseURL = "http://nodejs.org/dist/v" + version + "/";
		String basePath = directory.getAbsolutePath() + File.separator;
		String arch;
		if (Os.isArch("x86")) {
			arch = "x86";
		} else if (Os.isArch("x86_64") || Os.isArch("amd64")) {
			arch = "x64";
		} else {
			throw new MojoExecutionException("Unsupported OS arch: " + Os.OS_ARCH);
		}

		NodeInstallInformation result = new NodeInstallInformation();
		try {
			if (Os.isFamily(Os.FAMILY_WINDOWS) || Os.isFamily(Os.FAMILY_WIN9X)) {
				result.url = new URL(baseURL + "node.exe");
				result.archive = new File(basePath + "node-" + version + ".exe");
				result.executable = new File(basePath + "node-" + version + ".exe");
			} else if (Os.isFamily(Os.FAMILY_MAC)) {
				result.url = new URL(baseURL + "node-v" + version + "-darwin-" + arch + ".tar.gz");
				result.archive = new File(basePath + "node-v" + version + "-darwin-" + arch + ".tar.gz");
				result.executable = new File(basePath + "node-v" + version + "-darwin-" + arch + File.separator + "bin" + File.separator + "node");
			} else if (Os.isFamily(Os.FAMILY_UNIX)) {
				result.url = new URL(baseURL + "node-v" + version + "-linux-" + arch + ".tar.gz");
				result.archive = new File(basePath + "node-v" + version + "-linux-" + arch + ".tar.gz");
				result.executable = new File(basePath + "node-v" + version + "-linux-" + arch + File.separator + "bin" + File.separator + "node");
			} else {
				throw new MojoExecutionException("Unsupported OS: " + Os.OS_FAMILY);
			}
		} catch (java.net.MalformedURLException ex) {
			throw new MojoExecutionException("Malformed node URL", ex);
		}
		return result;
	}

	public NodeInstallInformation run() throws MojoExecutionException {
		return run(null);
	}

	public NodeInstallInformation run(TaskFilter filter) throws MojoExecutionException {
		if (tasks == null || tasks.isEmpty()) {
			getLog().warn("No NodeJSTasks have been defined. Nothing to do");
			return null;
		}

		NodeInstallInformation information = getNodeInstallationInformation(nodeJsVersion, nodeJsDirectory);
		try {
			if (nodeJsURL != null) {
				information.url = new URL(nodeJsURL);
			}
		} catch (java.net.MalformedURLException ex) {
			throw new MojoExecutionException("Malformed provided node URL", ex);
		}

		try {
			if (!information.executable.exists()) {
				getLog().info("Downloading Node JS from " + information.url);
				FileUtils.copyURLToFile(information.url, information.archive);
				if (information.archive.getName().endsWith(".tar.gz")) {
					Commandline commandLine = getCommandLine(nodeJsDirectory, "tar", "xf", information.archive.getName());
					executeCommandLine(commandLine);
				}
			}

			for (Task task : tasks) {
				if (filter == null || filter.accept(task)) {
					executeTask(task, information);
				}
			}
		} catch (IOException ex) {
			getLog().error("Failed to downloading nodeJs from " + nodeJsURL, ex);
			throw new MojoExecutionException("Failed to downloading nodeJs from " + nodeJsURL, ex);
		} catch (MojoExecutionException ex) {
			getLog().error("Execution Exception", ex);
			if (stopOnError)
				throw new MojoExecutionException("Execution Exception", ex);
		} catch (CommandLineException ex) {
			getLog().error("Command Line Exception", ex);
			throw new MojoExecutionException("Command execution failed.", ex);
		}

		return information;
	}

	protected void executeClosureCompiler(ClosureCompilerTask task) {
		getLog().info("Closure Compiler compiling: " + task.sourceFile.getName() + " with " + task.compilationLevel);
		ClosureCompilerRunner closureCompiler = buildClosureCompilerRunner(task);
		if (closureCompiler.shouldRunCompiler()) {
			closureCompiler.myRun();
		}
	}

	public ClosureCompilerRunner buildClosureCompilerRunner(ClosureCompilerTask task) {
		List<String> args = new ArrayList<>();

		args.add("--compilation_level");
		args.add(task.compilationLevel);

		args.add("--js");
		args.add(task.sourceFile.getAbsolutePath());

		addExternDirectory(task.externDirectory, args);
		if (task.externs != null) {
			for (File externPath : task.externs) {
				args.add("--externs");
				args.add(externPath.getAbsolutePath());
			}
		}

		args.add("--js_output_file");
		args.add(task.outputFile.getAbsolutePath());

		return new ClosureCompilerRunner(args.toArray(new String[args.size()]), task.outputFile);
	}

	protected void addExternDirectory(File externDirectory, List<String> args) {
		if (externDirectory != null) {
			for (File extern : externDirectory.listFiles()) {
				if (extern.isFile()) {
					args.add("--externs");
					args.add(extern.getAbsolutePath());
				} else if (extern.isDirectory()) {
					addExternDirectory(extern, args);
				}
			}
		}
	}

	public class ClosureCompilerRunner extends CommandLineRunner {
		private File outputFile;
		public ClosureCompilerRunner(String[] args, File outputFile) {
			super(args);
			this.outputFile = outputFile;
		}

		public void myRun() {
			try {
				doRun();
			} catch (Exception e) {
			} catch (Throwable t) {
			}
		}
	}

	/**
	 * Executes the given commandline
	 *
	 * @param commandLine
	 * @return
	 * @throws CommandLineException
	 */
	protected void executeCommandLine(Commandline commandLine)
			throws CommandLineException, MojoExecutionException {
		getLog().info("Executing command: " + commandLine.toString());
		CommandLineUtils.StringStreamConsumer systemErr = new CommandLineUtils.StringStreamConsumer();
		CommandLineUtils.StringStreamConsumer systemOut = new CommandLineUtils.StringStreamConsumer();

		int exitCode = CommandLineUtils.executeCommandLine(commandLine, systemOut, systemErr);
		String output = systemOut.getOutput().trim();
		if (!StringUtils.isEmpty(output)) {
			getLog().info("");
			for (String line : output.split("\n")) {
				getLog().info(line);
			}
			getLog().info("");
		}
		output = systemErr.getOutput().trim();
		if (!StringUtils.isEmpty(output)) {
			getLog().error("");
			for (String line : output.split("\n")) {
				getLog().error(line);
			}
			getLog().error("");
		}
		if (exitCode != 0) {
			throw new MojoExecutionException("Result of " + commandLine + " execution is: '" + exitCode + "'.");
		}
	}

	/**
	 * Create an CommandLine for the given executable
	 *
	 * @param workDir
	 * @param executable
	 * @param moduleName
	 * @param args
	 * @return
	 */
	protected Commandline getCommandLine(File workDir, String executable, String moduleName, String... args) {
		Commandline commandLine = new Commandline();
		commandLine.getShell().setQuotedExecutableEnabled(false);
		commandLine.getShell().setQuotedArgumentsEnabled(false);

		if (workDir != null) {
			if (!workDir.exists()) {
				workDir.mkdirs();
			}
			commandLine.setWorkingDirectory(workDir);
		}

		commandLine.setExecutable(executable);
		if (moduleName != null) {
			Arg arg = commandLine.createArg();
			arg.setValue(moduleName);
		}
		if (args != null) {
			commandLine.addArguments(args);

		}

		getLog().debug("commandLine = " + commandLine);

		return commandLine;
	}

	protected void executeTask(Task task, NodeInstallInformation information) throws CommandLineException, MojoExecutionException {
		if (task instanceof NodeJsTask) {
			NodeJsTask nodeJsTask = (NodeJsTask) task;
			Commandline commandLine = getCommandLine(nodeJsTask.workingDirectory, information.executable.getAbsolutePath(), nodeJsTask.name, nodeJsTask.arguments);
			executeCommandLine(commandLine);
		} else if (task instanceof ClosureCompilerTask) {
			ClosureCompilerTask closureCompilerTask = (ClosureCompilerTask) task;
			executeClosureCompiler(closureCompilerTask);
		} else {
			throw new MojoExecutionException("Unknown task type");
		}
	}
}
