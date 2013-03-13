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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.tools.ant.taskdefs.Parallel;
import org.codehaus.plexus.util.cli.CommandLineException;

import java.io.IOException;
import java.io.File;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Author: Paul Solomon
 * Date: 13/02/13
 */
@Mojo( name = "watch", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class NodeJsWatcherMojo extends NodeJsMojoBase {

	private static final WatchEvent.Kind<?>[] watchEvents = {StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE};
	private WatchService watchService;
	private Map<Path, Task> watchTasks = new HashMap<>();

	private boolean changed = true;

	private final NodeJsMojoBase.TaskFilter filter = new NodeJsMojoBase.TaskFilter() {
			public boolean accept(Task t) {
				return t.watch;
			}
		};

	@Override
	public void execute() throws MojoExecutionException {
		NodeJsMojoBase.NodeInstallInformation info = super.run(filter);

		if (info == null) {
			return;
		}

		for (Task task : tasks) {
			try {
				if (task.watch) {
					addWatchForTask(task);
				}
			} catch (IOException ex) {
				throw new MojoExecutionException("Error adding watch for task " + task, ex);
			}
		}


		getLog().info("Starting watch vigil");
		try {
			watch(info);
		} catch (CommandLineException ex) {
			throw new MojoExecutionException("Error during watch", ex);
		} catch (InterruptedException ex) {
			throw new MojoExecutionException("Error during watch", ex);
		} catch (IOException ex) {
			throw new MojoExecutionException("Error during watch", ex);
		}
	}


	public void addWatchForTask(final Task task) throws IOException {
		if (task instanceof NodeJsTask) {
			NodeJsTask nodeJsTask = (NodeJsTask) task;
			Path sourceDir = nodeJsTask.workingDirectory.toPath();
			watchTasks.put(sourceDir, task);

			if (watchService == null) {
				watchService = sourceDir.getFileSystem().newWatchService();
			}

			Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					dir.register(watchService, watchEvents);
					watchTasks.put(dir, task);
					//getLog().info("Adding NodeJS watcher for: " + dir.toString());
					return FileVisitResult.CONTINUE;
				}
			});
		} else if (task instanceof ClosureCompilerTask) {
			final ClosureCompilerTask closureCompilerTask = (ClosureCompilerTask) task;

			if (watchService == null) {
				watchService = FileSystems.getDefault().newWatchService();
			}

			for (File source : closureCompilerTask.sources) {
				Path path = source.getParentFile().toPath();
				path.register(watchService, watchEvents);
				watchTasks.put(path, task);
				Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
						@Override
							public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
							dir.register(watchService, watchEvents);
							watchTasks.put(dir, task);
							//getLog().info("Adding Closure watcher for: " + closureCompilerTask.sources);
							return FileVisitResult.CONTINUE;
						}
					});
			}
		} else {
			watchService = null;
		}
	}

	public void watch(NodeJsMojoBase.NodeInstallInformation info) throws IOException, InterruptedException, CommandLineException, MojoExecutionException {
		while (true) {
			if (changed) {
				getLog().info("Waiting for changes...");
				changed = false;
			}

			WatchKey watchKey = watchService.take();
			Path dir = (Path) watchKey.watchable();

			List<String> updatedFiles = new ArrayList<>();
			for (WatchEvent<?> event : watchKey.pollEvents()) {
				Path file = dir.resolve((Path) event.context());
				String absolutePath = file.toFile().getAbsolutePath();
				String filename = file.toFile().getName();
				getLog().debug(String.format("watched %s - %s", event.kind().name(), file));


				if (file.toString().endsWith("___jb_bak___") || file.toString().endsWith("___jb_old___")) { // Ignore tmp files from idea
					continue;
				}

				if (updatedFiles.contains(file.toString())) {
					continue;
				}

				if (filename.startsWith(".")) {
					continue;
				}

				if (filename.endsWith("~")) {
					continue;
				}

				if (Files.isDirectory(file)) {
					if (event.kind().name().equals(StandardWatchEventKinds.ENTRY_CREATE.name())) {
						// watch created folder.
						Task task = watchTasks.get(file.getParent());
						if (task != null) {
							file.register(watchService, watchEvents);
							getLog().info(String.format("added watch for %s", file));
							watchTasks.put(file, task);
						}
					}
					continue;
				}

				if (event.kind().name().equals(StandardWatchEventKinds.ENTRY_MODIFY.name()) || event.kind().name().equals(StandardWatchEventKinds.ENTRY_CREATE.name())) {
					updatedFiles.add(file.toString());
					Task task = watchTasks.get(file);
					if (task == null) {
						task = watchTasks.get(file.getParent());
					}
					if (task != null) {
						if (task instanceof ClosureCompilerTask) {
							ClosureCompilerTask closureCompilerTask = (ClosureCompilerTask) task;
							if (!filename.endsWith(".js")) {
								// bail out here, because if it's not a .js file then the compiler task won't compile it
								continue;
							}
							/*boolean validFile = false;
							for (File source : closureCompilerTask.sources) {
								String path = source.getAbsolutePath(); 
								if (source.isDirectory()) {
									if (absolutePath.startsWith(filename)) {
										validFile = true;
									}
								} else {
									if (absolutePath.equals(path)) {
										validFile = true;
									}
								}
							}
							if (!validFile) {
								continue;
								}*/
						}
						getLog().info(String.format("%s MODIFIED rerunning Task", file));
						executeTask(task, info);
						changed = true;
					}
				}

			}
			watchKey.reset();
		}
	}


}
