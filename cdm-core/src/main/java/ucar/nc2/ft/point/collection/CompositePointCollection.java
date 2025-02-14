/*
 * Copyright (c) 1998-2018 John Caron and University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */
package ucar.nc2.ft.point.collection;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nonnull;
import thredds.inventory.TimedCollection;
import ucar.nc2.Attribute;
import ucar.nc2.AttributeContainer;
import ucar.nc2.VariableSimpleIF;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.ft.DsgFeatureCollection;
import ucar.nc2.ft.FeatureDatasetFactoryManager;
import ucar.nc2.ft.FeatureDatasetPoint;
import ucar.nc2.ft.PointFeature;
import ucar.nc2.ft.PointFeatureCollection;
import ucar.nc2.ft.PointFeatureIterator;
import ucar.nc2.ft.point.PointCollectionImpl;
import ucar.nc2.ft.point.PointIteratorAbstract;
import ucar.nc2.time.CalendarDateRange;
import ucar.nc2.time.CalendarDateUnit;
import ucar.unidata.geoloc.LatLonRect;

/**
 * PointCollection composed of other PointCollections
 *
 * @author caron
 * @since May 19, 2009
 */
public class CompositePointCollection extends PointCollectionImpl implements UpdateableCollection {
  private final TimedCollection pointCollections;
  protected List<VariableSimpleIF> dataVariables;
  protected AttributeContainer globalAttributes;

  protected CompositePointCollection(String name, CalendarDateUnit timeUnit, String altUnits,
      TimedCollection pointCollections) {
    super(name, timeUnit, altUnits);
    this.pointCollections = pointCollections;
  }

  private void readMetadata() {
    // must open a prototype in order to get the data variable
    TimedCollection.Dataset td = pointCollections.getPrototype();
    if (td == null)
      throw new RuntimeException("No datasets in the collection");

    Formatter errlog = new Formatter();
    try (FeatureDatasetPoint openDataset =
        (FeatureDatasetPoint) FeatureDatasetFactoryManager.open(FeatureType.POINT, td.getLocation(), null, errlog)) {
      if (openDataset != null) {
        dataVariables = openDataset.getDataVariables();
        globalAttributes = openDataset.attributes();
      }
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  public List<VariableSimpleIF> getDataVariables() {
    if (dataVariables == null)
      readMetadata();
    return dataVariables;
  }

  public AttributeContainer attributes() {
    return globalAttributes;
  }

  /** @deprecated use attributes() */
  @Deprecated
  public List<Attribute> getGlobalAttributes() {
    if (globalAttributes == null)
      readMetadata();
    return ImmutableList.copyOf(globalAttributes);
  }

  @Override
  @Nonnull
  public PointFeatureCollection subset(LatLonRect boundingBox, CalendarDateRange dateRange) {
    if ((dateRange == null) && (boundingBox == null))
      return this;
    else if (dateRange == null)
      return new PointCollectionSubset(this, boundingBox, null);
    else {
      CompositePointCollection dateSubset =
          new CompositePointCollection(name, getTimeUnit(), getAltUnits(), pointCollections.subset(dateRange));
      return new PointCollectionSubset(dateSubset, boundingBox, dateRange);
    }
  }

  @Override
  public PointFeatureIterator getPointFeatureIterator() {
    return new CompositePointFeatureIterator();
  }

  @Override
  public CalendarDateRange update() throws IOException {
    return pointCollections.update();
  }

  private class CompositePointFeatureIterator extends PointIteratorAbstract {
    private boolean finished;
    private final Iterator<TimedCollection.Dataset> iter;
    private FeatureDatasetPoint currentDataset;
    private PointFeatureIterator pfIter;

    CompositePointFeatureIterator() {
      iter = pointCollections.getDatasets().iterator();
    }

    private PointFeatureIterator getNextIterator() throws IOException {
      if (!iter.hasNext())
        return null;
      TimedCollection.Dataset td = iter.next();

      Formatter errlog = new Formatter();
      currentDataset =
          (FeatureDatasetPoint) FeatureDatasetFactoryManager.open(FeatureType.POINT, td.getLocation(), null, errlog);
      if (currentDataset == null)
        throw new IllegalStateException("Cant open FeatureDatasetPoint " + td.getLocation());
      if (CompositeDatasetFactory.debug)
        System.out.printf("CompositePointFeatureIterator open dataset %s%n", td.getLocation());

      List<DsgFeatureCollection> fcList = currentDataset.getPointFeatureCollectionList();
      PointFeatureCollection pc = (PointFeatureCollection) fcList.get(0);
      return pc.getPointFeatureIterator();
    }

    @Override
    public boolean hasNext() {
      try {
        if (pfIter == null) {
          pfIter = getNextIterator();
          if (pfIter == null) {
            close();
            return false;
          }
        }

        if (!pfIter.hasNext()) {
          pfIter.close();
          if (CompositeDatasetFactory.debug)
            System.out.printf("CompositePointFeatureIterator open dataset %s%n", currentDataset.getLocation());
          currentDataset.close();

          pfIter = getNextIterator();
          return hasNext();
        }

        return true;
      } catch (IOException ioe) {
        throw new RuntimeException(ioe);
      }
    }

    @Override
    public PointFeature next() {
      return pfIter.next();
    }

    @Override
    public void close() {
      if (finished)
        return;

      if (pfIter != null)
        pfIter.close();
      finishCalcBounds();

      if (currentDataset != null)
        try {
          currentDataset.close();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }

      finished = true;
    }
  }
}
