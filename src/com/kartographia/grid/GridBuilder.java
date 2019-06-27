package com.kartographia.grid;
import com.vividsolutions.jts.geom.*;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import java.util.*;
import javaxt.utils.Console;

//******************************************************************************
//**  GridBuilder
//******************************************************************************
/**
 *   Used to generate global grids using cylindrical projections such as the
 *   Behrmann equal area projection or Web Mercator.
 *
 ******************************************************************************/

public class GridBuilder {

    public final static int SQUARE_SHAPE = 1;
    public final static int DIAMOND_SHAPE = 3;
    public final static int HEX_SHAPE = 2;
    private int projID;
    private CoordinateReferenceSystem proj;
    private CoordinateReferenceSystem wgs84 = getCRS("EPSG:4326");
    private MathTransform WGS84toProj;
    private MathTransform ProjToWGS84;
    private Console console = new Console();


    public interface CallBack {
        public void add(GridCell cell);
        public void done();
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Used to create a new instance of this class
   *  @param proj A name of a projection or an EPSG identifier. Name lookups
   *  are currently limited to "behrmann" and "google"
   */
    public GridBuilder(String proj){

        if (proj.equalsIgnoreCase("behrmann")){
            projID = 54017;
        }
        else if (proj.equalsIgnoreCase("google")){
            projID = 3857;
        }
        else{
            if (proj.toUpperCase().startsWith("EPSG:")){
                projID = Integer.valueOf(proj.substring(5));
            }
            else{
                projID = Integer.valueOf(proj);
            }
        }

        init();
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Used to create a new instance of this class
   *  @param srid Spatial Reference System Identifier (SRID) used to represent
   *  a projection. Only EPSG codes are supported as this time.
   */
    public GridBuilder(int srid){
        projID = srid;
        init();
    }


  //**************************************************************************
  //** init
  //**************************************************************************
    private void init(){
        proj = getCRS("EPSG:" + projID);
        WGS84toProj = getTransform(wgs84, proj);
        ProjToWGS84 = getTransform(proj, wgs84);
    }


  //**************************************************************************
  //** getID
  //**************************************************************************
  /** Returns the Spatial Reference System Identifier (SRID) used to represent
   *  the projection
   */
    public int getSRID(){
        return projID;
    }


  //**************************************************************************
  //** createGrid
  //**************************************************************************
  /** Used to generate grid cells
   *
   *  @param shape Shape of individual grid cells (e.g SQUARE_SHAPE, HEX_SHAPE,
   *  DIAMOND_SHAPE)
   *
   *  @param level Used to specify the size of individual grid cells. Currently
   *  supports values from 1-9 where 1 is the lowest resolution resulting in
   *  large grid cells.
   *
   *  @param density Used to calculate the vertex spacing. A value of 1 implies
   *  no densification. For applications that require high precision or for
   *  rendering in GIS applications, use higher numbers.
   *
   *  @param spatialFilter Used to define a spatial filter. If null, no spatial
   *  filter will be applied and a grid will be generated for the entire planet.
   *  Otherwise, will only generate cells that intersect the given geometry.
   *  Assumes that the geometry is in WGS84.
   *
   *  @param numThreads Used to specify the number of threads used to generate
   *  the grid.
   *
   *  @param callback Called whenever a new cell is created and when we have
   *  finished creating cells. Not that if the numThreads is greater than 1,
   *  then the add() will be called asynchronously via multiple threads so make
   *  sure your implementation is synchronized! Example:
   <pre>
        new GridBuilder.CallBack() {

            public synchronized void add(GridCell cell){ //synchronized method!
                try{
                    cell.save();
                }
                catch(Exception e){
                    //probably a duplicate
                }
            }

            public void done(){
            }
        }
   </pre>
   */
    public void createGrid(int shape, int level, double density,
        Geometry spatialFilter, int numThreads, CallBack callback) throws Exception {


     //Bounding box in WGS84. Coordinates must be specified in the
     //following order: left,bottom,right,top.
        Double[] bbox = null;
        if (spatialFilter!=null){
            Envelope envelope = spatialFilter.getEnvelopeInternal();
            double west = envelope.getMinX();
            double south = envelope.getMinY();
            double east = envelope.getMaxX();
            double north = envelope.getMaxY();
            bbox = new Double[]{west, south, east, north};
        }



      //Compute cell multiplier. Subdivide each cell by a factor of 2
        int multiplier;
        switch(level){
            case 1: multiplier = 1; break;
            case 2: multiplier = 2*2; break;
            case 3: multiplier = 4*4; break;
            case 4: multiplier = 8*8; break;
            case 5: multiplier = 16*16; break;
            case 6: multiplier = 32*32; break;
            case 7: multiplier = 64*64; break;
            case 8: multiplier = 128*128; break;
            case 9: multiplier = 256*256; break;
            default: multiplier = 1; level = 1; break;
        }



      //Compute left and right bounds and compute grid size
        GeometryFactory geometryFactory = new GeometryFactory();
        Point left = (Point) JTS.transform( geometryFactory.createPoint(new Coordinate(0,-180,0)), WGS84toProj);
        Point right = (Point) JTS.transform( geometryFactory.createPoint(new Coordinate(0,180,0)), WGS84toProj);
        double gridSize = (right.getX()*2)/(double)(48*multiplier);
        gridSize = (right.getX()*2)/(double)(30*multiplier);



      //Compute offset as needed
        double leftOffset = 0.0;
        if ((shape==DIAMOND_SHAPE) && level>1) leftOffset = gridSize/2d;



      //Compute top and bottom bounds
        Point top = null;
        Point bottom = null;
        try{
            top = (Point) JTS.transform( geometryFactory.createPoint(new Coordinate(90,0,0)), WGS84toProj);
            bottom = (Point) JTS.transform( geometryFactory.createPoint(new Coordinate(-90,0,0)), WGS84toProj);
        }
        catch(Exception e){

          //The projected coordinate system probably doesn't extend all the way
          //to the poles so we'll have to get the max extent of the projection
            org.geotools.metadata.iso.extent.GeographicBoundingBoxImpl box =
                (org.geotools.metadata.iso.extent.GeographicBoundingBoxImpl)
                proj.getDomainOfValidity().getGeographicElements().iterator().next();


          //Get the north bounding latitude
            double northBoundLatitude = box.getNorthBoundLatitude();


          //Calculate max y by incrementally adding the gridSize until we reach the northBoundLatitude
            double y = gridSize;
            while (true){
                try{
                    Point p = (Point) JTS.transform( geometryFactory.createPoint(new Coordinate(0,y,0)), ProjToWGS84);
                    double lat = p.getX(); //Yeah, getX is a little wierd...
                    if (lat>northBoundLatitude){
                        top = (Point) JTS.transform( geometryFactory.createPoint(new Coordinate(lat,0,0)), WGS84toProj);
                        bottom = (Point) JTS.transform( geometryFactory.createPoint(new Coordinate(-lat,0,0)), WGS84toProj);
                        break;
                    }
                    y += gridSize;

                }
                catch(Exception ex){
                    break;
                }
            }
        }

        if (top==null || bottom==null) throw new IllegalArgumentException();



      //Spawn threads
        List pool = new LinkedList();
        int maxPoolSize = 50000;
        ArrayList<Thread> threads = new ArrayList<>();
        if (numThreads<1) numThreads = 1;
        for (int i=0; i<numThreads; i++){
            Thread thread = new Thread(new CellGenerator(shape, level, density, spatialFilter, projID, pool, callback));
            threads.add(thread);
            thread.start();
        }




      //Transform bbox as needed
        if (bbox!=null) bbox = transFormBBox(bbox);





      //Generate grid cells
        for (double x=left.getX()-leftOffset; x<right.getX(); x+=gridSize){

          //Skip cells as needed
            if (bbox!=null){
                if (x+gridSize<bbox[0] || x>bbox[2]) continue;
            }



          //Northern latitudes (0 to 90N)
            for (double y=0; y<=top.getY(); y+=gridSize){


              //Skip cells as needed
                if (bbox!=null){
                    if (y+gridSize<bbox[1] || y>bbox[3]) continue;
                }


              //Create bbox for the grid cell
                Coordinate[] coords = new Coordinate[]{
                    new Coordinate(x,y+gridSize,0), //ul
                    new Coordinate(x,y,0), //ll
                    new Coordinate(x+gridSize,y,0), //lr
                    new Coordinate(x+gridSize,y+gridSize,0), //ur
                    new Coordinate(x,y+gridSize,0)
                };



              //Update y coordinate as needed
                if (shape==HEX_SHAPE){
                    y += (gridSize/2.0);
                }





              //Create cells
                synchronized(pool){
                    while (pool.size()>maxPoolSize){
                        try{
                            pool.wait();
                        }
                        catch(java.lang.InterruptedException e){
                            break;
                        }
                    }

                    pool.add(coords);
                    pool.notify();
                }

            }


          //Southern latitudes (0 to 90S)
            for (double y=-gridSize; y>bottom.getY()-gridSize; y-=gridSize){


              //Skip cells as needed
                if (bbox!=null){
                    if (y<bbox[1] || y>bbox[3]) continue;
                }



              //Create bbox for the grid cell
                Coordinate[] coords = new Coordinate[]{
                    new Coordinate(x,y+gridSize,0), //ul
                    new Coordinate(x,y,0), //ll
                    new Coordinate(x+gridSize,y,0), //lr
                    new Coordinate(x+gridSize,y+gridSize,0), //ur
                    new Coordinate(x,y+gridSize,0)
                };



              //Update y coordinate as needed
                if (shape==HEX_SHAPE){


                  //skip first row
                    if (y==-gridSize){
                        y = -(gridSize/2.0);
                        continue;
                    }
                    else{
                        y -= (gridSize/2.0);
                    }

                }



              //Create cells
                synchronized(pool){
                    while (pool.size()>maxPoolSize){
                        try{
                            pool.wait();
                        }
                        catch(java.lang.InterruptedException e){
                            break;
                        }
                    }


                    pool.add(coords);
                    pool.notify();
                }


            }
        }




      //Notify the CellGenerator that we are done adding objects to the pool
        synchronized (pool) {
            pool.add(null);
            pool.notify(); //pool.notifyAll();
        }



      //Wait for threads to complete
        while (true) {
            try {
                for (Thread thread : threads){
                    thread.join();
                }
                break;
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        threads.clear();

        callback.done();
    }



  //**************************************************************************
  //** CellGenerator
  //**************************************************************************
  /** Thread used to generate cells
   */
    private class CellGenerator implements Runnable {

        private int proj;
        private int shape;
        private int level;
        private double density;
        private List pool;
        private CallBack callback;
        private Geometry spatialFilter;
        private GeometryFactory geometryFactory = new GeometryFactory();
        private LineString leftBorder = geometryFactory.createLineString(new Coordinate[]{
            new Coordinate(-180.005,90,0),
            new Coordinate(-180.005,-90,0)
        });

        public CellGenerator(int shape, int level, double density, Geometry spatialFilter, int proj, List pool, CallBack callback){
            this.proj = proj;
            this.shape = shape;
            this.level = level;
            this.density = density;
            this.spatialFilter = spatialFilter;
            this.callback = callback;
            this.pool = pool;
        }

        public void run() {

            while (true) {

                Object obj = null;
                synchronized (pool) {
                    while (pool.isEmpty()) {
                        try {
                          pool.wait();
                        }
                        catch (InterruptedException e) {
                          return;
                        }
                    }
                    obj = pool.get(0);
                    if (obj!=null) pool.remove(0);
                    pool.notifyAll();
                }

                if (obj!=null){
                    Coordinate[] coords = (Coordinate[]) obj;



                    ArrayList<Polygon> cells = new ArrayList<>();

                    Polygon cell = getCell(shape, coords, density, geometryFactory, ProjToWGS84);
                    cells.add(cell);


                  //Create new cell to the right and slightly lower than the current cell
                    if (shape==DIAMOND_SHAPE || shape==HEX_SHAPE){
                        Coordinate[] coords2 = shiftCoords(coords, shape, geometryFactory);
                        Polygon cell2 = getCell(shape, coords2, density, geometryFactory, ProjToWGS84);
                        cells.add(cell2);
                    }



                    for (Polygon polygon : cells){
                        if (polygon==null) continue;
                        if (spatialFilter!=null){
                            if (!polygon.intersects(spatialFilter)) continue;
                        }

                        try{

                          //Check left border
                            if (polygon.crosses(leftBorder)){
                                console.log("Skipping border cell...");
                                continue;
                            }


                            GridCell gridCell = new GridCell();
                            gridCell.setShape(shape);
                            gridCell.setLevel(level);
                            gridCell.setProj(proj);
                            gridCell.setGeom(polygon);
                            Coordinate centroid = polygon.getCentroid().getCoordinate();
                            int hashCode = Objects.hash(shape, level, proj, centroid.x, centroid.y);
                            gridCell.setHash(hashCode);

                            callback.add(gridCell);
                        }
                        catch(Exception e){
                            console.log(leftBorder);
                            console.log(polygon);
                            e.printStackTrace();
                            //console.log(e.getMessage());
                        }
                    }
                }
                else{
                    return;
                }
            }
        }
    }



  //**************************************************************************
  //** getCell
  //**************************************************************************
  /** Used to generate grid cells (hexes, squares, diamonds, etc).
   *
   *  @param coords Bounding box of the grid cell. Assumes coordinates are in
   *  counter clockwise order starting with the upper left coordinate.
   *
   *  @param density Used to calculate the vertex spacing. A value of 1 implies
   *  no densification. For applications that require high precision or for
   *  rendering in GIS applications, use higher numbers.
   */
    private static Polygon getCell(int shape, Coordinate[] coords, double density,
        GeometryFactory geometryFactory, MathTransform projToWGS84){

        Polygon polygon = (Polygon) geometryFactory.createPolygon(coords);

        if (shape!=SQUARE_SHAPE){

            Coordinate ul = coords[0];
            Coordinate ur = coords[3];

            Coordinate ll = coords[1];
            Coordinate lr = coords[2];

            double cellHeight = ul.getOrdinate(Coordinate.Y)-ll.getOrdinate(Coordinate.Y);
            double cellWidth = ul.getOrdinate(Coordinate.X)-ur.getOrdinate(Coordinate.X);

            double top = ul.getOrdinate(Coordinate.Y);
            double bottom = ll.getOrdinate(Coordinate.Y);
            double left = ul.getOrdinate(Coordinate.X);
            double right = ur.getOrdinate(Coordinate.X);

            Coordinate center = polygon.getCentroid().getCoordinate();

            if (shape==DIAMOND_SHAPE){

                coords = new Coordinate[]{
                    new Coordinate(center.getOrdinate(Coordinate.X), top),
                    new Coordinate(left, center.getOrdinate(Coordinate.Y)),
                    new Coordinate(center.getOrdinate(Coordinate.X), bottom),
                    new Coordinate(right, center.getOrdinate(Coordinate.Y)),
                    new Coordinate(center.getOrdinate(Coordinate.X), top)
                };

                polygon = (Polygon) geometryFactory.createPolygon(coords);

            }


            else if (shape==HEX_SHAPE) {


                double dy = cellHeight/4.0;
                double y1 = top-dy;
                double y2 = bottom+dy;

                coords = new Coordinate[]{
                    new Coordinate(center.getOrdinate(Coordinate.X), top),
                    new Coordinate(left, y1),
                    new Coordinate(left, y2),
                    new Coordinate(center.getOrdinate(Coordinate.X), bottom),
                    new Coordinate(right, y2),
                    new Coordinate(right, y1),
                    new Coordinate(center.getOrdinate(Coordinate.X), top)
                };

                polygon = (Polygon) geometryFactory.createPolygon(coords);
            }

        }


      //Densify polygon as needed
        Geometry densePolygon = polygon;
        if (density>1){
            double vertexSpacing = polygon.getLength() / density;
            densePolygon = com.vividsolutions.jts.densify.Densifier.densify(polygon, vertexSpacing);
        }


        try{

          //Convert projected coordinates to WGS84
            Geometry g = JTS.transform(densePolygon, projToWGS84);

          //Fix axis order (lat, lon)
            for (Coordinate coord : g.getCoordinates()){
                double x1 = coord.getOrdinate(coord.X);
                double y1 = coord.getOrdinate(coord.Y);
                coord.setOrdinate(coord.X, y1);
                coord.setOrdinate(coord.Y, x1);
            }

            return (Polygon) g;
        }
        catch(Exception e){
            return null;
        }

    }


  //**************************************************************************
  //** shiftCoords
  //**************************************************************************
  /** Used to compute coordinates right and down from a given bounding box.
   *  @param coords Projected coordinates (not WGS84)
   */
    private static Coordinate[] shiftCoords(Coordinate[] coords, int shape, GeometryFactory geometryFactory){
        Polygon polygon = (Polygon) geometryFactory.createPolygon(coords);

        Coordinate ul = coords[0];
        Coordinate ur = coords[3];

        Coordinate ll = coords[1];
        //Coordinate lr = coords[2];
//
//
//        double top = ul.getOrdinate(Coordinate.Y);
//        double bottom = ll.getOrdinate(Coordinate.Y);
//        double left = ul.getOrdinate(Coordinate.X);
//        double right = ur.getOrdinate(Coordinate.X);

        Coordinate center = polygon.getCentroid().getCoordinate();


        double cellHeight = ul.getOrdinate(Coordinate.Y)-ll.getOrdinate(Coordinate.Y);
        double cellWidth = ul.getOrdinate(Coordinate.X)-ur.getOrdinate(Coordinate.X);



        if (shape==HEX_SHAPE){


            double a = cellHeight/4.0;
            return new Coordinate[]{
                new Coordinate(center.getOrdinate(Coordinate.X), center.getOrdinate(Coordinate.Y)-a), //ul
                new Coordinate(center.getOrdinate(Coordinate.X), center.getOrdinate(Coordinate.Y)-cellHeight-a), //ll
                new Coordinate(center.getOrdinate(Coordinate.X)-cellWidth, center.getOrdinate(Coordinate.Y)-cellHeight-a), //lr
                new Coordinate(center.getOrdinate(Coordinate.X)-cellWidth, center.getOrdinate(Coordinate.Y)-a), //ur
                new Coordinate(center.getOrdinate(Coordinate.X), center.getOrdinate(Coordinate.Y)-a)
            };
        }
        else{

          //Create new square (right and down from previous original square)
            return new Coordinate[]{
                new Coordinate(center.getOrdinate(Coordinate.X), center.getOrdinate(Coordinate.Y)), //ul
                new Coordinate(center.getOrdinate(Coordinate.X), center.getOrdinate(Coordinate.Y)-cellHeight), //ll
                new Coordinate(center.getOrdinate(Coordinate.X)-cellWidth, center.getOrdinate(Coordinate.Y)-cellHeight), //lr
                new Coordinate(center.getOrdinate(Coordinate.X)-cellWidth, center.getOrdinate(Coordinate.Y)), //ur
                new Coordinate(center.getOrdinate(Coordinate.X), center.getOrdinate(Coordinate.Y))
            };
        }
    }


  //**************************************************************************
  //** getArea
  //**************************************************************************
  /** Try to compute area
   */
    private static double getArea(Polygon geoPoly, MathTransform WGS84toBehrmann) throws Exception {

        Coordinate[] geoCoordinates = geoPoly.getCoordinates();
        Coordinate[] behrmannCoordinates = new Coordinate[geoCoordinates.length];

        int i = 0;
        for (Coordinate coord : geoPoly.getCoordinates()){
            double lat = coord.getOrdinate(coord.X);
            double lon = coord.getOrdinate(coord.Y);
            behrmannCoordinates[i] = new Coordinate(lon, lat);
            i++;
        }



        Polygon polygon = (Polygon) geoPoly.getFactory().createPolygon(behrmannCoordinates);
        return JTS.transform(polygon, WGS84toBehrmann).getArea();
    }


  //**************************************************************************
  //** getCRS
  //**************************************************************************
    private static CoordinateReferenceSystem getCRS(String name){
        try{
            return CRS.decode(name);
        }
        catch(Exception e){
            return null;
        }
    }


  //**************************************************************************
  //** getTransform
  //**************************************************************************
    private static MathTransform getTransform(CoordinateReferenceSystem input, CoordinateReferenceSystem output){
        try{
            return CRS.findMathTransform(input, output, true);
        }
        catch(Exception e){
            return null;
        }
    }


  //**************************************************************************
  //** transFormBBox
  //**************************************************************************
    private Double[] transFormBBox(Double[] bbox) throws Exception {
        GeometryFactory geometryFactory = new GeometryFactory();
        Point ll = (Point) JTS.transform(geometryFactory.createPoint(new Coordinate(bbox[1],bbox[0],0)), WGS84toProj);
        Point ur = (Point) JTS.transform(geometryFactory.createPoint(new Coordinate(bbox[3],bbox[2],0)), WGS84toProj);
        return new Double[]{ll.getX(),ll.getY(),ur.getX(),ur.getY()};
    }
}