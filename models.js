var package = "com.kartographia.grid";
var models = {


  //**************************************************************************
  //** GridCell
  //**************************************************************************
    GridCell: {
        fields: [

            {name: 'shape',        type: 'int'},
            {name: 'level',        type: 'int'},
            {name: 'geom',         type: 'geo'},
            {name: 'proj',         type: 'int'},
            {name: 'hash',         type: 'int'},
            {name: 'info',         type: 'json'}
        ],
        constraints: [
            {name: 'shape',      required: true},
            {name: 'level',      required: true},
            {name: 'proj',       required: true},
            {name: 'geom',       required: true},
            {name: 'hash',       required: true, unique: true}
        ]
    }

};