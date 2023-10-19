# solidlab-sdx-jdk
Java version of the Solid Development eXperience

## Overview
This repo contains source code for the Java version of the SDX in the ```gradle-plugin```, ```commons``` and ```client-lib``` directories, as well as a demo app in the ```demo-app``` directory and a (limited) benchmark to evaluate performance in the ```benchmark``` directory.

## How to run
Currently to run the demo app, limited setup is necessary. 

First clone the repository to your local environment. 

Run the Gradle task ```publishToMavenLocal``` in the ```demo-app``` project. This will allow you to use the Gradle Plugin in your other projects.
Afterwards, run the Gradle task ```sdxBuild``` to generate the GraphQL schema based on the configuration in the ```build.gradle.kts``` file of the demo app.

To run the demo app, you first have to launch a Solid Server with a pod. To do so, run ```npx @solid/community-server -c @css:config/file.json -f pod/```

Finally, you can launch any of the programs in the main ```src``` folder of the demo app.

## Gradle configuration
To configure the Gradle plugin, add a ```sdx``` block to your ```build.gradle```. The following configuration options are available:

* ```catalogURL (Property<String>)```: URL of the catalog to be used to import shapes from.
* ```importShapesFromURL (ListProperty<String>)```: Shorthand for importShapes: imports the Shapes from the specified URLs using the default options.
* ```importShapesFromCatalog (ListProperty<String>)```: Shorthand for importShapes: imports the Shape packages with the specified catalog id using the default options.
* ```packageName (Property<String>)```: Name of the package to use for the generated classes.
* ```importShapes (ListProperty<ShapeImport>)```: List of the Shape imports (see below)

### ```ShapeImport```
A list of ShapeImports should be provided for the Gradle plugin. These contains the configuration for the SHACL shapes that the Gradle Plugin will fetch and use to generate the GraphQL plugin. Each ```ShapeImport``` has the following options with listed defaults:


* ```catalogId: String? = null```
Id of the shape package in the configured shape catalog
* ```importUrl: String? = null```
URL of the Shape package or file to import
* ```exclude: Set<String> = emptySet()```
Set of Shapes to ignore, identified by their URIs.
* ```generateMutations: Boolean = true```
Determines if mutations should be generated for the Shapes
* ```typePrefix: String = ""```
An optional prefix to add to types generated from the to be imported Shapes, can help to solve conflicts.
* ```shapeFileName: String? = null```
An optional filename to use for the downloaded Shape import, can help to solve conflicts.
