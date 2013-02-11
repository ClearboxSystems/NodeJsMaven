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


/**
 * Author: Paul Solomon
 * Date: 11/02/13
 */
public class NodeJsPluginException extends Exception {
	public NodeJsPluginException() {
	}

	public NodeJsPluginException(String message) {
		super( message );
	}

	public NodeJsPluginException(Throwable cause) {
		super( cause );
	}

	public NodeJsPluginException(String message, Throwable cause) {
		super( message, cause );
	}
}
