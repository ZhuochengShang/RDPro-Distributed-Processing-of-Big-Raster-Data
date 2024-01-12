# RDPro-Distributed-Processing-of-Big-Raster-Data


## Documentation
### Dataset
Data used in this project is include under the /data folder.
Include small and medium size 

### Installation
Build jar file
```shell
mvn clean package -Puberjar -DskipTests
```
### 
Open experiment project then build the jar file
```shell
mvn clean package -Puberjar -DskipTests
```
### run command line of experiments
```shell
beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar <operation> <required arguments>
```
- Load & Write Distributed
```shell
beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdprowrite_dist Landsat8_Small output.tif
```

- Load & Write Compatability
```shell
beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdprowrite_comp Landsat8_Small output.tif
```

- MapPixel
```shell
beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdprowrite Landsat8_Small output.tif
```

- FilterPixel
```shell
beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdprowrite Landsat8_Small output.tif
```

- Overlay
```shell
beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdprowrite Landsat8_Small output.tif
```

- Reshape: reproject CRS
```shell
beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdprowrite Landsat8_Small output.tif
```

- Reshape: rescale
```shell
beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdprowrite Landsat8_Small output.tif
```

- SlidingWindow
```shell
beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdprowrite Landsat8_Small output.tif
```

- Convolution
```shell
beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdprowrite Landsat8_Small output.tif
```

- Flatten
```shell
beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdprowrite Landsat8_Small output.tif
```

- Rasterize
```shell
beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdprowrite Landsat8_Small output.tif
```