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

import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.util.List;

/**
 * Author: Paul Solomon
 * Date: 11/02/13
 */
public class ClosureCompilerTask extends Task {
	@Parameter(required = true)
	public File sourceFile;

	@Parameter
	public File externDirectory;

	@Parameter
	public List<File> externs;

	@Parameter(required = true)
	public File outputFile;

//	WHITESPACE_ONLY,
//	SIMPLE_OPTIMIZATIONS
//	ADVANCED_OPTIMIZATIONS
	@Parameter(defaultValue = "SIMPLE_OPTIMIZATIONS")
	public String compilationLevel;

}
