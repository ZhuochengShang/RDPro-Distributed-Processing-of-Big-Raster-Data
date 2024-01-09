# RDPro-Distributed-Processing-of-Big-Raster-Data

## Datasets
https://drive.google.com/drive/folders/10w-g6YQTXtEsv9pchjqWh1O64xODS-9c?usp=sharing 
Required doownload and unzip Landsat_GeoTiff and Landsat_USGS
## compile mvn
run mvn install -DskipTests

## compile experiment package
mvn clean package -Puberjar -DskipTests

## run command line
### Load and Write
### Mappixels and FilterPixels
### Overlay
### Reshape: reproject to EPSG:4326
### Reshape: rescale
### SlidingWindow
### Convolution
### Flatten
### Rasterize
