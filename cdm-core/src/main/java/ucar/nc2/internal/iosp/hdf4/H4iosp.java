/*
 * Copyright (c) 1998-2018 John Caron and University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */
package ucar.nc2.internal.iosp.hdf4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import ucar.array.Arrays;
import ucar.array.ArraysConvert;
import ucar.array.Storage;
import ucar.array.StructureData;
import ucar.ma2.Array;
import ucar.ma2.ArrayStructure;
import ucar.ma2.ArrayStructureBB;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Section;
import ucar.ma2.StructureMembers;
import ucar.nc2.Group;
import ucar.nc2.Structure;
import ucar.nc2.Variable;
import ucar.nc2.constants.DataFormatType;
import ucar.nc2.iosp.AbstractIOServiceProvider;
import ucar.nc2.iosp.IospHelper;
import ucar.nc2.iosp.Layout;
import ucar.nc2.iosp.LayoutBB;
import ucar.nc2.iosp.LayoutBBTiled;
import ucar.nc2.iosp.LayoutRegular;
import ucar.nc2.iosp.LayoutSegmented;
import ucar.nc2.iosp.LayoutTiled;
import ucar.nc2.util.CancelTask;
import ucar.nc2.util.IO;
import ucar.unidata.io.PositioningDataInputStream;
import ucar.unidata.io.RandomAccessFile;
import javax.annotation.Nullable;

/** HDF4 iosp */
public class H4iosp extends AbstractIOServiceProvider {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(H4iosp.class);

  private H4header header;
  private Charset valueCharset;

  @Override
  public boolean isValidFile(RandomAccessFile raf) throws IOException {
    return H4header.isValidFile(raf);
  }

  @Override
  public String getFileTypeId() {
    if (header != null && header.isEos()) {
      return "HDF4-EOS";
    }
    return DataFormatType.HDF4.getDescription();
  }

  @Override
  public String getFileTypeVersion() {
    return header.version;
  }

  @Override
  public String getFileTypeDescription() {
    return "Hierarchical Data Format, version 4";
  }

  /**
   * Return header for reading netcdf file.
   * Create it if it's not already created.
   * 
   * @return header for reading HDF4 file.
   */
  private H4header getHeader() {
    if (header == null) {
      header = new H4header(this);
    }
    return header;
  }

  @Override
  public void build(RandomAccessFile raf, Group.Builder rootGroup, CancelTask cancelTask) throws IOException {
    super.open(raf, rootGroup.getNcfile(), cancelTask);

    raf.order(RandomAccessFile.BIG_ENDIAN);
    header = new H4header(this);
    header.read(raf, rootGroup, null);
  }

  @Override
  public Array readData(Variable v, Section section) throws IOException, InvalidRangeException {
    if (v instanceof Structure) {
      return readStructureData((Structure) v, section);
    }

    Object data = readDataObject(v, section);
    if (data != null) {
      return Array.factory(v.getDataType(), section.getShape(), data);
    }
    throw new IllegalStateException();
  }

  @Override
  public ucar.array.Array<?> readArrayData(Variable v, ucar.array.Section section)
      throws IOException, ucar.array.InvalidRangeException {
    if (v instanceof Structure) {
      return readStructureDataArray((Structure) v, section);
    }

    Object data = null;
    try {
      Section oldSection = ArraysConvert.convertSection(section);
      data = readDataObject(v, oldSection);
    } catch (InvalidRangeException e) {
      e.printStackTrace();
    }

    if (data != null) {
      return Arrays.factory(v.getArrayType(), section.getShape(), data);
    }
    throw new IllegalStateException();
  }

  private Object readDataObject(Variable v, Section section) throws IOException, InvalidRangeException {
    H4header.Vinfo vinfo = (H4header.Vinfo) v.getSPobject();
    DataType dataType = v.getDataType();
    vinfo.setLayoutInfo(this.ncfile); // make sure needed info is present

    // make sure section is complete
    section = Section.fill(section, v.getShape());

    if (vinfo.hasNoData) {
      Object arr = (vinfo.fillValue == null) ? IospHelper.makePrimitiveArray((int) section.computeSize(), dataType)
          : IospHelper.makePrimitiveArray((int) section.computeSize(), dataType, vinfo.fillValue);
      if (dataType == DataType.CHAR) {
        arr = IospHelper.convertByteToChar((byte[]) arr);
      }
      return arr;
    }

    if (!vinfo.isCompressed) {
      if (!vinfo.isLinked && !vinfo.isChunked) {
        Layout layout = new LayoutRegular(vinfo.start, v.getElementSize(), v.getShape(), section);
        return IospHelper.readDataFill(raf, layout, dataType, vinfo.fillValue, null);

      } else if (vinfo.isLinked) {
        Layout layout = new LayoutSegmented(vinfo.segPos, vinfo.segSize, v.getElementSize(), v.getShape(), section);
        return IospHelper.readDataFill(raf, layout, dataType, vinfo.fillValue, null);

      } else if (vinfo.isChunked) {
        H4ChunkIterator chunkIterator = new H4ChunkIterator(vinfo);
        Layout layout = new LayoutTiled(chunkIterator, vinfo.chunkSize, v.getElementSize(), section);
        return IospHelper.readDataFill(raf, layout, dataType, vinfo.fillValue, null);
      }

    } else {
      if (!vinfo.isLinked && !vinfo.isChunked) {
        Layout index = new LayoutRegular(0, v.getElementSize(), v.getShape(), section);
        InputStream is = getCompressedInputStream(vinfo);
        PositioningDataInputStream dataSource = new PositioningDataInputStream(is);
        return IospHelper.readDataFill(dataSource, index, dataType, vinfo.fillValue);

      } else if (vinfo.isLinked) {
        Layout index = new LayoutRegular(0, v.getElementSize(), v.getShape(), section);
        InputStream is = getLinkedCompressedInputStream(vinfo);
        PositioningDataInputStream dataSource = new PositioningDataInputStream(is);
        return IospHelper.readDataFill(dataSource, index, dataType, vinfo.fillValue);

      } else if (vinfo.isChunked) {
        LayoutBBTiled.DataChunkIterator chunkIterator = new H4CompressedChunkIterator(vinfo);
        LayoutBB layout = new LayoutBBTiled(chunkIterator, vinfo.chunkSize, v.getElementSize(), section);
        return IospHelper.readDataFill(layout, dataType, vinfo.fillValue);
      }
    }
    throw new IllegalStateException();
  }

  /**
   * Structures must be fixed sized
   *
   * @param s the record structure
   * @param section the record range to read
   * @return an ArrayStructure, with all the data read in.
   * @throws IOException on error
   * @throws InvalidRangeException if invalid section
   */
  private ArrayStructure readStructureData(Structure s, Section section) throws IOException, InvalidRangeException {
    H4header.Vinfo vinfo = (H4header.Vinfo) s.getSPobject();
    vinfo.setLayoutInfo(this.ncfile); // make sure needed info is present
    int recsize = vinfo.elemSize;

    // create the ArrayStructure
    StructureMembers members = s.makeStructureMembers();
    for (StructureMembers.Member m : members.getMembers()) {
      Variable v2 = s.findVariable(m.getName());
      H4header.Minfo minfo = (H4header.Minfo) v2.getSPobject();
      m.setDataParam(minfo.offset);
    }
    members.setStructureSize(recsize);
    ArrayStructureBB structureArray = new ArrayStructureBB(members, section.getShape()); // LOOK subset

    // loop over records
    byte[] result = structureArray.getByteBuffer().array();

    /*
     * if (vinfo.isChunked) {
     * InputStream is = getChunkedInputStream(vinfo);
     * PositioningDataInputStream dataSource = new PositioningDataInputStream(is);
     * Layout layout = new LayoutRegular(vinfo.start, recsize, s.getShape(), section);
     * IospHelper.readData(dataSource, layout, DataType.STRUCTURE, result);
     */

    if (!vinfo.isLinked && !vinfo.isCompressed) {
      Layout layout = new LayoutRegular(vinfo.start, recsize, s.getShape(), section);
      IospHelper.readData(raf, layout, DataType.STRUCTURE, result, null, true);

      /*
       * option 1
       * } else if (vinfo.isLinked && !vinfo.isCompressed) {
       * Layout layout = new LayoutSegmented(vinfo.segPos, vinfo.segSize, recsize, s.getShape(), section);
       * IospHelper.readData(raf, layout, DataType.STRUCTURE, result, -1);
       */

      // option 2
    } else if (vinfo.isLinked && !vinfo.isCompressed) {
      InputStream is = new LinkedInputStream(vinfo);
      PositioningDataInputStream dataSource = new PositioningDataInputStream(is);
      Layout layout = new LayoutRegular(0, recsize, s.getShape(), section);
      IospHelper.readData(dataSource, layout, DataType.STRUCTURE, result);

    } else if (!vinfo.isLinked && vinfo.isCompressed) {
      InputStream is = getCompressedInputStream(vinfo);
      PositioningDataInputStream dataSource = new PositioningDataInputStream(is);
      Layout layout = new LayoutRegular(0, recsize, s.getShape(), section);
      IospHelper.readData(dataSource, layout, DataType.STRUCTURE, result);

    } else if (vinfo.isLinked && vinfo.isCompressed) {
      InputStream is = getLinkedCompressedInputStream(vinfo);
      PositioningDataInputStream dataSource = new PositioningDataInputStream(is);
      Layout layout = new LayoutRegular(0, recsize, s.getShape(), section);
      IospHelper.readData(dataSource, layout, DataType.STRUCTURE, result);

    } else {
      throw new IllegalStateException();
    }

    return structureArray;
  }

  private ucar.array.Array<ucar.array.StructureData> readStructureDataArray(Structure s, ucar.array.Section section)
      throws IOException, ucar.array.InvalidRangeException {
    H4header.Vinfo vinfo = (H4header.Vinfo) s.getSPobject();
    vinfo.setLayoutInfo(this.ncfile); // make sure needed info is present
    int recsize = vinfo.elemSize;

    // create the StructureMembers
    ucar.array.StructureMembers.Builder membersb = s.makeStructureMembersBuilder();
    for (ucar.array.StructureMembers.MemberBuilder m : membersb.getStructureMembers()) {
      Variable v2 = s.findVariable(m.getName());
      H4header.Minfo minfo = (H4header.Minfo) v2.getSPobject();
      m.setOffset(minfo.offset);
    }
    membersb.setStructureSize(recsize);

    int nrecs = (int) section.computeSize();
    byte[] result = new byte[(int) (nrecs * recsize)];

    try {
      Section oldSection = ArraysConvert.convertSection(section);
      if (!vinfo.isLinked && !vinfo.isCompressed) {
        Layout layout = new LayoutRegular(vinfo.start, recsize, s.getShape(), oldSection);
        IospHelper.readData(raf, layout, DataType.STRUCTURE, result, null, true);

        // option 2
      } else if (vinfo.isLinked && !vinfo.isCompressed) {
        InputStream is = new LinkedInputStream(vinfo);
        PositioningDataInputStream dataSource = new PositioningDataInputStream(is);
        Layout layout = new LayoutRegular(0, recsize, s.getShape(), oldSection);
        IospHelper.readData(dataSource, layout, DataType.STRUCTURE, result);

      } else if (!vinfo.isLinked && vinfo.isCompressed) {
        InputStream is = getCompressedInputStream(vinfo);
        PositioningDataInputStream dataSource = new PositioningDataInputStream(is);
        Layout layout = new LayoutRegular(0, recsize, s.getShape(), oldSection);
        IospHelper.readData(dataSource, layout, DataType.STRUCTURE, result);

      } else if (vinfo.isLinked && vinfo.isCompressed) {
        InputStream is = getLinkedCompressedInputStream(vinfo);
        PositioningDataInputStream dataSource = new PositioningDataInputStream(is);
        Layout layout = new LayoutRegular(0, recsize, s.getShape(), oldSection);
        IospHelper.readData(dataSource, layout, DataType.STRUCTURE, result);

      } else {
        throw new IllegalStateException();
      }
    } catch (InvalidRangeException e) {
      throw new ucar.array.InvalidRangeException(e);
    }

    ucar.array.StructureMembers members = membersb.build();
    Storage<StructureData> storage =
        new ucar.array.StructureDataStorageBB(members, ByteBuffer.wrap(result), (int) section.computeSize());
    return new ucar.array.StructureDataArray(members, section.getShape(), storage);
  }

  public String toStringDebug(Object o) {
    if (o instanceof Variable) {
      Variable v = (Variable) o;
      H4header.Vinfo vinfo = (H4header.Vinfo) v.getSPobject();
      return (vinfo != null) ? vinfo.toString() : "";
    }
    return null;
  }

  private InputStream getCompressedInputStream(H4header.Vinfo vinfo) throws IOException {
    // probably could construct an input stream from a channel from a raf for now, just read it in.
    byte[] buffer = new byte[vinfo.length];
    raf.seek(vinfo.start);
    raf.readFully(buffer);
    ByteArrayInputStream in = new ByteArrayInputStream(buffer);
    return new java.util.zip.InflaterInputStream(in);
  }

  private InputStream getLinkedCompressedInputStream(H4header.Vinfo vinfo) {
    return new java.util.zip.InflaterInputStream(new LinkedInputStream(vinfo));
  }

  private class LinkedInputStream extends InputStream {
    byte[] buffer;

    int nsegs;
    long[] segPosA;
    int[] segSizeA;

    int segno = -1;
    int segpos;
    int segSize;
    // H4header.Vinfo vinfo;

    LinkedInputStream(H4header.Vinfo vinfo) {
      segPosA = vinfo.segPos;
      segSizeA = vinfo.segSize;
      nsegs = segSizeA.length;
    }

    LinkedInputStream(H4header.SpecialLinked linked) throws IOException {
      List<H4header.TagLinkedBlock> linkedBlocks = linked.getLinkedDataBlocks();
      nsegs = linkedBlocks.size();
      segPosA = new long[nsegs];
      segSizeA = new int[nsegs];
      int count = 0;
      for (H4header.TagLinkedBlock tag : linkedBlocks) {
        segPosA[count] = tag.offset;
        segSizeA[count] = tag.length;
        count++;
      }
    }

    private boolean readSegment() throws IOException {
      segno++;
      if (segno == nsegs)
        return false;

      segSize = segSizeA[segno];
      while (segSize == 0) { // for some reason may have a 0 length segment
        segno++;
        if (segno == nsegs)
          return false;
        segSize = segSizeA[segno];
      }

      buffer = new byte[segSize]; // Look: could do this in buffer size 4096 to save memory
      raf.seek(segPosA[segno]);
      raf.readFully(buffer);
      segpos = 0;

      return true;
    }

    public int read() throws IOException {
      if (segpos == segSize) {
        boolean ok = readSegment();
        if (!ok)
          return -1;
      }

      int b = buffer[segpos] & 0xff;
      segpos++;
      return b;
    }
  }

  private static class H4ChunkIterator implements LayoutTiled.DataChunkIterator {
    List<H4header.DataChunk> chunks;
    int chunkNo;

    H4ChunkIterator(H4header.Vinfo vinfo) {
      this.chunks = vinfo.chunks;
      this.chunkNo = 0;
    }

    public boolean hasNext() {
      return chunkNo < chunks.size();
    }

    public LayoutTiled.DataChunk next() {
      H4header.DataChunk chunk = chunks.get(chunkNo);
      H4header.TagData chunkData = chunk.data;
      chunkNo++;

      return new LayoutTiled.DataChunk(chunk.origin, chunkData.offset);
    }
  }

  private class H4CompressedChunkIterator implements LayoutBBTiled.DataChunkIterator {
    List<H4header.DataChunk> chunks;
    int chunkNo;

    H4CompressedChunkIterator(H4header.Vinfo vinfo) {
      this.chunks = vinfo.chunks;
      this.chunkNo = 0;
    }

    public boolean hasNext() {
      return chunkNo < chunks.size();
    }

    public LayoutBBTiled.DataChunk next() {
      H4header.DataChunk chunk = chunks.get(chunkNo);
      H4header.TagData chunkData = chunk.data;
      assert (chunkData.ext_type == TagEnum.SPECIAL_COMP);
      chunkNo++;

      return new DataChunk(chunk.origin, chunkData.compress);
    }
  }

  private class DataChunk implements LayoutBBTiled.DataChunk {
    private final int[] offset; // offset index of this chunk, reletive to entire array
    private final H4header.SpecialComp compress;
    private ByteBuffer bb; // the data is placed into here

    DataChunk(int[] offset, H4header.SpecialComp compress) {
      this.offset = offset;
      this.compress = compress;
    }

    public int[] getOffset() {
      return offset;
    }

    public ByteBuffer getByteBuffer() throws IOException {
      if (bb == null) {
        // read compressed data in
        H4header.TagData cdata = compress.getDataTag();
        InputStream in;

        // compressed data stored in one place
        if (cdata.linked == null) {
          byte[] cbuffer = new byte[cdata.length];
          raf.seek(cdata.offset);
          raf.readFully(cbuffer);
          in = new ByteArrayInputStream(cbuffer);

        } else { // or compressed data stored in linked storage
          in = new LinkedInputStream(cdata.linked);
        }

        // uncompress it
        if (compress.compress_type == TagEnum.COMP_CODE_DEFLATE) {
          // read the stream in and uncompress
          InputStream zin = new java.util.zip.InflaterInputStream(in);
          ByteArrayOutputStream out = new ByteArrayOutputStream(compress.uncomp_length);
          IO.copy(zin, out);
          byte[] buffer = out.toByteArray();
          bb = ByteBuffer.wrap(buffer);

        } else if (compress.compress_type == TagEnum.COMP_CODE_NONE) {
          // just read the stream in
          ByteArrayOutputStream out = new ByteArrayOutputStream(compress.uncomp_length);
          IO.copy(in, out);
          byte[] buffer = out.toByteArray();
          bb = ByteBuffer.wrap(buffer);
        } else {
          throw new IllegalStateException("unknown compression type =" + compress.compress_type);
        }
      }

      return bb;
    }

  }

  @Override
  public void reacquire() throws IOException {
    super.reacquire();
    getHeader().raf = this.raf;
  }

  public Object sendIospMessage(Object message) {
    if (message instanceof Charset) {
      setValueCharset((Charset) message);
    }
    if (message.toString().equals("header")) {
      return getHeader();
    }
    return super.sendIospMessage(message);
  }

  /**
   * Return {@link Charset value charset} if it was defined. Definition of charset
   * occurs by sending a charset as a message using the {@link #sendIospMessage}
   * method.
   * 
   * @return {@link Charset value charset} if it was defined.
   */
  protected Optional<Charset> getValueCharset() {
    return Optional.ofNullable(valueCharset);
  }

  /**
   * Define {@link Charset value charset}.
   * 
   * @param charset may be null.
   */
  protected void setValueCharset(@Nullable Charset charset) {
    this.valueCharset = charset;
  }
}
