# RDPro-Distributed-Processing-of-Big-Raster-Data


## Documentation
Full detailed documentation of RDPro: 
https://bitbucket.org/bdlabucr/beast/src/master/doc/rdpro.md

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
rdpro-0.10.1-SNAPSHOT-bin/rdpro-0.10.1-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.1-SNAPSHOT.jar <operation> <required arguments>
```
- Load & Write Distributed
```shell
rdpro-0.10.1-SNAPSHOT-bin/rdpro-0.10.1-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.1-SNAPSHOT.jar rdprowritedist datasets/Landsat8_City output_dist.tif
```

- Load & Write Compatability
```shell
rdpro-0.10.1-SNAPSHOT-bin/rdpro-0.10.1-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.1-SNAPSHOT.jar rdprowritecomp datasets/Landsat8_City output_comp.tif
```

- MapPixel
```shell
rdpro-0.10.1-SNAPSHOT-bin/rdpro-0.10.1-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.1-SNAPSHOT.jar rdpromappixel datasets/Landsat8_City
```

- FilterPixel
```shell
rdpro-0.10.1-SNAPSHOT-bin/rdpro-0.10.1-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.1-SNAPSHOT.jar rdprofilter datasets/Landsat8_City
```

- Overlay
```shell
rdpro-0.10.1-SNAPSHOT-bin/rdpro-0.10.1-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.1-SNAPSHOT.jar rdprooverlay datasets/CDL_2021_City datasets/Landsat8_City
```

- Reshape: reproject CRS
```shell
rdpro-0.10.1-SNAPSHOT-bin/rdpro-0.10.1-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.1-SNAPSHOT.jar rdprocrs datasets/Landsat8_City output_reproject.tif
```

- Reshape: rescale
```shell
rdpro-0.10.1-SNAPSHOT-bin/rdpro-0.10.1-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.1-SNAPSHOT.jar rdproscale datasets/Landsat8_City output_rescale.tif
```

- SlidingWindow
```shell
rdpro-0.10.1-SNAPSHOT-bin/rdpro-0.10.1-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.1-SNAPSHOT.jar rdprosldw datasets/CDL_2021_City
```

- Convolution
```shell
rdpro-0.10.1-SNAPSHOT-bin/rdpro-0.10.1-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.1-SNAPSHOT.jar rdproconv datasets/CDL_2021_City
```

- Flatten
```shell
rdpro-0.10.1-SNAPSHOT-bin/rdpro-0.10.1-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.1-SNAPSHOT.jar rdproflatten datasets/CDL_2021_City
```

- Rasterize
```shell
rdpro-0.10.1-SNAPSHOT-bin/rdpro-0.10.1-SNAPSHOT/bin/rdpro --master spark://127.0.0.1:7077 --jars rdpro-experiments-0.10.1-SNAPSHOT.jar rdproraster 12000 12000 output_raster.tif
```
