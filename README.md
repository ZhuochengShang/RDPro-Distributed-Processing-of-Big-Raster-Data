# RDPro-Distributed-Processing-of-Big-Raster-Data


## Documentation
### Dataset
Data used in this project is include under the /datasets folder.
Include small size for CDL and both samm and medium size for Landsat8.

### Installation
Build jar file from : rdpro ptoject folder
```shell
mvn clean package -Puberjar -DskipTests
```
### 
Open experiment project then build the jar file of it
```shell
mvn clean package -DskipTests
```
### run command line of experiments
```shell
beast-0.10.0-SNAPSHOT-bin/beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar <operation> <required arguments>
```
- Load & Write Distributed
```shell
beast-0.10.0-SNAPSHOT-bin/beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdprowritedist datasets/Landsat8_Small output_dist.tif
```

- Load & Write Compatability
```shell
beast-0.10.0-SNAPSHOT-bin/beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdprowritecomp datasets/Landsat8_Small output_comp.tif
```

- MapPixel
```shell
beast-0.10.0-SNAPSHOT-bin/beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdpromappixel datasets/Landsat8_Small
```

- FilterPixel
```shell
beast-0.10.0-SNAPSHOT-bin/beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdprofilter datasets/Landsat8_Small
```

- Overlay
```shell
beast-0.10.0-SNAPSHOT-bin/beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdprooverlay Landsat8_Small output_overlay.tif
```

- Reshape: reproject CRS
```shell
beast-0.10.0-SNAPSHOT-bin/beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdprocrs Landsat8_Small output_reproject.tif
```

- Reshape: rescale
```shell
beast-0.10.0-SNAPSHOT-bin/beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdproscale datasets/Landsat8_Small output_rescale.tif
```

- SlidingWindow
```shell
beast-0.10.0-SNAPSHOT-bin/beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdprosldw datasets/CDL_Small_Size
```

- Convolution
```shell
beast-0.10.0-SNAPSHOT-bin/beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdproconv datasets/CDL_Small_Size
```

- Flatten
```shell
beast-0.10.0-SNAPSHOT-bin/beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdproflatten datasets/CDL_Small_Size
```

- Rasterize
```shell
beast-0.10.0-SNAPSHOT-bin/beast-0.10.0-SNAPSHOT/bin/beast --jars beast-examples-0.10.0-SNAPSHOT.jar rdproraster 12000 12000 output_raster.tif
```
