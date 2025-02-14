/*
 * Copyright (c) 1998-2020 John Caron and University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */
package ucar.nc2.internal.dataset;

import java.util.List;
import java.util.ArrayList;
import java.util.Formatter;
import javax.annotation.Nullable;

import ucar.array.ArrayType;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.nc2.AttributeContainer;
import ucar.nc2.Attribute;
import ucar.nc2.constants.CDM;
import ucar.nc2.constants.CF;
import ucar.nc2.constants._Coordinate;
import ucar.nc2.dataset.*;
import ucar.nc2.ft2.coverage.CoverageTransform;
import ucar.nc2.internal.dataset.transform.horiz.*;
import ucar.nc2.internal.dataset.transform.vertical.*;
import ucar.unidata.geoloc.Projection;

/** Factory for Coordinate Transforms. */
public class CoordTransformFactory {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CoordTransformFactory.class);
  private static final List<Transform> transformList = new ArrayList<>();
  private static boolean userMode = false;

  private static final boolean loadWarnings = false;

  // search in the order added
  static {
    registerTransform(CF.ALBERS_CONICAL_EQUAL_AREA, AlbersEqualArea.class);
    registerTransform(CF.AZIMUTHAL_EQUIDISTANT, AzimuthalEquidistant.class);
    registerTransform("flat_earth", FlatEarth.class);
    registerTransform(CF.GEOSTATIONARY, Geostationary.class);
    registerTransform(CF.LAMBERT_AZIMUTHAL_EQUAL_AREA, LambertAzimuthal.class);
    registerTransform(CF.LAMBERT_CONFORMAL_CONIC, LambertConformalConic.class);
    registerTransform(CF.LAMBERT_CYLINDRICAL_EQUAL_AREA, LambertCylindricalEqualArea.class);
    registerTransform(CF.LATITUDE_LONGITUDE, LatLon.class);
    // optional - needs visad.jar
    registerTransformMaybe("mcidas_area", "ucar.nc2.iosp.mcidas.McIDASAreaTransformBuilder");
    registerTransform(CF.MERCATOR, Mercator.class);
    registerTransform("MSGnavigation", MSGnavigation.class);
    registerTransform(CF.ORTHOGRAPHIC, Orthographic.class);
    registerTransform(CF.POLAR_STEREOGRAPHIC, PolarStereographic.class);
    registerTransform("polyconic", PolyconicProjection.class); // ghansham@sac.isro.gov.in 1/8/2012
    registerTransform(CF.ROTATED_LATITUDE_LONGITUDE, RotatedPole.class);
    registerTransform("rotated_latlon_grib", RotatedLatLon.class);
    registerTransform(CF.SINUSOIDAL, Sinusoidal.class);
    registerTransform(CF.STEREOGRAPHIC, Stereographic.class);
    registerTransform(CF.TRANSVERSE_MERCATOR, TransverseMercator.class);
    registerTransform("UTM", UTM.class);
    registerTransform(CF.VERTICAL_PERSPECTIVE, VerticalPerspective.class);

    // DO NOT USE: see CF1Convention.makeAtmLnCoordinate()
    // registerTransform("atmosphere_ln_pressure_coordinate", VAtmLnPressure.class);

    registerTransform("atmosphere_hybrid_height_coordinate", CFHybridHeight.class);
    registerTransform("atmosphere_hybrid_sigma_pressure_coordinate", CFHybridSigmaPressure.class);
    registerTransform("atmosphere_sigma_coordinate", CFSigma.class);
    registerTransform("ocean_s_coordinate", CFOceanS.class);
    registerTransform("ocean_sigma_coordinate", CFOceanSigma.class);
    registerTransform("explicit_field", VExplicitField.class);
    registerTransform("existing3DField", VExplicitField.class); // deprecate

    // -sachin 03/25/09
    registerTransform("ocean_s_coordinate_g1", VOceanSG1.class);
    registerTransform("ocean_s_coordinate_g2", VOceanSG2.class);

    registerTransform("sigma_level", CsmSigma.class);
    registerTransform("hybrid_sigma_pressure", CsmSigma.HybridSigmaPressureBuilder.class);

    // further calls to registerTransform are by the user
    userMode = true;
  }

  /**
   * Register a class that implements a Coordinate Transform.
   * 
   * @param transformName name of transform. This name is used in the datasets to identify the transform, eg CF names.
   * @param c class that implements CoordTransBuilderIF.
   */
  public static void registerTransform(String transformName, Class<?> c) {
    if (!(VerticalCTBuilder.class.isAssignableFrom(c)) && !(HorizTransformBuilderIF.class.isAssignableFrom(c)))
      throw new IllegalArgumentException(
          "Class " + c.getName() + " must implement VertTransformBuilderIF or HorizTransformBuilderIF");

    // fail fast - check newInstance works
    try {
      c.newInstance();
    } catch (InstantiationException e) {
      throw new IllegalArgumentException(
          "CoordTransBuilderIF Class " + c.getName() + " cannot instantiate, probably need default Constructor");
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException("CoordTransBuilderIF Class " + c.getName() + " is not accessible");
    }

    // user stuff gets put at top
    if (userMode)
      transformList.add(0, new Transform(transformName, c));
    else
      transformList.add(new Transform(transformName, c));

  }

  /**
   * Register a class that implements a Coordinate Transform.
   * 
   * @param transformName name of transform. This name is used in the datasets to identify the transform, eg CF names.
   * @param className name of class that implements CoordTransBuilderIF.
   * @throws ClassNotFoundException if Class.forName( className) fails
   */
  public static void registerTransform(String transformName, String className) throws ClassNotFoundException {
    Class<?> c = Class.forName(className);
    registerTransform(transformName, c);
  }

  /**
   * Register a class that implements a Coordinate Transform.
   * 
   * @param transformName name of transform. This name is used in the datasets to identify the transform, eg CF names.
   * @param className name of class that implements CoordTransBuilderIF.
   */
  public static void registerTransformMaybe(String transformName, String className) {
    Class<?> c;
    try {
      c = Class.forName(className);
    } catch (ClassNotFoundException e) {
      if (loadWarnings)
        log.warn("Coordinate Transform Class " + className + " not found.");
      return;
    }
    registerTransform(transformName, c);
  }

  private static class Transform {
    String transName;
    Class<?> transClass;

    Transform(String transName, Class<?> transClass) {
      this.transName = transName;
      this.transClass = transClass;
    }
  }

  /**
   * Make a CoordinateTransform object from the parameters in a Coordinate Transform Variable, using an intrinsic or
   * registered CoordTransBuilder.
   * 
   * @param ds enclosing dataset, only used for vertical transforms
   * @param ctv the Coordinate Transform Variable - container for the transform parameters
   * @param parseInfo pass back information about the parsing.
   * @param errInfo pass back error information.
   * @return CoordinateTransform, or null if failure.
   */
  @Nullable
  public static CoordinateTransform.Builder<?> makeCoordinateTransform(NetcdfDataset ds, AttributeContainer ctv,
      Formatter parseInfo, Formatter errInfo) {
    // standard name
    String transform_name = ctv.findAttributeString("transform_name", null);
    if (null == transform_name)
      transform_name = ctv.findAttributeString("Projection_Name", null);

    // these names are from CF - dont want to have to duplicate
    if (null == transform_name)
      transform_name = ctv.findAttributeString(CF.GRID_MAPPING_NAME, null);
    if (null == transform_name)
      transform_name = ctv.findAttributeString(CF.STANDARD_NAME, null);

    // Finally check the units
    if (null == transform_name)
      transform_name = ctv.findAttributeString(CDM.UNITS, null);

    if (null == transform_name) {
      parseInfo.format("**Failed to find Coordinate Transform name from Variable= %s%n", ctv);
      return null;
    }

    transform_name = transform_name.trim();

    // do we have a transform registered for this ?
    Class<?> builderClass = null;
    for (Transform transform : transformList) {
      if (transform.transName.equals(transform_name)) {
        builderClass = transform.transClass;
        break;
      }
    }
    if (null == builderClass) {
      parseInfo.format("**Failed to find CoordTransBuilder name= %s from Variable= %s%n", transform_name, ctv);
      return null;
    }

    // get an instance of that class
    Object builderObject;
    try {
      builderObject = builderClass.newInstance();
    } catch (InstantiationException | IllegalAccessException e) {
      log.error("Cant create new instance " + builderClass.getName(), e);
      return null;
    }

    CoordinateTransform.Builder<?> ct;
    if (builderObject instanceof VerticalCTBuilder) {
      VerticalCTBuilder vertBuilder = (VerticalCTBuilder) builderObject;
      vertBuilder.setErrorBuffer(errInfo);
      ct = vertBuilder.makeVerticalCT(ds, ctv); // TODO: remove dependence on ds?
      if (ct != null) {
        ct.setTransformType(TransformType.Vertical);
      }

    } else if (builderObject instanceof HorizTransformBuilderIF) {
      HorizTransformBuilderIF horizBuilder = (HorizTransformBuilderIF) builderObject;
      horizBuilder.setErrorBuffer(errInfo);
      String units = TransformBuilders.getGeoCoordinateUnits(ds, ctv); // TODO: remove dependence on ds?
      ct = horizBuilder.makeCoordinateTransform(ctv, units);
      if (ct != null) {
        ct.setTransformType(TransformType.Projection);
      }

    } else {
      log.error("Illegal class " + builderClass.getName());
      return null;
    }

    if (ct != null) {
      parseInfo.format(" Made Coordinate transform %s from variable %s: %s%n", transform_name, ctv.getName(),
          builderObject.getClass().getName());
    } else {
      parseInfo.format(" Failed to make Coordinate transform %s from variable %s: %s%n", transform_name, ctv.getName(),
          builderObject.getClass().getName());
    }

    return ct;
  }

  /**
   * Create a "dummy" Coordinate Transform Variable based on the given CoordinateTransform.
   * This creates a scalar Variable with dummy data, and adds the Parameters of the CoordinateTransform
   * as attributes.
   * 
   * @param ds for this dataset
   * @param ct based on the CoordinateTransform
   * @return the Coordinate Transform Variable. You must add it to the dataset.
   */
  public static VariableDS makeDummyTransformVariable(NetcdfDataset ds, CoordinateTransform ct) {
    VariableDS.Builder<?> vb = VariableDS.builder().setName(ct.getName()).setArrayType(ArrayType.CHAR);
    vb.addAttributes(ct.getCtvAttributes());
    vb.addAttribute(new Attribute(_Coordinate.TransformType, ct.getTransformType().toString()));

    // fake data
    Array data = Array.factory(DataType.CHAR, new int[] {}, new char[] {' '});
    vb.setSourceData(data);

    return vb.build(ds.getRootGroup());
  }

  @Nullable
  public static Class<?> getBuilderClassFor(String transformName) {
    // do we have a transform registered for this ?
    for (Transform transform : transformList) {
      if (transform.transName.equals(transformName)) {
        return transform.transClass;
      }
    }
    return null;
  }

  /**
   * Make a Projection object from the parameters in a CoverageTransform
   *
   * @param errInfo pass back error information.
   * @return CoordinateTransform, or null if failure.
   * @deprecated use CoverageTransform.makeProjection().
   */
  @Deprecated
  @Nullable
  public static Projection makeProjection(CoverageTransform gct, Formatter errInfo) {
    // standard name
    String transformName = gct.attributes().findAttributeString(CF.GRID_MAPPING_NAME, null);

    if (null == transformName) {
      errInfo.format("**Failed to find Coordinate Transform name from GridCoordTransform= %s%n", gct);
      return null;
    }

    transformName = transformName.trim();

    // do we have a transform registered for this ?
    Class<?> builderClass = CoordTransformFactory.getBuilderClassFor(transformName);
    if (null == builderClass) {
      errInfo.format("**Failed to find CoordTransBuilder name= %s from GridCoordTransform= %s%n", transformName, gct);
      return null;
    }

    // get an instance of that class
    HorizTransformBuilderIF builder;
    try {
      builder = (HorizTransformBuilderIF) builderClass.newInstance();
    } catch (InstantiationException | IllegalAccessException e) {
      log.error("Cant create new instance " + builderClass.getName(), e);
      return null;
    }

    String units = gct.attributes().findAttributeString(CDM.UNITS, null);
    builder.setErrorBuffer(errInfo);
    ProjectionCT.Builder<?> ct = builder.makeCoordinateTransform(gct.attributes(), units);
    if (ct == null) {
      return null;
    }

    return ct.build().getProjection();
  }

}
