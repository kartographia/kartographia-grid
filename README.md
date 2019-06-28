# Kartographia Grid Builder
Java library used generate global grids using cylindrical projections such as the
Behrmann equal area projection or Web Mercator.


# Usage
The GridBuilder.createGrid() method is used to generate grid cells. For performance
reasons, the cells can be generated in separate threads. The caller is notified
when an individual thread creates a cell via the GridBuilder.CallBack. Note
that the add() will be called asynchronously when using multiple threads so make
sure your implementation of the add() method is synchronized! Example:
``` java
    GridBuilder grid = new GridBuilder(proj);
    grid.createGrid(shape, level, density, geom, numThreads,
        new GridBuilder.CallBack() {

            public synchronized void add(GridCell cell){
                System.out.println(cell.getGeom());
            }

            public void done(){
                System.out.println("Done!");
            }
        }
    );
```

# Dependencies
This library relies on GeoTools, JTS, and JavaXT. All the requisite JAR files
are found in the lib directory.



# Command Line Interface
Although this project is intended as a library, there is a command line interface
available to generate grids and test projections.

- -config Path to a config file (json) containing database connection info
- -shape Shape of individual grid cells (square, hex, diamond)
- -level Grid level (1-9)
- -proj Grid projection. Accepts EPSG codes and keywords (google, behrmann)
- -aoi Spatial filter. Can be a shapefile or a database query
- -t Number of threads



# Config.json
If running as a stand-alone app, you'll need a config.json file to start.
At a minimum, the config.json file should include connection information to a database.
Here's an example:
```javascript
{
    "database" : {
        "driver" : "PostgreSQL",
        "host" : "localhost:5432",
        "name" : "kartographia",
        "username" : "postgres",
        "password" : "***********",
        "maxConnections" : 50
    }

}
```

# Java Compatibility
Note that the current implementation of this library requires Java 1.8.


# License
This is an open source project released under an MIT License. See the LICENSE.txt file for specifics.
Feel free to use the code and information found here as you like. This software comes with no guarantees or warranties.
You may use this software in any open source or commercial project.