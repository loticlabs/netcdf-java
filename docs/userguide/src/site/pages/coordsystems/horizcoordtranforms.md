---
title: Standard horizontal coordinate transforms 
last_updated: 2021-06-28
sidebar: userguide_sidebar 
permalink: std_horizonal_coord_transforms.html
toc: false
---

## Standard Horizontal Coordinate Transforms
This page documents the horizontal coordinate transforms that are standard in CDM. Most follow the [CF-1.0 Conventions](http://cfconventions.org/Data/cf-conventions/cf-conventions-1.6/build/cf-conventions.html#appendix-grid-mappings){:target="_blank"}, where they are called grid_mappings. 
They are also often called projections, because most employ projective geometry.

To follow CF, typically one creates a transform definition variable, whose purpose is to contain attributes whose values are the parameters of the transform. 
Typically, the variable does not contain any real data, and so a scalar variable is used. 
Each data variable that uses the transform has an attribute with name `grid_mapping` whose value is the name of the transform variable. The projection coordinate variables are also required.

For example:

~~~
  float data(y0, x0);
    data:grid_mapping = "Lambert_Conformal";
     
  double x0(x0=640);
    x0:standard_name = "projection_x_coordinate";
    x0:long_name = "x distance on the projection plane from the origin";
    x0:units = "km";

  double y0(y0=440);
    y0:standard_name = "projection_y_coordinate";
    y0:long_name = "y distance on the projection plane from the origin";
    y0:units = "km";

  char Lambert_Conformal;
    Lambert_Conformal:grid_mapping_name = "lambert_conformal_conic";
    Lambert_Conformal:standard_parallel = 38.5; // double
    Lambert_Conformal:longitude_of_central_meridian = 262.5; // double
    Lambert_Conformal:latitude_of_projection_origin = 38.5; // double
~~~
     
In this example, the `Lambert_Conformal` variable defines the projection, and the data variable references it with the `grid_mapping` attribute. 
The `x0` and `y0` are coordinate variables, and the CF convention `standard_name` attribute is used to identify them unambiguously as projection x and y coordinates. 
The default unit is km, but any units that can be converted to km can be used. The value of the coordinates must be the correct geolocation for your data. 
The projection that you specify is then used to calculate the correct (lat, lon) point. All projections have the form:

~~~
 Projection: (x, y) -> (lat, lon)
 ProjectionInverse: (lat, lon) -> (x, y)
~~~

where the x,y values in this equation are the ones that you put into the x and y projection coordinate variables.

### Summary: CF Horizontal transforms in the CDM

In order for CF Horizontal transforms to work in the CDM, you must:

1. Define x and y projection coordinate variables, using the correct projection units, typically km on the projection plane.
2. Define your projection dummy variable which has an attribute `grid_mapping_name`
3. Refer to the projection in your data variables with the `grid_mapping` attribute.

#### Resources

* Standard horizontal transforms are documented on this page. You can also [implement your own](coord_system_builder.html).
* You may also be interested in [Standard Vertical Coordinate Transforms](std_vertical_coord_transforms.html).
* The [CDM _Coordinate Conventions](coord_attr_conv.html)

### Standard Horizontal Transforms (Projections)

The following are the currently implemented transforms.
Attribute names follow the [CF Conventions](http://cfconventions.org/Data/cf-conventions/cf-conventions-1.6/build/cf-conventions.html#appendix-grid-mappings){:target="_blank"} Appendix F (Grid Mappings). 
See that document for details on the meanings of the formula terms. The projection algorithms are mostly taken from *John Snyder,* [Map Projections used by the USGS, Bulletin 1532, 2nd edition (1983)](https://pubs.usgs.gov/bul/1532/report.pdf){:target="_blank"}. 
Some of the ellipsoidal forms are corrected versions of *com.jhlabs.map.proj*.

* In some cases, the earth radius may be specified, which uses a spherical earth for the projection. 
  This is indicated by the presence of the `earth_radius` attribute.

* In some cases, the ellipsoidal form of the projection may be used. 
  This is indicated by the presence of the `semi_major_axis`, and either the `semi_minor_axis` or `inverse_flattening` attributes. 
  Note that not all projections have an ellipsoidal implementation.

* When neither `earth_radius` or `semi_major_axis` is allowed or specified, the projection will be spherical with a default earth radius of 6371.229 km.

* The units of `earth_radius`, `semi_major_axis`, `semi_minor_axis` must be in meters.

* The optional `false_easting`, and `false_northing` should match the units of the x and y projection coordinates. 
  Alternatively, the attribute "units" may be specified on the dummy `CoordinateTransform` variable (this is CDM standard, not CF). 
  
When they are not present in the documentation below, they are not used. Contact us if you have a real sample where they are non-zero.

#### albers_conical_equal_area

~~~
   char Albers_Projection;
     :grid_mapping_name = "albers_conical_equal_area";
     :standard_parallel = 20.0, 60.0; // one or two
     :longitude_of_central_meridian = -32.0; 
     :latitude_of_projection_origin = 40.0; 
     :false_easting = 0.0;
     :false_northing = 0.0;
     :earth_radius = 6371.229;
     :semi_major_axis =  6378.137;
     :semi_minor_axis =  6356.752;
     :inverse_flattening =   298.257;
~~~

This uses a spherical or ellipsoidal earth. See Snyder, p 98.

#### azimuthal_equidistant

~~~
char azimuthal_equidistant;
 :grid_mapping_name = "azimuthal_equidistant";
 :semi_major_axis = 6378137.0; // double
 :inverse_flattening = 298.257223563; // double
 :longitude_of_prime_meridian = 0.0; // double
 :false_easting = 0.0; // double
 :false_northing = 0.0; // double
 :latitude_of_projection_origin = -37.0; // double
 :longitude_of_projection_origin = 145.0; // double
~~~

Adapted from proj4 jhlabs. See Snyder, p 191.

#### flat_earth

~~~
   char Flat_Earth;
     :grid_mapping_name = "flat_earth";
     :longitude_of_projection_origin = -132.0; 
     :latitude_of_projection_origin = 40.0;
~~~
 
This is not a standard CF projection. It is used when a "flat earth" assumption is acceptable.

#### geostationary

~~~
   char Geostationary;
     :grid_mapping_name = "geostationary";
     :longitude_of_projection_origin = -97.0; 
     :latitude_of_projection_origin = 0.0; 
     :perspective_point_height= 33.0, 45.0;  
     :false_easting = 0.0;
     :false_northing = 0.0;
     :earth_radius = 6371.229;
     :semi_major_axis =  6378.137;
     :semi_minor_axis =  6356.752;
     :inverse_flattening =   298.257;
     :sweep_angle_axis= 33.0, 45.0;  
     :fixed_angle_axis= 33.0, 45.0;
~~~  

This uses an ellipsoidal earth. Notes from CF:

* The `perspective_point_height` is the distance to the surface of the ellipsoid. Adding the earth major axis gives the distance from the centre of the earth.
* The `sweep_angle_axis` attribute indicates which axis the instrument sweeps. The value *y* corresponds to the spin-stabilized Meteosat satellites, the value *x* to the GOES-R satellite.
* The `fixed_angle_axis` attribute indicates which axis the instrument is fixed. The values are opposite to `sweep_angle_axis`. Only one of those two attributes are mandatory.

See CF [adding geostationary](http://cfconventions.org/Data/cf-conventions/cf-conventions-1.6/build/cf-conventions.html#appendix-grid-mappings){:target="_blank"}. This projection covers both Eumetsat GEOS and US GOES-R satellites.

#### lambert_azimuthal_equal_area

~~~
   char Lambert_azimuth_Projection;
     :grid_mapping_name = "lambert_azimuthal_equal_area";
     :longitude_of_projection_origin = -32.0; 
     :latitude_of_projection_origin = 90.0; 
     :false_easting = 0.0; // km 
     :false_northing = 0.0; // km 
     :earth_radius = 6371.229;
~~~
This uses a spherical earth. See Snyder, p 184.

#### lambert_conformal_conic

~~~
   char Lambert_Conformal;
     :grid_mapping_name = "lambert_conformal_conic";
     :standard_parallel = 33.0, 45.0;   // one or two
     :longitude_of_central_meridian = -97.0; 
     :latitude_of_projection_origin = 40.0; 
     :false_easting = 0.0;
     :false_northing = 0.0;
     :earth_radius = 6371.229;
     :semi_major_axis =  6378.137;
     :semi_minor_axis =  6356.752;
     :inverse_flattening =   298.257;
~~~

This uses a spherical or ellipsoidal earth. See Snyder, p 104.

#### lambert_cylindrical_equal_area

~~~
char lambert_cylindrical_equal_area;
 :grid_mapping_name = "lambert_cylindrical_equal_area";
 :semi_major_axis = 6378137.0; // double
 :inverse_flattening = 298.257223563; // double

 :longitude_of_central_meridian = 145.0; // double
 :false_easting = 0.0; // double
 :false_northing = 0.0; // double
 :standard_parallel = -37.0; // double
~~~
 
Adapted from proj4 / jhlabs. See Snyder, p 76. As of version 4.3.10

#### mcidas_area

~~~
   char McIDAS_Projection;
     :grid_mapping_name = "mcidas_area";
     :AreaHeader = 33.0, 45.0, ...;   // an integer array
     :NavHeader = -97.0, ...;     // an integer array
~~~
 
This is not a standard CF projection. The headers are read from a McIDAS Area file, and placed in the attributes as int arrays.

#### mercator

~~~
  char Mercator_Projection;
     :grid_mapping_name = "mercator";
     :longitude_of_projection_origin = 110.0; 
     :latitude_of_projection_origin = -25.0; 
     :standard_parallel = 0.02;
~~~
 
This uses a spherical earth and default radius. See Snyder, p 47.

#### MSGnavigation

~~~
   char Space_View_Perspective_or_Orthographic;
     :grid_mapping_name = "MSGnavigation";
     :longitude_of_projection_origin = 0.0; // double
     :latitude_of_projection_origin = 0.0; // double
     :semi_major_axis = 6356755.5; // double
     :semi_minor_axis = 6378140.0; // double
     :height_from_earth_center = 4.2163970098E7; // double
     :scale_x = 35785.830098; // double
     :scale_y = -35785.830098; // double
~~~

This is not a standard CF projection, and is used for MSG (METEOSAT 8 onwards) data. 
This uses an ellipsoidal earth. Note there is a bug in some versions of Eumetsat GRIB encoding, per Simon Eliot 1/18/2010, in which the "apparent diameter of earth in units of grid lengths" is incorrectly specified. 
We do a correction for this in `ucar.nc2.internal.grid.GridHorizCS` when we read the GRIB file.

#### orthographic

~~~
  char Orthographic_Projection;
     :grid_mapping_name = "orthographic";
     :longitude_of_projection_origin = 110.0; 
     :latitude_of_projection_origin = -25.0;
~~~
 
This is not a standard CF projection. This uses a spherical earth and default radius. See Snyder, p 145.

#### polar_stereographic

~~~
   char Polar_Stereographic;
     :grid_mapping_name = "polar_stereographic";
     :straight_vertical_longitude_from_pole = -32.0; 
     :latitude_of_projection_origin = 90.0; 
     :scale_factor_at_projection_origin = 0.9330127018922193; 
     :false_easting = 0.0;
     :false_northing = 0.0;
     :semi_major_axis =  6378.137;
     :semi_minor_axis =  6356.752;
     :inverse_flattening =   298.257;
~~~

The Polar Stereographic is the same as the Stereographic projection with origin at the north or South Pole. 
It can use a spherical or ellipsoidal earth.

The polar stereographic will accept these alternate parameter names:

~~~
   char Polar_Stereographic;
     :grid_mapping_name = "polar_stereographic";
     :longitude_of_projection_origin = -32.0; 
     :latitude_of_projection_origin = 90.0; 
     :standard_parallel = 0.9330127018922193;
~~~
      
If the `standard_parallel` is specified, this indicates the parallel where the scale factor = 1.0. 
In that case the projection scale factor is calculated as:

~~~
 double sin = Math.abs(Math.sin( Math.toRadians( stdpar)));
 scale = (1.0 + sin)/2;
rotated_pole
 char rotated_pole;
    :grid_mapping_name = "rotated_latitude_longitude";
   :grid_north_pole_latitude = 37.0f; // float
   :grid_north_pole_longitude = -153.0f; // float
~~~
   
The rotated latitude and longitude coordinates are identified by the `standard_name` attribute values `grid_latitude` and `grid_longitude` respectively. For example:

~~~
 float rlat(rlat=84);
     :standard_name = "grid_latitude";
     :long_name = "rotated latitude";
     :units = "degrees";
     :_CoordinateAxisType = "GeoY";
   float rlon(rlon=90);
     :standard_name = "grid_longitude";
     :long_name = "rotated longitude";
     :units = "degrees";
     :_CoordinateAxisType = "GeoX";
~~~

The rotated longitude coordinate must be in the range [-180,180], meaning there will be a problem when it crosses the dateline. This code was contributed by Robert Schmunk.

#### rotated_latlon_grib

~~~
 char rotated_pole;
   :grid_mapping_name = "rotated_latlon_grib";
   :grid_south_pole_latitude = 37.0f; // float
   :grid_south_pole_longitude= -153.0f; // float
   :grid_south_pole_angle= 0.0f; // float
~~~   

Grib 1 projection 10 and Grib 2 projection 1. This is not a standard CF projection. Contributed by Tor Christian Bekkvik.

#### sinusoidal

~~~
   char SinusoidalProjection;
     :grid_mapping_name = "sinusoidal";
     :longitude_of_central_meridian = 0.0; // required
     :false_easting = 0.0;
     :false_northing = 0.0;
     :earth_radius = 6371.229;
~~~
 
This uses a spherical earth. See code to [add sinusoidal](https://github.com/cf-convention/cf-conventions/pull/85){:target="_blank"} on GitHub.

This projection is one of those selected by the [ESA Climate Change Initiative](http://www.esa-cci.org/){:target="_blank"}, which will be reanalysing the MERIS, MODIS and SeaWiFS time series and producing netcdf-CF files.

#### stereographic

~~~
   char Stereographic;
     :grid_mapping_name = "stereographic";
     :longitude_of_projection_origin = -32.0; 
     :latitude_of_projection_origin = 90.0; 
     :scale_factor_at_projection_origin = 0.9330127018922193; 
     :false_easting = 0.0;
     :false_northing = 0.0;
     :semi_major_axis =  6378.137;
     :semi_minor_axis =  6356.752;
     :inverse_flattening =   298.257;
~~~

This uses a spherical or ellipsoidal earth. See Snyder, p 153.

#### transverse_mercator

~~~
   char Transverse_mercator;
     :grid_mapping_name = "transverse_mercator";
     :longitude_of_central_meridian = -32.0; 
     :latitude_of_projection_origin = 40.0; 
     :scale_factor_at_central_meridian = 0.9330127018922193; 
     :false_easting = 0.0;
     :false_northing = 0.0;
     :semi_major_axis =  6378.137;
     :semi_minor_axis =  6356.752;
     :inverse_flattening =   298.257;
     :_CoordinateTransformType = "Projection";
     :_CoordinateAxisTypes = "GeoX GeoY";
~~~     
     
This uses a spherical or ellipsoidal earth. See Snyder, p 53.

#### UTM (Universal Transverse Mercator)

~~~
   char UTM_Projection;
     :grid_mapping_name = "universal_transverse_mercator";
     :utm_zone_number = 22; 
     :semi_major_axis = 6378137;
     :inverse_flattening = 298.257;
     :_CoordinateTransformType = "Projection";
     :_CoordinateAxisTypes = "GeoX GeoY";
~~~
     
This is not a standard CF projection. UTM uses an ellipsoidal earth. Code contributed from the GeoTransform package by Dan Toms, SRI International. Note that semi_major_axis is in meters.

#### vertical_perspective

~~~
   char vertical_perspective_Projection;
     :grid_mapping_name = "vertical_perspective";
     :longitude_of_projection_origin = -97.0; 
     :latitude_of_projection_origin = 40.0; 
     :height_above_earth = 23980.0; // km
     :earth_radius = 6371.229;
     :false_easting = 0.0; 
     :false_northing = 0.0; 
     :_CoordinateTransformType = "Projection";
     :_CoordinateAxisTypes = "GeoX GeoY";
~~~
     
This uses a spherical earth. See Snyder, p 176.
