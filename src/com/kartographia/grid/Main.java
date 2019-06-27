package com.kartographia.grid;

import org.geotools.referencing.CRS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import com.vividsolutions.jts.io.WKTReader;
import com.vividsolutions.jts.geom.*;

import java.util.*;

import javaxt.io.Jar;
import javaxt.utils.Console;
import javaxt.json.*;
import javaxt.sql.*;


//******************************************************************************
//**  Main Application
//******************************************************************************
/**
 *   Console app used to generate grids and test projections
 *
 ******************************************************************************/

public class Main {

    private static Console console = new Console();


  //**************************************************************************
  //** Main
  //**************************************************************************
  /** Entry point for the application. Used to process command line arguments.
   *  @param arr the command line arguments
   */
    public static void main(String[] arr) throws Exception {
        HashMap<String, String> args = Console.parseArgs(arr);

        if (args.containsKey("-test")){
            test(args);
        }
        else{
            createGrid(args);
        }
    }


  //**************************************************************************
  //** createGrid
  //**************************************************************************
  /** Used to create populate a table in a database with GridCells. The table
   *  schema is found in "schema.sql" and the table should be created before
   *  invoking this method.
   *  @param args Command line arguments:
   *  -config Path to a config file (json) containing database connection info
   *  -shape Shape of individual grid cells (square, hex, diamond)
   *  -level Grid level (1-9)
   *  -proj Grid projection. Accepts EPSG codes and keywords (google, behrmann)
   *  -aoi Spatial filter. Can be a shapefile or a database query
   *  -t Number of threads
   */
    private static void createGrid(HashMap<String, String> args) throws Exception {


      //Get jar file
        Jar jar = new Jar(Main.class);
        javaxt.io.File jarFile = new javaxt.io.File(jar.getFile());



      //Get config file
        javaxt.io.File configFile = (args.containsKey("-config")) ?
            getFile(args.get("-config"), jarFile) :
            new javaxt.io.File(jar.getFile().getParentFile(), "config.json");

        if (!configFile.exists()) {
            if (!args.containsKey("-config")){
                configFile = new javaxt.io.File(jar.getFile().getParentFile().getParentFile(), "config.json");
            }
            if (!configFile.exists()) {
                System.out.println("Could not find config file. Use the \"-config\" parameter to specify a path to a config");
                return;
            }
        }


      //Parse config file
        JSONObject config = new JSONObject(configFile.getText());


      //Get database info
        JSONObject json = config.get("database").toJSONObject();
        javaxt.sql.Database database = new javaxt.sql.Database();
        database.setDriver(json.get("driver").toString());
        database.setHost(json.get("host").toString());
        database.setName(json.get("name").toString());
        database.setUserName(json.get("username").toString());
        database.setPassword(json.get("password").toString());
        if (json.has("maxConnections")){
            database.setConnectionPoolSize(json.get("maxConnections").toInteger());
        }



      //Initialize connection pool
        database.initConnectionPool();


      //Initialize GridCell class
        Model.init(GridCell.class, database.getConnectionPool());



      //Process command line args
        int shape = GridBuilder.SQUARE_SHAPE;
        if (args.containsKey("-shape")){
            String s = args.get("-shape").toLowerCase();
            if (s.startsWith("hex")){
                shape = GridBuilder.HEX_SHAPE;
            }
            else if(s.equals("diamond")){
                shape = GridBuilder.DIAMOND_SHAPE;
            }
        }
        int level = args.containsKey("-level") ? Integer.parseInt(args.get("-level")) : 1;
        String proj = args.containsKey("-proj") ? args.get("-proj") : "google"; //behrmann
        String aoi = args.get("-aoi");
        Geometry geom = null;
        if (aoi!=null){
            if (aoi.endsWith(".shp")){
                createTempTable(aoi);
            }
            else{
                javaxt.sql.Parser sql = new javaxt.sql.Parser(aoi);
                String from = sql.getFromString();


                if (from.endsWith(".shp")){
                    createTempTable(from);
                }
                else{
                    geom = getGeometry(sql, database);
                }

            }
        }
        int numThreads = args.containsKey("-t") ? Integer.parseInt(args.get("-t")) : 4;




      //Instantiate GridBuilder and generate grid
        GridBuilder grid = new GridBuilder(proj);
        if (args.containsKey("-clear")) clear(shape, level, grid.getSRID(), geom, database);
        grid.createGrid(shape, level, 1.0, geom, numThreads,
            new GridBuilder.CallBack() {

                public synchronized void add(GridCell cell){
                    try{
                        cell.save();
                    }
                    catch(Exception e){
                        //probably a duplicate
                    }
                }

                public void done(){}
            }
        );
    }


  //**************************************************************************
  //** clear
  //**************************************************************************
    private static void clear(int shape, int level, int srid, Geometry geom, Database database) throws Exception {
        String tableName = "grid_cell";
        Connection conn = database.getConnection();
        conn.execute("DELETE FROM " + tableName + " where shape=" + shape + " AND level=" + level + " AND proj=" + srid);
        conn.execute("SELECT setval('" + tableName + "_id_seq', (SELECT MAX(id) FROM " + tableName + "));");
        conn.close();

        /*
      //Add extents
        if (geom!=null){
            Geometry bbox = new GeometryFactory().toGeometry(geom.getEnvelopeInternal());
            GridCell cell = new GridCell();
            cell.setGeom(bbox);
            cell.setLevel(level);
            cell.setShape(shape);
            cell.setProj(srid);
            cell.setHash(-level);
            //cell.save();
        }
        */
    }


  //**************************************************************************
  //** createTempTable
  //**************************************************************************
    private static String createTempTable(String shpFile) throws Exception {
        throw new Exception("Not Implemented");
    }


  //**************************************************************************
  //** getGeometry
  //**************************************************************************
    private static Geometry getGeometry(javaxt.sql.Parser sql, Database database) throws Exception {
        String select = sql.getSelectString();
        sql = sql.clone();
        sql.setSelect("st_astext(" + select + ")");
        Connection conn = null;
        try{
            String wkt = null;
            conn = database.getConnection();
            for (Recordset rs : conn.getRecordset(sql.toString())){
                wkt = rs.getValue(0).toString();
            }
            conn.close();
            return new WKTReader().read(wkt);
        }
        catch(Exception e){
            if (conn!=null) conn.close();
            throw e;
        }
    }


  //**************************************************************************
  //** test
  //**************************************************************************
  /** Used to test projections
   */
    private static void test(HashMap<String, String> args) throws Exception {

        String test = args.get("-test");
        if (test==null) test="";

        if (test.equalsIgnoreCase("behrmann")){
            CoordinateReferenceSystem behrmann = CRS.decode("EPSG:54017");
            System.out.println(behrmann);
        }
        else if (test.equalsIgnoreCase("google")){
            CoordinateReferenceSystem google = CRS.decode("EPSG:3857");
            System.out.println(google);
        }
        else if (test.equalsIgnoreCase("mercator")){
            CoordinateReferenceSystem google = CRS.decode("EPSG:3395");
            System.out.println(google);
        }
        else if (test.equalsIgnoreCase("all")){

          //List of interesting projections
            int[] EPSG = new int[]{

              //Northern/Polar Projections
                3408, //NSIDC EASE-Grid North (Northern Hemisphere)
                3411, //NSIDC Sea Ice Polar Stereographic North


              //Southern/Polar Projections
                3031, //Antarctic Polar Stereographic
                3409, //NSIDC EASE-Grid South (Southern Hemisphere)
                3412, //NSIDC Sea Ice Polar Stereographic South
                32761, //UPS South


              //Global Projections
                3410, //NSIDC EASE-Grid Global (World)
                4326, //Geographic
                3395, //Mercator
                3857, //Web Mercator (same as google)
                54017, //Behrmann Cylindrical Equal Area
                900913 //Google
            };

            for (int srid : EPSG){
                try{
                    CoordinateReferenceSystem crs = CRS.decode("EPSG:" + srid);
                    System.out.println(crs);
                    System.out.println("------------------------------------");
                }
                catch(Exception e){

                }
            }

        }
        else{ //srid?
            CoordinateReferenceSystem crs = CRS.decode("EPSG:" + test);
            System.out.println(crs);
        }
    }


  //**************************************************************************
  //** getFile
  //**************************************************************************
  /** Returns a File for a given path
   *  @param path Full canonical path to a file or a relative path (relative
   *  to the jarFile)
   */
    private static javaxt.io.File getFile(String path, javaxt.io.File jarFile){
        javaxt.io.File file = new javaxt.io.File(path);
        if (!file.exists()){
            file = new javaxt.io.File(jarFile.MapPath(path));
        }
        return file;
    }
}