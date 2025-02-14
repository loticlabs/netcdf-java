package examples.runtime;

import ucar.array.ArrayType;
import ucar.array.InvalidRangeException;
import ucar.nc2.Dimension;
import ucar.nc2.iosp.NetcdfFileFormat;
import ucar.nc2.write.Nc4Chunking;
import ucar.nc2.write.Nc4ChunkingStrategy;
import ucar.nc2.write.NetcdfFormatWriter;
import java.io.IOException;

public class netcdf4ClibraryTutorial {

  public static void writingcdf(Nc4Chunking.Strategy typeAsStrategy, int deflateLevelAsInt,
      boolean shuffleBoolean, NetcdfFileFormat format, String locationAsString,
      ucar.array.Array<?> dataArray) throws IOException, InvalidRangeException {
    // create new netcdf4 file with chunker: enter null for default chunking algorithm
    Nc4Chunking chunker =
        Nc4ChunkingStrategy.factory(typeAsStrategy, deflateLevelAsInt, shuffleBoolean);

    NetcdfFormatWriter.Builder writerb =
        NetcdfFormatWriter.createNewNetcdf4(format, locationAsString, chunker);
    writerb.addDimension(Dimension.builder().setName("vdim").setIsUnlimited(true).build());
    writerb.addVariable("v", (ArrayType) null, "vdim");

    try (NetcdfFormatWriter writer = writerb.build()) {
      writer.config().forVariable("v").withArray(dataArray).write();
    }
  }

  public static void chunkingOverride(int deflateLevelAsInt, boolean shuffleBoolean) {
    // set deflate > 0 to compress
    // set shuffleBoolean to true for the shuffle filter
    // pass Nc4Chunking.Strategy.standard to run Nc4ChunkingDefault
    Nc4Chunking chunker = Nc4ChunkingStrategy.factory(Nc4Chunking.Strategy.standard,
        deflateLevelAsInt, shuffleBoolean);
  }
}
