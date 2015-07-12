/* Copyright */
package ucar.nc2.ft2.coverage;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Describes a subset of a GridCoverage.
 * Coordinate values only, no indices.
 *
 * @author caron
 * @since 5/6/2015
 */
public class CoverageSubset {
  public static final String latlonBB = "latlonBB";     // value = LatLonRect
  public static final String projBB = "projBB";         // value = ProjRect
  public static final String horizStride = "horizStride";  // value = Integer
  public static final String vertCoord = "vertCoord";   // value = double
  public static final String vertIndex = "vertIndex";   // value = integer
  public static final String dateRange = "dateRange";   // value = CalendarDateRange
  public static final String date = "time";             // value = CalendarDate
  public static final String timeWindow = "timeWindow"; // value = TimeDuration
  public static final String timeStride = "timeStride"; // value = Integer
  public static final String allTimes = "allTimes";     // value = Boolean
  public static final String latestTime = "latestTime"; // value = Boolean
  public static final String runtime = "runtime";       // value = CalendarDate
  public static final String ensCoord = "ensCoord";     // value = double ??

  private final Map<String, Object> req = new HashMap<>();

  public Set<Map.Entry<String, Object>> getEntries() {
    return req.entrySet();
  }
  public Set<String> getKeys() {
    return req.keySet();
  }

  public CoverageSubset set(String key, Object value) {
    req.put(key, value);
    return this;
  }

  public Object get(String key) {
     return req.get(key);
   }

  public boolean isTrue(String key) {
     Boolean val = (Boolean) req.get(key);
    return (val != null) && val;
   }

  public Double getDouble(String key) {
    Object val = req.get(key);
    if (val == null) return null;
    Double dval;
    if (val instanceof Double) dval = (Double) val;
    else dval = Double.parseDouble((String) val);
    return dval;
  }

  public Integer getInteger(String key) {
    Object val = req.get(key);
    if (val == null) return null;
    Integer dval;
    if (val instanceof Integer) dval = (Integer) val;
    else dval = Integer.parseInt((String) val);
    return dval;
  }

}