/*
 * Copyright (c) 1998-2018 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */
package ucar.nc2;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.ma2.*;
import ucar.ma2.StructureMembers.Member;
import ucar.unidata.util.test.TestDir;
import java.io.*;
import java.lang.invoke.MethodHandles;
import java.util.*;

/** Test reading record data */
public class TestStructureReading {
  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  NetcdfFile ncfile;

  @Before
  public void setUp() throws Exception {
    // testWriteRecord is 1 dimensional (nc2 record dimension)
    ncfile = NetcdfFiles.open(TestDir.cdmLocalTestDataDir + "testWriteRecord.nc", -1, null,
        NetcdfFile.IOSP_MESSAGE_ADD_RECORD_STRUCTURE);
    System.out.printf("TestStructure %s%n", ncfile.getLocation());
  }

  @After
  public void tearDown() throws Exception {
    ncfile.close();
  }

  @Test
  public void testNames() {
    List<Variable> vars = ncfile.getVariables();
    String[] trueNames = {"rh", "T", "lat", "lon", "time", "recordvarTest", "record"};
    for (int i = 0; i < vars.size(); i++) {
      Assert.assertEquals("Checking names", trueNames[i], vars.get(i).getFullName());
    }

    Structure record = (Structure) ncfile.findVariable("record");
    assert record != null;

    vars = record.getVariables();
    String[] trueRecordNames = {"record.rh", "record.T", "record.time", "record.recordvarTest"};
    for (int i = 0; i < vars.size(); i++) {
      Assert.assertEquals("Checking record names", trueRecordNames[i], vars.get(i).getFullName());
    }

    Variable time = ncfile.findVariable("record.time");
    assert time != null;

    Variable time2 = record.findVariable("time");
    assert time2 != null;

    Assert.assertEquals(time, time2);
  }

  @Test
  public void testReadStructureCountBytesRead() throws IOException, InvalidRangeException {

    Structure record = (Structure) ncfile.findVariable("record");
    assert record != null;

    // read all at once
    long totalAll = 0;
    Array dataAll = record.read();
    IndexIterator iter = dataAll.getIndexIterator();
    while (iter.hasNext()) {
      StructureData sd = (StructureData) iter.next();

      for (Member m : sd.getMembers()) {
        Array data = sd.getArray(m);
        totalAll += data.getSize() * m.getDataType().getSize();
      }
    }
    Assert.assertEquals("Total bytes read", 304, totalAll);

    // read one at a time
    int numrecs = record.getShape()[0];
    long totalOne = 0;
    for (int i = 0; i < numrecs; i++) {
      StructureData sd = record.readStructure(i);

      for (Member m : sd.getMembers()) {
        Array data = sd.getArray(m);
        totalOne += data.getSize() * m.getDataType().getSize();
      }
    }
    Assert.assertEquals("testReadStructureCountBytesRead", totalAll, totalOne);

    // read with the iterator
    long totalIter = 0;
    try (StructureDataIterator iter2 = record.getStructureIterator()) {
      while (iter2.hasNext()) {
        StructureData sd = iter2.next();
        for (StructureMembers.Member m : sd.getMembers()) {
          Array data = sd.getArray(m);
          totalIter += data.getSize() * m.getDataType().getSize();
        }
      }
    }
    Assert.assertEquals("Bytes through iteration", totalIter, totalOne);
  }

  @Test
  public void testN3ReadStructureCheckValues() throws IOException, InvalidRangeException {
    Structure record = (Structure) ncfile.findVariable("record");
    assert record != null;

    // read all at once
    int recnum = 0;
    Array dataAll = record.read();
    IndexIterator iter = dataAll.getIndexIterator();
    while (iter.hasNext()) {
      StructureData s = (StructureData) iter.next();
      Array rh = s.getArray("rh");
      assert (rh instanceof ArrayInt.D2);
      checkValues(rh, recnum); // check the values are right
      recnum++;
    }

    // read one at a time
    int numrecs = record.getShape()[0];
    for (int i = 0; i < numrecs; i++) {
      StructureData s = record.readStructure(i);
      Array rh = s.getArray("rh");
      assert (rh instanceof ArrayInt.D2);
      checkValues(rh, i); // check the values are right
    }

    // read using iterator
    recnum = 0;
    try (StructureDataIterator iter2 = record.getStructureIterator()) {
      while (iter2.hasNext()) {
        StructureData s = iter2.next();
        Array rh = s.getArray("rh");
        assert (rh instanceof ArrayInt.D2);
        checkValues(rh, recnum); // check the values are right
        recnum++;
      }
    }
  }

  @Test
  public void testReadBothWaysV3mode() throws IOException {
    // readBothWays(TestAll.testdataDir+"grid/netcdf/mm5/n040.nc");
    readBothWays(TestDir.cdmLocalTestDataDir + "testWriteRecord.nc");
    // readBothWays(TestAll.testdataDir+"station/ldm-old/2004061915_metar.nc");

    // System.out.println("*** testReadBothWaysV3mode ok");
  }

  private void readBothWays(String filename) throws IOException {
    NetcdfFile ncfile = NetcdfFiles.open(filename);
    ncfile.sendIospMessage(NetcdfFile.IOSP_MESSAGE_ADD_RECORD_STRUCTURE);
    // System.out.println(ncfile);
    ncfile.close();

    ncfile = NetcdfFiles.open(filename);
    // System.out.println(ncfile);
    ncfile.close();
  }

  private void checkValues(Array rh, int recnum) {
    assert (rh instanceof ArrayInt.D2) : rh.getClass().getName();

    // check the values are right
    ArrayInt.D2 rha = (ArrayInt.D2) rh;
    int[] shape = rha.getShape();
    for (int j = 0; j < shape[0]; j++) {
      for (int k = 0; k < shape[1]; k++) {
        int want = 20 * recnum + 4 * j + k + 1;
        int val = rha.get(j, k);
        Assert.assertEquals(" " + recnum + " " + j + " " + k + " " + want + " " + val, want, val);
      }
    }
  }
}
