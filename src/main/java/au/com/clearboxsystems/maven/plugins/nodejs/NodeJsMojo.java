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

@Mojo( name = "compile", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class NodeJsMojo extends AbstractMojo {

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
	protected File nodejsDirectory;

	private String nodeJsExecutable;

	private URL getNodeJsURL() throws MojoExecutionException {
		if (nodeJsURL == null || nodeJsURL.length() == 0) {

			String baseURL = "http://nodejs.org/dist/v" + nodeJsVersion + "/";

			if (Os.isFamily(Os.FAMILY_WINDOWS) || Os.isFamily(Os.FAMILY_WIN9X)) {
				nodeJsURL = baseURL + "node.exe";
      } else if (Os.isFamily(Os.FAMILY_MAC)) {
				if (Os.isArch("x86")) {
					nodeJsURL = baseURL + "node-v" + nodeJsVersion + "-darwin-x86.tar.gz";
				} else if (Os.isArch("x86_64") || Os.isArch("amd64")) {
					nodeJsURL = baseURL + "node-v" + nodeJsVersion + "-darwin-x64.tar.gz";
				} else {
					getLog().error("Unsupported OS Arch " + Os.OS_ARCH);
					throw new MojoExecutionException("Unsupported OS Arch " + Os.OS_ARCH);
				}
			} else if (Os.isFamily(Os.FAMILY_UNIX)) {
				if (Os.isArch("x86")) {
					nodeJsURL = baseURL + "node-v" + nodeJsVersion + "-linux-x86.tar.gz";
				} else if (Os.isArch("x86_64") || Os.isArch("amd64")) {
					nodeJsURL = baseURL + "node-v" + nodeJsVersion + "-linux-x64.tar.gz";
				} else {
					getLog().error("Unsupported OS Arch " + Os.OS_ARCH);
					throw new MojoExecutionException("Unsupported OS Arch " + Os.OS_ARCH);
				}
			} else {
				getLog().error("Unsupported OS Family " + Os.OS_FAMILY);
				throw new MojoExecutionException("Unsupported OS Family " + Os.OS_FAMILY);
			}
		}

		try {
			return new URL(nodeJsURL);
		} catch (MalformedURLException ex) {
			getLog().error("Malformed URL: " + nodeJsURL, ex);
			throw new MojoExecutionException("Malformed URL: " + nodeJsURL, ex);
		}
	}

	private String getNodeJsFilePath() throws MojoExecutionException {
		String basePath = nodejsDirectory.getAbsolutePath() + File.separator;

		if (Os.isFamily(Os.FAMILY_WINDOWS) || Os.isFamily(Os.FAMILY_WIN9X)) {
			return basePath + "node-" + nodeJsVersion + ".exe";
		} else if (Os.isFamily(Os.FAMILY_MAC)) {
			if (Os.isArch("x86")) {
				return basePath + "node-v" + nodeJsVersion + "-darwin-x86.tar.gz";
			} else if (Os.isArch("x86_64") || Os.isArch("amd64")) {
				return basePath + "node-v" + nodeJsVersion + "-darwin-x64.tar.gz";
			} else {
				getLog().error("Unsupported OS Arch " + Os.OS_ARCH);
				throw new MojoExecutionException("Unsupported OS Arch " + Os.OS_ARCH);
			}
		} else if (Os.isFamily(Os.FAMILY_UNIX)) {
			if (Os.isArch("x86")) {
				return basePath + "node-v" + nodeJsVersion + "-linux-x86.tar.gz";
			} else if (Os.isArch("x86_64") || Os.isArch("amd64")) {
				return basePath + "node-v" + nodeJsVersion + "-linux-x64.tar.gz";
			} else {
				getLog().error("Unsupported OS Arch " + Os.OS_ARCH);
				throw new MojoExecutionException("Unsupported OS Arch " + Os.OS_ARCH);
			}
		} else {
			getLog().error("Unsupported OS Family " + Os.OS_FAMILY);
			throw new MojoExecutionException("Unsupported OS Family " + Os.OS_FAMILY);
		}
	}

	private String getNodeJsExecutable() throws MojoExecutionException {
		String basePath = nodejsDirectory.getAbsolutePath() + File.separator;

		if (Os.isFamily(Os.FAMILY_WINDOWS) || Os.isFamily(Os.FAMILY_WIN9X)) {
			return basePath + "node-" + nodeJsVersion + ".exe";
		} else if (Os.isFamily(Os.FAMILY_MAC)) {
			return basePath + "node-v" + nodeJsVersion + "-darwin-x" + (Os.OS_ARCH.equals("x86") ? "86" : "64") + File.separator + "bin" + File.separator + "node";
		} else if (Os.isFamily(Os.FAMILY_UNIX)) {
			return basePath + "node-v" + nodeJsVersion + "-linux-x" + (Os.OS_ARCH.equals("x86") ? "86" : "64") + File.separator + "bin" + File.separator + "node";
		} else {
			getLog().error("Unsupported OS Family " + Os.OS_FAMILY);
			throw new MojoExecutionException("Unsupported OS Family " + Os.OS_FAMILY);
		}
	}

	public void execute() throws MojoExecutionException {
		if (tasks == null || tasks.isEmpty()) {
			getLog().warn("No NodeJSTasks have been defined. Nothing to do");
			return;
		}

		nodeJsExecutable = getNodeJsExecutable();
		try {
			if (!FileUtils.fileExists(nodeJsExecutable)) {
				getLog().info("Downloading Node JS from " + getNodeJsURL());
				FileUtils.copyURLToFile(getNodeJsURL(), new File(getNodeJsFilePath()));
				if (Os.isFamily(Os.FAMILY_MAC)) { // Unpack tar
					String tarName = "node-v" + nodeJsVersion + "-darwin-x" + (Os.OS_ARCH.equals("x86") ? "86" : "64") + ".tar.gz";

					Commandline commandLine = getCommandLine(nodejsDirectory, "tar", "xf", tarName);
					executeCommandLine(commandLine);
				} else if (Os.isFamily(Os.FAMILY_UNIX)) { // Unpack tar
					String tarName = "node-v" + nodeJsVersion + "-linux-x" + (Os.OS_ARCH.equals("x86") ? "86" : "64") + ".tar.gz";

					Commandline commandLine = getCommandLine(nodejsDirectory, "tar", "xf", tarName);
					executeCommandLine(commandLine);
				}
			}

			for (Task task : tasks) {
				executeTask(task);
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

	private void addExternDirectory(File externDirectory, List<String> args) {
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
		String output = StringUtils.isEmpty(systemOut.getOutput()) ? null : '\n' + systemOut.getOutput().trim();
		if (StringUtils.isNotEmpty(output)) {
			getLog().info(output);
		}
		if (exitCode != 0) {
			output = StringUtils.isEmpty(systemErr.getOutput()) ? null : '\n' + systemErr.getOutput().trim();
			if (StringUtils.isNotEmpty(output)) {
				getLog().error(output);
			}
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

	protected void executeTask(Task task) throws CommandLineException, MojoExecutionException {
		if (task instanceof NodeJsTask) {
			NodeJsTask nodeJsTask = (NodeJsTask) task;
			Commandline commandLine = getCommandLine(nodeJsTask.workingDirectory, nodeJsExecutable, nodeJsTask.name, nodeJsTask.arguments);
			executeCommandLine(commandLine);
		} else if (task instanceof ClosureCompilerTask) {
			ClosureCompilerTask closureCompilerTask = (ClosureCompilerTask) task;
			executeClosureCompiler(closureCompilerTask);
		}
	}
}
