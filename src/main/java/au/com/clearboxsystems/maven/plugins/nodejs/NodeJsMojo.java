package au.com.clearboxsystems.maven.plugins.nodejs;

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
import java.util.List;

/**
 * Goal which touches a timestamp file.
 *
 */
@Mojo( name = "compile", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class NodeJsMojo extends AbstractMojo {

	@Parameter
	protected String nodeJsURL;

	@Parameter(defaultValue = "0.8.19")
	protected String nodeJsVersion;

	@Parameter
	protected List<NodeJsTask> nodeJsTasks;

	/**
	 * Default location where nodejs will be extracted to and run from
	 */
	@Parameter(defaultValue = "${java.io.tmpdir}/nodejs")
	protected File nodejsDirectory;

	private URL getNodeJsURL() throws MojoExecutionException {
		if (nodeJsURL == null || nodeJsURL.length() == 0) {

			String baseURL = "http://nodejs.org/dist/v" + nodeJsVersion + "/";

			if (Os.isFamily(Os.FAMILY_WINDOWS) || Os.isFamily(Os.FAMILY_WIN9X)) {
				nodeJsURL = baseURL + "node.exe";
			} else if (Os.isFamily(Os.FAMILY_UNIX)) {
				if (Os.isArch("x86")) {
					nodeJsURL = baseURL + "node-v" + nodeJsVersion + "-linux-x86.tar.gz";
				} else if (Os.isArch("x64")) {
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
		} else if (Os.isFamily(Os.FAMILY_UNIX)) {
			if (Os.isArch("x86")) {
				return basePath + "node-v" + nodeJsVersion + "-linux-x86.tar.gz";
			} else if (Os.isArch("x64")) {
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
		} else if (Os.isFamily(Os.FAMILY_UNIX)) {
			return basePath + "node-v" + nodeJsVersion + "-linux-" + Os.OS_ARCH + File.separator + "bin" + File.separator + "node";
		} else {
			getLog().error("Unsupported OS Family " + Os.OS_FAMILY);
			throw new MojoExecutionException("Unsupported OS Family " + Os.OS_FAMILY);
		}
	}

	public void execute() throws MojoExecutionException {
		if (nodeJsTasks == null || nodeJsTasks.isEmpty()) {
			getLog().warn("No NodeJSTasks have been defined. Nothing to do");
			return;
		}

		try {
			String nodeJsExecutable = getNodeJsExecutable();
			if (!FileUtils.fileExists(nodeJsExecutable)) {
				getLog().info("Downloading Node JS from " + getNodeJsURL());
				FileUtils.copyURLToFile(getNodeJsURL(), new File(getNodeJsFilePath()));
				if (Os.isFamily(Os.FAMILY_UNIX)) { // Unpack tar
					String tarName = "node-v" + nodeJsVersion + "-linux-x" + Os.OS_ARCH + ".tar.gz";

					Commandline commandLine = getCommandLine(nodejsDirectory, "tar", "xf", tarName);
					executeCommandLine(commandLine);
				}
			}

			executeNodeJs(nodeJsExecutable);
		} catch (IOException ex) {
			getLog().error("Failed to downloading nodeJs from " + nodeJsURL, ex);
			throw new MojoExecutionException("Failed to downloading nodeJs from " + nodeJsURL, ex);
		} catch (MojoExecutionException ex) {
			getLog().error("Execution Exception", ex);
			throw new MojoExecutionException("Execution Exception", ex);
		} catch (CommandLineException ex) {
			getLog().error("Command Line Exception", ex);
			throw new MojoExecutionException("Command execution failed.", ex);
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

	protected void executeNodeJs(String executable) throws MojoExecutionException, CommandLineException {
		for (NodeJsTask task : nodeJsTasks) {
			Commandline commandLine = getCommandLine(task.workingDirectory, executable, task.name, task.arguments);
			executeCommandLine(commandLine);
		}
	}

}
