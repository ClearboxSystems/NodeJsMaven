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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.Os;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.Arg;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

/**
 * Goal which touches a timestamp file.
 *
 */
@Mojo( name = "compile", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class NodeJsMojo extends AbstractMojo {

	@Parameter(defaultValue = "0.8.19")
	protected String nodeJsVersion;

	@Parameter
	protected List<NodeJsModule> modules;

	/**
	 * Default location where nodejs will be extracted to and run from
	 */
	@Parameter(defaultValue = "${java.io.tmpdir}/nodejs")
	protected File nodejsDirectory;

	public void execute() throws MojoExecutionException {
		if (modules == null || modules.isEmpty()) {
			getLog().warn("No NodeJS work modules have been defined. Nothing to do");
			return;
		}

		String nodeJsClassifier = Os.OS_FAMILY;
		String nodeJsExecutable = getNodeJsExecutable(nodeJsClassifier);

		String nodeJsUrl = "http://nodejs.org/dist/v" + nodeJsVersion + "/";

		if (nodeJsClassifier.toLowerCase().startsWith("win") || Os.FAMILY_DOS.equals(nodeJsClassifier)) {
			nodeJsUrl = nodeJsUrl + "node.exe";
		} else {
			nodeJsUrl = nodeJsUrl + "node-v" + nodeJsVersion + "-linux-x86.tar.gz";
		}

		try {
			if (!FileUtils.fileExists(nodeJsExecutable)) {
				getLog().info("Downloading nodeJs");
				FileUtils.copyURLToFile(new URL(nodeJsUrl), new File(nodeJsExecutable));
			}

			executeNodeJs(nodeJsExecutable);
		} catch(MalformedURLException ex) {
			getLog().error("Did not like URL format: " + nodeJsUrl, ex);
			throw new MojoExecutionException("Did not like URL format: " + nodeJsUrl, ex);
		} catch (IOException ex) {
			getLog().error("Failed to downloading nodeJs from " + nodeJsUrl, ex);
			throw new MojoExecutionException("Failed to downloading nodeJs from " + nodeJsUrl, ex);
		} catch (MojoExecutionException ex) {
			getLog().error("Execution Exception", ex);
			throw new MojoExecutionException("Execution Exception", ex);
		} catch (CommandLineException ex) {
			getLog().error("Command Line Exception", ex);
			throw new MojoExecutionException("Command execution failed.", ex);
		}
    }

	/**
	 * Determine the right command executable for the given osFamily
	 *
	 * @param osFamily
	 * @return
	 */
	protected String getNodeJsExecutable(String osFamily) {
		if (osFamily == null) {
			throw new IllegalArgumentException("osFamily is null");
		}
		getLog().debug(String.format("Determing executable for osFamily = '%s'", osFamily));
		StringBuilder sb = new StringBuilder(nodejsDirectory.getAbsolutePath());
		if (osFamily.toLowerCase().startsWith("win") || Os.FAMILY_DOS.equals(osFamily)) {
			sb.append(File.separator).append("node-" + nodeJsVersion + ".exe");
		} else {
			sb.append(File.separator).append("bin").append(File.separator).append("node");
		}
		getLog().info(String.format("Determined executable for osFamily '%s' = '%s'", osFamily, sb.toString()));
		return sb.toString();
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

	/**
	 * Execute nodejs for every module in #modules.
	 *
	 * @param executable
	 * @throws MojoExecutionException
	 * @throws CommandLineException
	 */
	protected void executeNodeJs(String executable) throws MojoExecutionException, CommandLineException {
		for (NodeJsModule module : modules) {
			// get a commandline with the nodejs executable
			Commandline commandLine = getCommandLine(module.workingDirectory, executable, module.name, module.arguments);
			executeCommandLine(commandLine);
		}
	}

}
