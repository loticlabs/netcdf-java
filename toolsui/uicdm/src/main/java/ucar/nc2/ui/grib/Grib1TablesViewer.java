/*
 * Copyright (c) 1998-2018 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */
package ucar.nc2.ui.grib;

import ucar.nc2.grib.GribResourceReader;
import ucar.nc2.grib.grib1.tables.Grib1Customizer;
import ucar.nc2.grib.grib1.Grib1Parameter;
import ucar.nc2.grib.grib1.tables.Grib1ParamTableReader;
import ucar.nc2.grib.grib1.tables.Grib1ParamTables;
import ucar.nc2.ui.dialog.Grib1TableCompareDialog;
import ucar.ui.widget.*;
import ucar.nc2.ui.dialog.Grib1TableDialog;
import ucar.ui.widget.PopupMenu;
import ucar.nc2.units.SimpleUnit;
import ucar.nc2.util.IO;
import ucar.nc2.internal.wmo.CommonCodeTable;
import ucar.nc2.internal.wmo.Util;
import ucar.util.prefs.PreferencesExt;
import ucar.ui.prefs.BeanTable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;

/**
 * Show Grib1 Tables
 *
 * @author caron
 * @since Sep 26, 2010
 */
public class Grib1TablesViewer extends JPanel {

  private final PreferencesExt prefs;

  private final BeanTable<TableBean> codeTable;
  private final BeanTable<EntryBean> entryTable;
  private final JSplitPane split;

  private final TextHistoryPane infoTA;
  private final IndependentWindow infoWindow;

  private Grib1TableDialog showTableDialog;
  private Grib1TableCompareDialog compareTableDialog;

  public Grib1TablesViewer(PreferencesExt prefs, JPanel buttPanel) {
    this.prefs = prefs;

    codeTable = new BeanTable<>(TableBean.class, (PreferencesExt) prefs.node("CodeTableBean"), false);
    codeTable.addListSelectionListener(e -> {
      TableBean csb = codeTable.getSelectedBean();
      if (csb != null) {
        setEntries(csb.table);
      }
    });
    ucar.ui.widget.PopupMenu varPopup = new PopupMenu(codeTable.getJTable(), "Options");
    varPopup.addAction("Show File contents", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        TableBean bean = codeTable.getSelectedBean();
        if (bean != null) {
          showFile(bean);
        }
      }
    });
    varPopup.addAction("Compare to default WMO table", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        TableBean bean = codeTable.getSelectedBean();
        if (bean == null)
          return;
        initTableDialog();

        Grib1ParamTableReader wmo = new Grib1ParamTables().getParameterTable(0, 0, bean.getVersion());
        if (wmo == null) {
          infoTA.setText("Cant find WMO version " + bean.getVersion());
          infoWindow.show();
          return;
        }

        compareTableDialog.setTable1(new TableBean(wmo));
        compareTableDialog.setTable2(bean);
        compareTableDialog.setVisible(true);
      }
    });
    varPopup.addAction("Compare two tables", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        TableBean bean = codeTable.getSelectedBean();
        if (bean == null)
          return;
        initTableDialog();

        List<TableBean> list = codeTable.getSelectedBeans();
        if (list.size() == 2) {
          TableBean bean1 = list.get(0);
          TableBean bean2 = list.get(1);
          compareTableDialog.setTable1(bean1);
          compareTableDialog.setTable2(bean2);
          compareTableDialog.setVisible(true);
        }
      }
    });
    varPopup.addAction("Compare to all non-local tables", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        TableBean bean = codeTable.getSelectedBean();
        if (bean == null)
          return;
        initTableDialog();

        compareTableDialog.setTable1(bean);
        compareTableDialog.setTable2(null);
        compareTableDialog.setVisible(true);
      }
    });

    entryTable = new BeanTable<>(EntryBean.class, (PreferencesExt) prefs.node("EntryBean"), false);
    varPopup = new PopupMenu(entryTable.getJTable(), "Options");
    varPopup.addAction("Show in all tables", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        EntryBean bean = entryTable.getSelectedBean();
        if (bean == null)
          return;
        showAll(bean);
      }
    });

    // the info window
    infoTA = new TextHistoryPane();
    infoWindow = new IndependentWindow("Extra Information", BAMutil.getImage("nj22/NetcdfUI"), infoTA);
    infoWindow.setBounds((Rectangle) prefs.getBean("InfoWindowBounds", new Rectangle(300, 300, 800, 600)));

    split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, codeTable, entryTable);
    split.setDividerLocation(prefs.getInt("splitPos", 500));

    setLayout(new BorderLayout());
    add(split, BorderLayout.CENTER);

    AbstractButton infoButton = BAMutil.makeButtcon("Information", "Show Table Used", false);
    infoButton.addActionListener(e -> {
      if (showTableDialog == null) {
        showTableDialog = new Grib1TableDialog((Frame) null);
        showTableDialog.pack();
      }
      showTableDialog.setVisible(true);
    });
    buttPanel.add(infoButton);

    try {
      List<Grib1ParamTableReader> tables = Grib1ParamTables.getStandardParameterTables();
      java.util.List<TableBean> beans = new ArrayList<>(tables.size());
      for (Grib1ParamTableReader t : tables) {
        beans.add(new TableBean(t));
      }
      // Collections.sort(beans);
      codeTable.setBeans(beans);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void initTableDialog() {
    if (compareTableDialog == null) {
      compareTableDialog = new Grib1TableCompareDialog((Frame) null);
      compareTableDialog.pack();
      compareTableDialog.addPropertyChangeListener("OK",
          evt -> compareTables((Grib1TableCompareDialog.Data) evt.getNewValue()));
    }
  }

  public void save() {
    codeTable.saveState(false);
    entryTable.saveState(false);
    prefs.putBeanObject("InfoWindowBounds", infoWindow.getBounds());
    prefs.putInt("splitPos", split.getDividerLocation());
  }

  public void setTable(String filename) {
    Grib1ParamTableReader table = new Grib1ParamTableReader(filename);
    TableBean bean = new TableBean(table);
    codeTable.addBean(bean);
    codeTable.setSelectedBean(bean);
  }

  private void setEntries(Grib1ParamTableReader table) {
    Map<Integer, Grib1Parameter> map = table.getParameters();
    if (map == null) {
      return;
    }

    ArrayList<Integer> params = new ArrayList<>(map.keySet());
    Collections.sort(params);
    java.util.List<EntryBean> beans = new ArrayList<>(params.size());
    for (Integer key : params) {
      beans.add(new EntryBean(map.get(key)));
    }
    entryTable.setBeans(beans);
  }

  ///////////////////////////////////////////////////////////////////////////////////////////////

  private void showFile(TableBean bean) {
    infoTA.setText("Table:" + bean.getPath() + "\n");
    try (InputStream is = GribResourceReader.getInputStream(bean.getPath())) {
      infoTA.appendLine(IO.readContents(is));
      infoWindow.setVisible(true);

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void showAll(EntryBean ebean) {
    Formatter f = new Formatter();
    infoTA.setText("Entry:" + ebean.getNumber() + "\n");
    for (Object bean : codeTable.getBeans()) {
      TableBean tbean = (TableBean) bean;
      Grib1Parameter p = tbean.table.getLocalParameter(ebean.getNumber());
      if (p != null)
        f.format(" %s from %s (%d)%n", p, tbean.table.getName(), tbean.table.getParameters().hashCode());
    }

    infoTA.appendLine(f.toString());
    infoWindow.setVisible(true);
  }

  private void compareTables(Grib1TableCompareDialog.Data data) {
    Formatter f = new Formatter();
    if (data.table2bean == null)
      compareAll(data.table1bean.table, data, f);
    else
      compare(data.table1bean.table, data.table2bean.table, data, f);

    infoTA.setText(f.toString());
    infoTA.gotoTop();
    infoWindow.show();
  }

  private void compare(Grib1ParamTableReader t1, Grib1ParamTableReader t2, Grib1TableCompareDialog.Data data,
      Formatter out) {
    out.format("Compare%n %s%n %s%n", t1.toString(), t2.toString());
    Map<Integer, Grib1Parameter> h1 = t1.getParameters();
    Map<Integer, Grib1Parameter> h2 = t2.getParameters();
    List<Integer> keys = new ArrayList<>(h1.keySet());
    Collections.sort(keys);

    for (Integer key : keys) {
      Grib1Parameter d1 = h1.get(key);
      Grib1Parameter d2 = h2.get(key);
      if (d2 == null) {
        if (data.showMissing)
          out.format("**No key %s (%s) in second table%n", key, d1);
      } else {
        if (data.compareDesc) {
          if (!equiv(d1.getDescription(), d2.getDescription()))
            out.format(" %s desc%n   %s%n   %s%n", d1.getNumber(), d1.getDescription(), d2.getDescription());
        }
        if (data.compareNames) {
          if (!equiv(d1.getName(), d2.getName()))
            out.format(" %d name%n   %s%n   %s%n", d1.getNumber(), d1.getName(), d2.getName());
        }
        if (data.compareUnits) {
          if (!equiv(d1.getUnit(), d2.getUnit()))
            out.format(" %s units%n   %s%n   %s%n", d1.getNumber(), d1.getUnit(), d2.getUnit());
        }
        if (data.cleanUnits) {
          String cu1 = Util.cleanUnit(d1.getUnit());
          String cu2 = Util.cleanUnit(d2.getUnit());
          if (!equiv(cu1, cu2))
            out.format(" %s cleanUnits%n   %s%n   %s%n", d1.getNumber(), cu1, cu2);
        }
        if (data.udunits) {
          String cu1 = Util.cleanUnit(d1.getUnit());
          String cu2 = Util.cleanUnit(d2.getUnit());
          try {
            SimpleUnit su1 = SimpleUnit.factoryWithExceptions(cu1);
            if (!su1.isCompatible(cu2))
              out.format(" %s udunits%n   %s%n   %s%n", d1.getNumber(), cu1, cu2);
          } catch (Exception e) {
            out.format(" %s udunits%n   cant parse = %s%n   %s%n", d1.getNumber(), cu1, cu2);
          }
        }
      }
    }

    if (data.showMissing) {
      out.format("%n***Check if entries are missing in first table%n");
      keys = new ArrayList<>(h2.keySet());
      Collections.sort(keys);
      for (Integer key : keys) {
        Grib1Parameter d1 = h1.get(key);
        Grib1Parameter d2 = h2.get(key);
        if (d1 == null)
          out.format("**No key %s (%s) in first table%n", key, d2);
      }
    }
  }

  private boolean equiv(String org1, String org2) {
    if (org1 == org2)
      return true;
    if (org1 == null)
      return false;
    if (org2 == null)
      return false;
    return org1.equalsIgnoreCase(org2);
  }

  private void compareAll(Grib1ParamTableReader t1, Grib1TableCompareDialog.Data data, Formatter out) {
    out.format("Compare All non-local Tables%s%n", t1.toString());
    Map<Integer, Grib1Parameter> h1 = t1.getParameters();
    List<Integer> keys = new ArrayList<>(h1.keySet());
    Collections.sort(keys);

    for (Integer key : keys) {
      Grib1Parameter d1 = h1.get(key);
      out.format("%n--- %s%n", d1);

      for (Object bean : codeTable.getBeans()) {
        TableBean tbean = (TableBean) bean;
        if (tbean.getVersion() > 127)
          continue;

        Grib1Parameter d2 = tbean.table.getLocalParameter(d1.getNumber());
        if (d2 != null) {
          boolean descDiff = data.compareDesc && !equiv(d1.getDescription(), d2.getDescription());
          boolean namesDiff = data.compareNames && !equiv(d1.getName(), d2.getName());
          boolean unitsDiff = data.compareUnits && !equiv(d1.getUnit(), d2.getUnit());
          boolean cunitsDiff = data.cleanUnits && !equiv(Util.cleanUnit(d1.getUnit()), Util.cleanUnit(d2.getUnit()));
          boolean udunitsDiff = false;
          if (data.udunits) {
            String cu1 = Util.cleanUnit(d1.getUnit());
            String cu2 = Util.cleanUnit(d2.getUnit());
            try {
              SimpleUnit su1 = SimpleUnit.factoryWithExceptions(cu1);
              udunitsDiff = !su1.isCompatible(cu2);
            } catch (Exception e) {
              udunitsDiff = false;
            }
          }

          if (descDiff)
            out.format("    desc=%s from %s%n", d2.getDescription(), tbean.table.getPath());
          if (namesDiff)
            out.format("    name=%s from %s%n", d2.getName(), tbean.table.getPath());
          if (unitsDiff || cunitsDiff || udunitsDiff)
            out.format("    units=%s from %s%n", d2.getUnit(), tbean.table.getPath());
        }
      }
    }

  }

  public static class TableBean implements Comparable<TableBean> {
    Grib1ParamTableReader table;

    // no-arg constructor

    public TableBean() {}

    // create from a dataset

    public TableBean(Grib1ParamTableReader table) {
      this.table = table;
    }

    public int getCenter_id() {
      return table.getCenter_id();
    }

    public String getCenter() {
      return CommonCodeTable.getCenterName(table.getCenter_id(), 1);
    }

    public String getSubCenter() {
      return Grib1Customizer.getSubCenterNameStatic(table.getCenter_id(), table.getSubcenter_id());
    }

    public int getSubcenter_id() {
      return table.getSubcenter_id();
    }

    public int getVersion() {
      return table.getVersion();
    }

    public String getPath() {
      return table.getPath();
    }

    public String getName() {
      return table.getName();
    }

    public int getKey() {
      return table.getKey();
    }

    @Override
    public int compareTo(TableBean o) {
      int ret = getCenter_id() - o.getCenter_id();
      if (ret == 0)
        ret = getSubcenter_id() - o.getSubcenter_id();
      if (ret == 0)
        ret = getVersion() - o.getVersion();
      return ret;
    }
  }

  public static class EntryBean {
    Grib1Parameter param;

    // no-arg constructor
    public EntryBean() {}

    public EntryBean(Grib1Parameter param) {
      this.param = param;
    }

    public String getName() {
      return param.getName();
    }

    public String getCFname() {
      return param.getCFname();
    }

    public String getWMOdesc() {
      Grib1ParamTableReader wmo = Grib1ParamTables.getDefaultWmoTable();
      Grib1Parameter p = wmo.getParameter(param.getNumber());
      return p == null ? "" : p.getDescription();
    }

    public String getDescription() {
      return param.getDescription();
    }

    public String getUnit() {
      return param.getUnit();
    }

    public String getCleanUnit() {
      return Util.cleanUnit(param.getUnit());
    }

    public String getUdunit() {
      String cu = Util.cleanUnit(param.getUnit());
      try {
        SimpleUnit su1 = SimpleUnit.factoryWithExceptions(cu);
        return (su1.isUnknownUnit()) ? "UNKNOWN" : su1.toString();
      } catch (Exception e) {
        return "FAIL " + e.getMessage();
      }
    }

    public int getNumber() {
      return param.getNumber();
    }

  }
}

