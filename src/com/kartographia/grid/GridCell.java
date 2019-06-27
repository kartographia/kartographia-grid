package com.kartographia.grid;
import javaxt.json.*;
import java.sql.SQLException;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKTReader;

//******************************************************************************
//**  GridCell Class
//******************************************************************************
/**
 *   Used to represent a GridCell
 *
 ******************************************************************************/

public class GridCell extends javaxt.sql.Model {

    private Integer shape;
    private Integer level;
    private Geometry geom;
    private Integer proj;
    private Integer hash;
    private JSONObject info;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public GridCell(){
        super("grid_cell", new java.util.HashMap<String, String>() {{
            
            put("shape", "shape");
            put("level", "level");
            put("geom", "geom");
            put("proj", "proj");
            put("hash", "hash");
            put("info", "info");

        }});
        
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class using a record ID in the database.
   */
    public GridCell(long id) throws SQLException {
        this();
        init(id);
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
  /** Creates a new instance of this class using a JSON representation of a
   *  GridCell.
   */
    public GridCell(JSONObject json){
        this();
        update(json);
    }


  //**************************************************************************
  //** update
  //**************************************************************************
  /** Used to update attributes using a record in the database.
   */
    protected void update(Object rs) throws SQLException {

        try{
            this.id = getValue(rs, "id").toLong();
            this.shape = getValue(rs, "shape").toInteger();
            this.level = getValue(rs, "level").toInteger();
            this.geom = new WKTReader().read(getValue(rs, "geom").toString());
            this.proj = getValue(rs, "proj").toInteger();
            this.hash = getValue(rs, "hash").toInteger();
            this.info = new JSONObject(getValue(rs, "info").toString());


        }
        catch(Exception e){
            if (e instanceof SQLException) throw (SQLException) e;
            else throw new SQLException(e.getMessage());
        }
    }


  //**************************************************************************
  //** update
  //**************************************************************************
  /** Used to update attributes with attributes from another GridCell.
   */
    public void update(JSONObject json){

        Long id = json.get("id").toLong();
        if (id!=null && id>0) this.id = id;
        this.shape = json.get("shape").toInteger();
        this.level = json.get("level").toInteger();
        try {
            this.geom = new WKTReader().read(json.get("geom").toString());
        }
        catch(Exception e) {}
        this.proj = json.get("proj").toInteger();
        this.hash = json.get("hash").toInteger();
        this.info = json.get("info").toJSONObject();
    }


    public Integer getShape(){
        return shape;
    }

    public void setShape(Integer shape){
        this.shape = shape;
    }

    public Integer getLevel(){
        return level;
    }

    public void setLevel(Integer level){
        this.level = level;
    }

    public Geometry getGeom(){
        return geom;
    }

    public void setGeom(Geometry geom){
        this.geom = geom;
    }

    public Integer getProj(){
        return proj;
    }

    public void setProj(Integer proj){
        this.proj = proj;
    }

    public Integer getHash(){
        return hash;
    }

    public void setHash(Integer hash){
        this.hash = hash;
    }

    public JSONObject getInfo(){
        return info;
    }

    public void setInfo(JSONObject info){
        this.info = info;
    }
    
    


  //**************************************************************************
  //** get
  //**************************************************************************
  /** Used to find a GridCell using a given set of constraints. Example:
   *  GridCell obj = GridCell.get("shape=", shape);
   */
    public static GridCell get(Object...args) throws SQLException {
        Object obj = _get(GridCell.class, args);
        return obj==null ? null : (GridCell) obj;
    }


  //**************************************************************************
  //** find
  //**************************************************************************
  /** Used to find GridCells using a given set of constraints.
   */
    public static GridCell[] find(Object...args) throws SQLException {
        Object[] obj = _find(GridCell.class, args);
        GridCell[] arr = new GridCell[obj.length];
        for (int i=0; i<arr.length; i++){
            arr[i] = (GridCell) obj[i];
        }
        return arr;
    }
}