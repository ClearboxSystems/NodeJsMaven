NodeJsMaven
===========

NodeJs Plugin for Maven

This plugin allows you to run javascript through nodejs during a maven build. It is based off
https://github.com/sirrapa/nodejs-maven-plugin but does not rely on any custom maven repositories.

The motivation for the plugin is to allow Typescript compilation, minification, obfuscation and less compilation
during a maven build. This is useful for compiling on cloud services such as Heroku where there is not always a
native Typescript compiler available. To compile typescript during a build you could configure your pom.xml as follows.

##### pom.xml file:
    <project>
    	<build>
			<plugins>
				<plugin>
					<groupId>au.com.clearboxsystems.maven.plugins.nodejs</groupId>
	                <artifactId>nodejs-maven-plugin</artifactId>
	                <version>1.1</version>
	                <executions>
	                    <execution>
	                        <phase>compile</phase>
	                        <goals>
	                            <goal>compile</goal>
	                        </goals>
	                    </execution>
	                </executions>
	                <configuration>
	                    <tasks>
	                        <nodeJsTask>
	                            <workingDirectory>websrc/ts</workingDirectory>
	                            <name>tsc-0.8.2.js</name>
	                            <arguments>
	                                <argument>--out</argument>
	                                <argument>${basedir}/web/js/outputFile.js</argument>
	                                <argument>sourceFile.ts</argument>
	                            </arguments>
	                        </nodeJsTask>
	                        <closureCompilerTask>
								<compilationLevel>ADVANCED_OPTIMIZATIONS</compilationLevel>
								<sourceFile>${basedir}/web/js/outputFile.js</sourceFile>
								<externs>
									<extern>${basedir}/websrc/externs/angular.js</extern>
								</externs>
								<outputFile>${basedir}/web/js/outputFile.min.js</outputFile>
							</closureCompilerTask>
	                    </tasks>
	                </configuration>
	            </plugin>
	        </plugins>
        </build>
    </project>

In this example, you would need to set up a directory called websrc/ts which contains a copy of the typescript compiler
calles tsc-0.8.2.js along with its lid.d.ts file.

As of version 1.1 closure compiler is supported as a task, so this can allow for GCC based minification to occur in your
build instead of javascript based solutions such as uglifyjs up yui.
