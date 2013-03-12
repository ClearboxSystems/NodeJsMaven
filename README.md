NodeJsMaven
===========

NodeJs Plugin for Maven

This plugin allows you to run javascript through nodejs during a maven build.
It is based off https://github.com/sirrapa/nodejs-maven-plugin but does not
rely on any custom maven repositories.

The motivation for the plugin is to allow Typescript compilation, minification,
obfuscation and less compilation during a maven build. This is useful for
compiling on cloud services such as Heroku where there is not always a native
Typescript compiler available. To compile typescript during a build you could
configure your `pom.xml` as follows.

##### pom.xml file:
    <project>
      <build>
        <plugins>
          <plugin>
            <groupId>au.com.clearboxsystems.maven.plugins.nodejs</groupId>
            <artifactId>nodejs-maven-plugin</artifactId>
            <version>1.3</version>
            <executions>
              <execution>
                <phase>compile</phase>
                <goals><goal>compile</goal></goals>
              </execution>
            </executions>
            <configuration>
              <tasks>
                <nodeJsTask>
                  <workingDirectory>src/main/web/ts></workingDirectory>
                  <name>tsc-0.8.2.js</name>
                  <arguments>
                    <argument>--out</argument
                    <argument>${basedir}/pub/web/js/outputFile.js</argument>
                    <argument>sourceFile.ts</argument>
                  </arguments>
                </nodeJsTask>
                <closureCompilerTask>
                  <compilationLevel>ADVANCED_OPTIMIZATIONS</compilationLevel>
                  <sources>
                    <source>${basedir}/pub/web/js/outputFile.js</source>
                  </sources>
                  <externs>
                    <extern>${basedir}/src/main/web/closure/externs/</extern>
                  </externs>
                  <outputFile>${basedir}/pub/web/js/outputFile_compiled.js</outputFile>
                </closureCompilerTask>
              </tasks>
            </configuration>
          </plugin>
        </plugins>
      </build>
    </project>

In this example, you would need to set up a directory called src/main/web/ts
which contains a copy of the typescript compiler called tsc-0.8.2.js along with
its lid.d.ts file.

As of version 1.1 closure compiler is supported as a task, so this can allow
for GCC based minification to occur in your build instead of javascript based
  solutions such as uglifyjs up yui.

As of version 1.2 added support for externsDirectory in the closure compiler so
that you can add an entire directory of externs in a single command line.

Also added a watch goal so that any task that has <watch>true</watch> will keep
watching files and recompiling files when the watched files are modified. This
can be executed with <pre> mvn nodejs:watch <pre>

As of version 1.3, `sources` and `externs` will include all `.js` files in a
directory recursively. The `externsDirectory` has been removed.
