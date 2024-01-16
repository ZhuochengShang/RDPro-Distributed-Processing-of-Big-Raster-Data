# RDPro-Distributed-Processing-of-Big-Raster-Data


## Documentation
### Dataset
Data used in this project is include under the /datasets folder.
Include small size for CDL and both samll and medium size for Landsat8.

### Installation
Build jar file from : rdpro ptoject folder
```shell
mvn clean package -Puberjar -DskipTests
```
### 
Open rdpro_experiments project then build the jar file of it
```shell
mvn clean package -DskipTests
```
### run command line of experiments
```shell
rdpro-0.10.0-SNAPSHOT-bin/rdpro-0.10.0-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.0-SNAPSHOT.jar <operation> <required arguments>
```
- Load & Write Distributed
```shell
rdpro-0.10.0-SNAPSHOT-bin/rdpro-0.10.0-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.0-SNAPSHOT.jar rdprowritedist datasets/Landsat8_Small_Size output_dist.tif
```

- Load & Write Compatability
```shell
rdpro-0.10.0-SNAPSHOT-bin/rdpro-0.10.0-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.0-SNAPSHOT.jar rdprowritecomp datasets/Landsat8_Small_Size output_comp.tif
```

- MapPixel
```shell
rdpro-0.10.0-SNAPSHOT-bin/rdpro-0.10.0-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.0-SNAPSHOT.jar rdpromappixel datasets/Landsat8_Small_Size
```

- FilterPixel
```shell
rdpro-0.10.0-SNAPSHOT-bin/rdpro-0.10.0-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.0-SNAPSHOT.jar rdprofilter datasets/Landsat8_Small_Size
```

- Overlay
```shell
rdpro-0.10.0-SNAPSHOT-bin/rdpro-0.10.0-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.0-SNAPSHOT.jar rdprooverlay Landsat8_Small_Size output_overlay.tif
```

- Reshape: reproject CRS
```shell
rdpro-0.10.0-SNAPSHOT-bin/rdpro-0.10.0-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.0-SNAPSHOT.jar rdprocrs Landsat8_Small_Size output_reproject.tif
```

- Reshape: rescale
```shell
rdpro-0.10.0-SNAPSHOT-bin/rdpro-0.10.0-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.0-SNAPSHOT.jar rdproscale datasets/Landsat8_Small_Size output_rescale.tif
```

- SlidingWindow
```shell
rdpro-0.10.0-SNAPSHOT-bin/rdpro-0.10.0-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.0-SNAPSHOT.jar rdprosldw datasets/CDL_Small_Size
```

- Convolution
```shell
rdpro-0.10.0-SNAPSHOT-bin/rdpro-0.10.0-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.0-SNAPSHOT.jar rdproconv datasets/CDL_Small_Size
```

- Flatten
```shell
rdpro-0.10.0-SNAPSHOT-bin/rdpro-0.10.0-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.0-SNAPSHOT.jar rdproflatten datasets/CDL_Small_Size
```

- Rasterize
```shell
rdpro-0.10.0-SNAPSHOT-bin/rdpro-0.10.0-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.0-SNAPSHOT.jar rdproraster 12000 12000 output_raster.tif
```
