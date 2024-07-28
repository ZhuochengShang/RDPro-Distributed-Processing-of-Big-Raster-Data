// React Components will go here

// Add imports for useEffect and useState
const { useEffect, useState } = React;

// DatasetList Component
function DatasetList({ datasets, selectedDataset, clickAction, doubleClickAction,
      actions, // Array of action objects { icon: dataset => string, onClick: dataset => ... }
      enableReorder, onReorderDatasets}) {
  const [dragOverIdx, setDragOverIdx] = useState(null);

  const handleDragOver = (e, idx) => {
    e.preventDefault();
    setDragOverIdx(idx);
  };

  const handleDrop = (e, idx) => {
    e.preventDefault();
    const draggedIdx = e.dataTransfer.getData("text/plain");
    onReorderDatasets(draggedIdx, idx);
    setDragOverIdx(null); // Reset the drag over index
  };

  const handleDragStart = (e, idx) => {
    e.dataTransfer.setData("text/plain", idx);
    setDragOverIdx(null); // Reset the drag over index
  };

  // Helper function to determine the dataset's class name
  const getDatasetClassName = (dataset) => {
    if (dataset.error_message) return "dataset-error";
    if (dataset.in_progress) return "dataset-in-progress";
    return "";
  }

  return (
    <div className="dataset-list">
      {datasets.map((dataset, idx) => (
        <div key={dataset.name} className={getDatasetClassName(dataset)} style={{ backgroundSize: `${dataset.progress}% 100%` }}>
          {dragOverIdx === idx && <div className="drag-over-indicator"></div>}
          <div className={`dataset-item ${selectedDataset && dataset.name === selectedDataset.name ? 'selected' : ''}`}
            onClick={clickAction ? () => clickAction(dataset) : undefined}
            onDoubleClick={doubleClickAction ? () => doubleClickAction(dataset) : undefined}
            draggable={enableReorder}
            onDragOver={enableReorder ? (e) => handleDragOver(e, idx) : null}
            onDragStart={enableReorder ? (e) => handleDragStart(e, idx) : null}
            onDrop={enableReorder ? (e) => handleDrop(e, idx) : null}>
            {enableReorder && <span>&#9776;</span>}
            {dataset.in_progress && !dataset.error_message && <span className="loading-icon"></span>}
            <span className="dataset-name">
              {dataset.name}
            </span>
            <div className="dataset-actions">
              {actions.map(action => (
                <span key={action.icon} className={`icon-button ${action.icon(dataset)}`} onClick={() => action.onClick(dataset)}>
                </span>
              ))}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

function GenericModal({ children, closeModal }) {
  useEffect(() => {
    const handleEsc = (event) => {
      if (event.keyCode === 27) {
        closeModal();
      }
    };

    window.addEventListener('keydown', handleEsc);
    return () => {
      window.removeEventListener('keydown', handleEsc);
    };
  }, [closeModal]);

  const handleOverlayClick = (event) => {
    if (event.target === event.currentTarget) {
      closeModal();
    }
  };

  return (
    <div className="modal-overlay" onClick={handleOverlayClick}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        {children}
      </div>
    </div>
  );
}

function StyleModal({ dataset, datasetStyle, workspace, closeModal, updateStyle }) {
  const completeStyle = {
    type: "basic",
    stroke_color: "black",
    stroke_width: 1,
    fill_color: "white",
    point_radius: 3,
    category_attribute: "none",
    categories: [{ value: "all others", color: "black" }],
    graduate_attribute: "none",
    min_color: "blue",
    max_color: "red",
    min_value: 0,
    max_value: 1,
    label_attribute: "none",
    opacity: 100,
    visible: true,
    ...datasetStyle
  }

  // Ensure the last category is always 'all others'
  const categories = completeStyle.categories;
  if (categories.length === 0 || categories[categories.length - 1].value !== 'all others') {
    categories.push({ value: 'all others', color: '#FFFFFF' });
  }
  const [style, setStyle] = useState(completeStyle);
  const attributes = dataset.schema && dataset.schema.map(attribute => attribute.name);

  const handleCategoryValueChange = (index, newValue) => {
    const updatedCategories = style.categories;
    updatedCategories[index] = { ...updatedCategories[index], value: newValue };
    setStyle({ ...style, categories: updatedCategories });
  }

  const handleCategoryColorChange = (index, newColor) => {
    const updatedCategories = style.categories;
    updatedCategories[index] = { ...updatedCategories[index], color: newColor };
    setStyle({ ...style, categories: updatedCategories });
  }

  const addCategory = () => {
    // Add new category above the catch-all 'all others' category
    const newCategories = style.categories.slice(0, -1);
    newCategories.push({ value: '', color: '#FFFFFF' }, style.categories[style.categories.length - 1]);
    setStyle({
      ...style,
      categories: newCategories
    });
  };

  const removeCategory = (index) => {
    // Allow removal of any category except the last one (the 'all others' category)
    if (style.categories.length > 1 && index !== style.categories.length - 1) {
      const updatedCategories = style.categories.filter((_, idx) => idx !== index);
      setStyle({
        ...style,
        categories: updatedCategories
      })
    }
  };

  const saveStyle = (e) => {
    e.preventDefault();
    // Ensure that you have the current workspace ID
    if (!workspace || !workspace.id) {
      console.error('No workspace selected');
      return;
    }
    // Logic to convert current state to JSON style object
    // Initialize with the common attributes
    let newStyle = {opacity: style.opacity, visible: style.visible};
    if (dataset.visualization_options.viz_type === "VectorTile" || dataset.visualization_options.viz_type === "GeoJSON") {
      newStyle = {...newStyle,
        type: style.type,
        label_attribute: style.label_attribute,
        stroke_width: style.stroke_width,
        point_radius: style.point_radius
      };
      if (style.type === 'basic') {
        // Update with attributes specific to the basic style
        newStyle = {
          ...newStyle,
          stroke_color: style.stroke_color,
          fill_color: style.fill_color
        };
      }
      if (style.type === 'categorized') {
        // Copy attributes specific to categorized style
        newStyle = {
          ...newStyle,
          category_attribute: style.category_attribute,
          categories: style.categories
        };
      }
      if (style.type === 'graduated') {
        // Copy attributes specific to graduated style
        newStyle = {
          ...newStyle,
          graduate_attribute: style.graduate_attribute,
          min_value: style.min_value,
          max_value: style.max_value,
          min_color: style.min_color,
          max_color: style.max_color
        };
      }
    }
    updateStyle(dataset.id, newStyle).then(closeModal);
  };

  const updateAtt = (e) => { setStyle({ ...style, [e.target.name]: e.target.value }) };

  // Function to render the circle preview
  const renderCirclePreview = () => {
    return (
      <svg width="50" height="50" className="circle-preview">
        <circle
          cx="25"
          cy="25"
          r={style.point_radius}
          stroke={style.stroke_color}
          strokeWidth={style.stroke_width}
          fill={style.fill_color}
          opacity={style.opacity / 100.0}
        />
      </svg>
    );
  };

  return <GenericModal closeModal={closeModal}>
      <form onSubmit={(e) => { e.preventDefault() }}>
        {(dataset.visualization_options.viz_type === "VectorTile" || dataset.visualization_options.viz_type === "GeoJSON") && (<>
          <div className="tabs">
            <label className={"tab " + (style.type === 'basic' && "active")}>Basic<input type="radio" checked={style.type === 'basic'} name="type" value="basic" onChange={updateAtt}></input></label>
            <label className={"tab " + (style.type === 'categorized' && "active")}>Categorized<input type="radio" checked={style.type === 'categorized'} name="type" value="categorized" onChange={updateAtt}></input></label>
            <label className={"tab " + (style.type === 'graduated' && "active")}>Graduated<input type="radio" checked={style.type === 'graduated'} name="type" value="graduated" onChange={updateAtt}></input></label>
          </div>
          {style.type === 'basic' && (
            <div className="controls">
              <label>Stroke Color: <input name="stroke_color" type="color" value={style.stroke_color} onChange={updateAtt} /> </label>
              <label>Fill Color: <input name="fill_color" type="color" value={style.fill_color} onChange={updateAtt} /> </label>
            </div>
          )}
          {style.type === 'categorized' && (
            <div className="controls">
              <label>Category Attribute:
                <select name="category_attribute" value={style.category_attribute} onChange={updateAtt} >
                  {attributes.map(attr => <option key={attr} value={attr}>{attr}</option>)}
                </select>
              </label>

              {style.categories.map((category, index) => (
                <div key={index}>
                  <input type="text" value={category.value} placeholder="Value"
                    readOnly={index === style.categories.length - 1}
                    onChange={e => handleCategoryValueChange(index, e.target.value)} />
                  <input type="color" value={category.color}
                    onChange={e => handleCategoryColorChange(index, e.target.value)} />
                  {index < style.categories.length - 1 && <button onClick={() => removeCategory(index)}><i className="fas fa-trash-alt"></i></button>}
                </div>
              ))}
              <button onClick={addCategory}>Add Category</button>
            </div>
          )}
          {style.type === 'graduated' && (
            <div className="controls">
              <label>Graduated Attribute:
                <select name="graduate_attribute" value={style.graduate_attribute} onChange={updateAtt}>
                  {attributes.map(attr => <option key={attr} value={attr}>{attr}</option>)}
                </select>
              </label>
              <label>Min Value: <input name="min_value" type="number" value={style.min_value} onChange={updateAtt} /> </label>
              <label>Min Color: <input name="min_color" type="color" value={style.min_color} onChange={updateAtt} /> </label>
              <label>Max Value: <input name="max_value" type="number" value={style.max_value} onChange={updateAtt} /> </label>
              <label>Max Color: <input name="max_color" type="color" value={style.max_color} onChange={updateAtt} /> </label>
            </div>
          )}
          <div className="controls">
            <label>Stroke Width: <input name="stroke_width" type="number" value={style.stroke_width} min="1" max="10" onChange={updateAtt} /> </label>
            <label>Point Radius:
              <input type="range" name="point_radius" min="1" max="20" value={style.point_radius} onChange={updateAtt} />
            </label>
            <label>Label Attribute:
              <select name="label_attribute" value={style.label_attribute} onChange={updateAtt} >
                <option value="">-----</option>
                {attributes.map(attr => <option key={attr} value={attr} checked={style.label === attr}>{attr}</option>)}
              </select>
            </label>
            <span>Preview:</span>
            {renderCirclePreview()}
          </div>
        </>)}
        <div className="controls">
          <label>Opacity: <input name="opacity" type="range" min="0" max="100" value={style.opacity} onChange={updateAtt} /> </label>
        </div>
        <div className="buttons-container">
          <button type="submit" onClick={saveStyle}>Save</button>
          <button onClick={closeModal}>Cancel</button>
        </div>
      </form>
  </GenericModal>
}

// Function to calculate the combined extent of all visible datasets
function calculateCombinedExtent(datasets) {
  let combinedExtent = ol.extent.createEmpty();

  datasets.forEach(dataset => {
    if (dataset.mbr) {
      // Assuming dataset.mbr is in the format [minX, minY, maxX, maxY]
      let extent = dataset.mbr;
      ol.extent.extend(combinedExtent, extent);
    }
  });

  return combinedExtent;
}

class MapViewer extends React.Component {
  constructor(props) {
    super(props);
    this.map = null;
  }

  componentDidMount() {
    this.map = new ol.Map({
      target: 'map',
      layers: [/*new ol.layer.Tile({ source: new ol.source.OSM() })*/],
      view: new ol.View({ center: ol.proj.fromLonLat([-117.375494, 33.953349]), zoom: 12 })
    });
    this.addZoomToExtentControl();
  }

  componentDidUpdate(prevProps) {
    if (prevProps.datasets !== this.props.datasets) {
      this.updateLayers();
      this.updateStyles();
    }
    if (prevProps.datasetStyles !== this.props.datasetStyles) {
      this.updateStyles();
    }
    if (prevProps.extent !== this.props.extent) {
      this.map.getView().fit(clipExtents(this.props.extent), { duration: 500 })
    }
    this.updateZoomToExtentControl();
  }

  addZoomToExtentControl() {
    this.zoomAllButton = new ol.control.ZoomToExtent({
      className: 'ol-zoom-extent',
      label: '\uf31e',
      tipLabel: 'Zoom to all extents',
      extent: []
    });
    this.map.addControl(this.zoomAllButton);
  }

  updateLayers() {
    this.props.datasets.forEach((dataset, idx) => {
      if (dataset.visualization_options) {
        var layerExists = this.map.getLayers().getArray().some(layer => dataset.id === layer.get("datasetID"));
        if (!layerExists) {
          // First time visualizing this dataset, create a corresponding layer in the map
          let layer;
          if (dataset.visualization_options.viz_type === "VectorTile") {
            layer = new ol.layer.VectorTile({
              source: new ol.source.VectorTile({
                format: new ol.format.MVT(),
                url: dataset.visualization_options.viz_url
              })
            });
          } else if (dataset.visualization_options.viz_type === "OSM") {
            layer = new ol.layer.Tile({ source: new ol.source.OSM() });
          } else if (dataset.visualization_options.viz_type === "GeoJSON") {
            layer = new ol.layer.Vector({
              source: new ol.source.Vector({
                url: dataset.visualization_options.viz_url,
                format: new ol.format.GeoJSON()
              })
            });
          } else {
            console.error("Unsupported visualization type", dataset.visualization_options);
          }
          layer.set("datasetID", dataset.id);
          this.map.addLayer(layer);
        }
      }
    });
    // Next, remove layers for datasets that are no longer present
    this.map.getLayers().getArray().forEach((layer) => {
      const datasetID = layer.get("datasetID");
      if (!datasetID || !this.props.datasets.some(dataset => dataset.id === datasetID)) {
        this.map.removeLayer(layer);
      }
    })
  }

  updateStyles() {
    for (let [datasetID, datasetStyle] of Object.entries(this.props.datasetStyles)) {
      var idx = this.props.datasets.findIndex(dataset => dataset.id === parseInt(datasetID))
      var layer = this.map.getLayers().getArray().find(layer => parseInt(datasetID) === layer.get("datasetID"));
      if (layer) {
        // Update style of this dataset
        if (layer.setStyle) {
          layer.setStyle(getStyleFromDataset(datasetStyle));
        }
        // Update visibility of this dataset
        layer.setVisible(datasetStyle.visible);
        layer.setZIndex(this.props.datasets.length - idx);
        layer.setOpacity(datasetStyle.opacity / 100.0);
      }
    }
  }

  updateZoomToExtentControl() {
    const combinedExtent = calculateCombinedExtent(this.props.datasets.filter(ds => this.props.datasetStyles[ds.id] && this.props.datasetStyles[ds.id].visible));
    var convertedDatasetExtent = ol.proj.transformExtent(combinedExtent, 'EPSG:4326', 'EPSG:3857');
    if (!ol.extent.isEmpty(convertedDatasetExtent)) {
      this.zoomAllButton.extent = clipExtents(convertedDatasetExtent);
    }
  }

  render() {
    return <div id="map" className="map-viewer"></div>;
  }
}

function clipExtents(extents) {
  return [
    Math.max(extents[0], -20037508.34),
    Math.max(extents[1], -20048966.1),
    Math.min(extents[2], 20037508.34),
    Math.min(extents[3], 20048966.1)
  ];
}

function WorkspaceManager({ onWorkspaceSelect, currentWorkspace}) {
  const [workspaces, setWorkspaces] = useState([]);
  const [newWorkspaceName, setNewWorkspaceName] = useState('');
  const [newWorkspaceDescription, setNewWorkspaceDescription] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState(null);
  const [isCreatingNewWorkspace, setIsCreatingNewWorkspace] = useState(false);

  // Fetch workspaces
  useEffect(() => {
    fetch('/workspaces.json')
      .then(response => response.json())
      .then(data => setWorkspaces(data))
      .catch(error => setErrorMessage('Error fetching workspaces'));
  }, []);

  // Create a new workspace
  const createWorkspace = () => {
    const payload = { name: newWorkspaceName, description: newWorkspaceDescription };

    fetch('/workspaces.json', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    .then(response => {
      if (!response.ok) {
        throw new Error('Network response was not ok');
      }
      return response.json();
    })
    .then(newWorkspace => {
      setWorkspaces([...workspaces, newWorkspace]);
      setNewWorkspaceName('');
      setNewWorkspaceDescription('');
    })
    .catch(error => setErrorMessage('Error creating workspace'));
  };

  const updateWorkspace = () => {
    const payload = {
      name: newWorkspaceName,
      description: newWorkspaceDescription
    };

    fetch(`/workspaces/${selectedWorkspaceId}.json`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    .then(response => {
      if (!response.ok) {
        // If the server responds with a bad request
        throw new Error('Network response was not ok');
      }
      return response.json();
    })
    .then(updatedWorkspace => {
      // Update the local state with the updated workspace details
      setWorkspaces(workspaces.map(workspace =>
        workspace.id === selectedWorkspaceId ? updatedWorkspace : workspace
      ));

      // Optionally, reset form and selected workspace
      setSelectedWorkspaceId(null);
      setNewWorkspaceName('');
      setNewWorkspaceDescription('');
      setIsCreatingNewWorkspace(false);
    })
    .catch(error => {
      console.error('Error updating workspace:', error);
      setErrorMessage(error.message || "An unknown error occurred while updating the workspace.");
    });
  };

  // Delete a workspace
  const deleteWorkspace = (workspaceId) => {
    if (window.confirm("Are you sure you want to delete this workspace?")) {
      fetch(`/workspaces/${workspaceId}`, { method: 'DELETE' })
        .then(response => {
          if (!response.ok) {
            throw new Error('Network response was not ok');
          }
          setWorkspaces(workspaces.filter(workspace => workspace.id !== workspaceId));
        })
        .catch(error => setErrorMessage('Error deleting workspace'));
    }
  };

  // Set workspace selected
  const editWorkspace = (workspaceId) => {
    setSelectedWorkspaceId(workspaceId);
    const selectedWorkspace = workspaces.find(workspace => workspace.id === workspaceId);
    setNewWorkspaceName(selectedWorkspace.name);
    setNewWorkspaceDescription(selectedWorkspace.description);
    setIsCreatingNewWorkspace(false);
  };

  // Open a workspace
  const openWorkspace = (workspaceId) => {
    const selectedWorkspace = workspaces.find(workspace => workspace.id === workspaceId);
    onWorkspaceSelect(selectedWorkspace);
  };

  // Create a new workspace logic
  const handleCreateNewWorkspace = () => {
    setIsCreatingNewWorkspace(true);
    setSelectedWorkspaceId(null);
    setNewWorkspaceName('');
    setNewWorkspaceDescription('');
  };

  return (
    <div className="workspace-manager">
      <h2>Workspaces</h2>
      <div className="workspace-list">
        {workspaces.map(workspace => (
          <div key={workspace.id} onClick={() => editWorkspace(workspace.id)}
              className={`workspace-item ${workspace.id === selectedWorkspaceId ? 'selected' : ''}`}>
            <span>{workspace.name}</span>
            <div className="workspace-actions">
              <i className="icon-button fas fa-trash-alt" title="Delete" onClick={() => confirmDeleteWorkspace(workspace.id)}></i>
            </div>
          </div>
        ))}
      </div>
      <form onSubmit={(e) => {e.preventDefault();}}>
        {selectedWorkspaceId && (
          <div className="workspace-details">
            <label>
              <span>Name:</span>
              <input type="text" value={newWorkspaceName} onChange={e => setNewWorkspaceName(e.target.value)} />
            </label>
            <label>
              <span>Description:</span>
              <textarea rows="3" value={newWorkspaceDescription} onChange={e => setNewWorkspaceDescription(e.target.value)}></textarea>
            </label>
          </div>
        )}
        {isCreatingNewWorkspace && (
          <div className="workspace-details">
            <input type="text" value={newWorkspaceName} onChange={e => setNewWorkspaceName(e.target.value)} placeholder="Workspace Name" />
            <textarea rows="3" value={newWorkspaceDescription} onChange={e => setNewWorkspaceDescription(e.target.value)} placeholder="Workspace Description"></textarea>
          </div>
        )}
        {errorMessage && <div className="error-message">{errorMessage}</div>}
        {/* Buttons container */}
        <div className="buttons-container">
          {isCreatingNewWorkspace && (<button onClick={createWorkspace}>Create</button>) }
          {selectedWorkspaceId && ( <button onClick={updateWorkspace}>Update</button> )}
          {selectedWorkspaceId && ( <button onClick={() => openWorkspace(selectedWorkspaceId)}>Open</button>)}
          {!isCreatingNewWorkspace && (<button onClick={handleCreateNewWorkspace} className="new-workspace-button">Create Workspace</button>) }
        </div>
      </form>
    </div>
  );
}

const CSVTable = ({ headerRow, data, highlightColumns }) => {
  return (
    <div className="csv-table-container">
      <table className="csv-table">
        <thead>
          {headerRow && (
            <tr>
              {headerRow.map((header, index) => (
                <th key={index}>{header}</th>
              ))}
            </tr>
          )}
        </thead>
        <tbody>
          {data.map((row, rowIndex) => (
            <tr key={rowIndex}>
              {row.map((cell, cellIndex) => (
                <td key={cellIndex} style={{
                  backgroundColor: highlightColumns.includes(cellIndex) ? '#efe' : 'transparent'
                }}>
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

function NewDatasetModal({ closeModal, onDatasetCreate, currentWorkspace, currentDatasets }) {
  const [formData, setFormData] = useState({
    name: '',
    source_uri: '',
    source_format: 'geojson',
    header: true,
    separator: ',',
    quotes: '"',
    geometry_type: 'point',
    x_column: '',
    y_column: '',
    wkt_column: '',
    wkt_attribute: '', // For JSON+WKT
    geometry_column: '' // For GeoParquet and SpatialParquet
  });

  const [errorMessage, setErrorMessage] = useState(null);
  const [allDatasets, setAllDatasets] = useState([]);
  const [workspaceDatasets, setWorkspaceDatasets] = useState(currentDatasets);
  const [selectedTab, setSelectedTab] = useState("new");
  const [fileSample, setFileSample] = useState(null);
  const [fileBrowserVisible, setFileBrowserVisible] = useState(false);

  const commonSeparators = {",": "Comma (,)", "\t": "Tab", " ": "Space", ";": "Semicolon (;)"};

  const setSourceURI = (uri) => {
    // Fake an event
    handleChange({target: {name: "source_uri", value: uri, type: "text", checked: false}});
  }

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    // For checkboxes, use 'checked'; for other inputs, use 'value'
    const newValue = type === 'checkbox' ? checked : value;
      const newFormData = { ...formData, [name]: newValue };

    // Additional logic for source_uri changes
    if (name === 'source_uri') {
      setFileSample(null); // Invalidate the cache
      const updatedFormat = detectFormatFromURI(value);
      if (updatedFormat)
        newFormData.source_format = updatedFormat;
    }

    setFormData(newFormData);
    setErrorMessage(null); // Reset error message on input change
  };

  const handleAutoDetect = async (e) => {
    let initialData;
    if (!fileSample) {
      initialData = await fetchInitialDataForDetection(formData.source_uri);
      setFileSample(initialData);
    } else {
      initialData = fileSample;
    }
    let detectedFormat;
    if (initialData) {
      if (formData.source_format === 'csv') {
        // Call your CSV detection function here
        detectedFormat = detectCSVFormat(initialData);
        if (detectedFormat) {
          const newFormData = {...formData};
          if (detectedFormat.separator in commonSeparators) {
            newFormData.separator = detectedFormat.separator;
          } else {
            newFormData.separator = "other";
            newFormData.customSeparator = detectedFormat.separator;
          }
          if (!detectedFormat.quoteChar)
            newFormData.quotes = "";
          if (detectedFormat.quoteChar === '"' || detectedFormat.quoteChar === "'")
            newFormData.quotes = detectedFormat.quoteChar;
          else {
            newFormData.quotes = "other";
            newFormData.otherQuotes = detectedFormat.quotes;
          }
          newFormData.header = detectedFormat.header;
          newFormData.geometry_type = detectedFormat.geometry_type;
          if (detectedFormat.geometry_type === "wkt") {
            newFormData.wkt_column = detectedFormat.wkt_column;
          } else if (detectedFormat.geometry_type === "point") {
            newFormData.x_column = detectedFormat.x_column;
            newFormData.y_column = detectedFormat.y_column;
          }
          setFormData(newFormData);
        }
      } else if (formData.source_format === 'json+wkt') {
        // Call your JSON detection function here
        detectedFormat = detectJSONFormat(initialData);
      }
    }
  };

  // Function to convert ArrayBuffer to string
  const arrayBufferToString = (buffer) => {
    const decoder = new TextDecoder('utf-8');
    return decoder.decode(buffer);
  };

  const renderCSVTable = () => {
    if (!fileSample || formData.source_format !== 'csv') return null;

    const fileString = arrayBufferToString(fileSample);
    // Split into lines assuming Linux line ending. We skip the last line which might be incomplete
    const data = parseCSV(fileString, formData.separator, formData.quotes, formData.header);

    if (data.length === 0) return <p>Invalid or empty CSV data.</p>;

    const headerRow = formData.header ? data[0] : data[0].map((_, index) => index.toString());
    // Skip header if set, and skip last row which might be incomplete
    const rowData = formData.header ? data.slice(1,-1) : data.slice(0,-1);

    const highlightColumns = [];
    if (formData.geometry_type === 'point') {
      if (!formData.header) {
        formData.x_column = formData.x_column.toString();
        formData.y_column = formData.y_column.toString();
      }
      highlightColumns.push(headerRow.indexOf(formData.x_column), headerRow.indexOf(formData.y_column));
    } else if (formData.geometry_type === 'wkt') {
      if (!formData.header) {
        formData.wkt_column = formData.wkt_column.toString();
      }
      highlightColumns.push(headerRow.indexOf(formData.wkt_column));
    }

    return (
      <CSVTable
        headerRow={headerRow}
        data={rowData}
        highlightColumns={highlightColumns}
      />
    );
  };


  // Fetch all datasets on component mount
  useEffect(() => {
    fetch('/datasets.json')
      .then(response => response.json())
      .then(data => setAllDatasets(data))
      .catch(error => console.error('Error fetching datasets:', error));
  }, []);

  // Handle adding dataset to workspace
  const handleAddDataset = (dataset) => {
    fetch(`/workspaces/${currentWorkspace.id}/datasets/${dataset.name}`, { method: 'POST' })
      .then(response => {
        if (response.ok) {
          setWorkspaceDatasets(prev => [...prev, allDatasets.find(d => d.id === dataset.id)]);
        } else {
          throw new Error('Failed to add dataset to workspace');
        }
      })
      .catch(error => {
        console.error('Error adding dataset to workspace:', error);
        setErrorMessage(error.message || "An unknown error occurred");
      });
  };

  // Handle removing dataset from workspace
  const handleRemoveDataset = (dataset) => {
    fetch(`/workspaces/${currentWorkspace.id}/datasets/${dataset.name}`, { method: 'DELETE' })
      .then(response => {
        if (response.ok) {
          setWorkspaceDatasets(prev => prev.filter(d => d.name !== dataset.name));
        } else {
          throw new Error('Failed to add dataset to workspace');
        }
      })
      .catch(error => {
        console.error('Error adding dataset to workspace:', error);
        setErrorMessage(error.message || "An unknown error occurred");
      });
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    // POST request to create a new dataset
    let beastOptions = {};
    if (formData.source_format === 'csv') {
      beastOptions = {
        header: formData.header,
        sep: formData.separator === 'other'? formData.customSeparator : formData.separator,
        quote: formData.quotes === 'other' ? formData.customQuote : formData.quotes,
        geometry_type: formData.geometry_type,
        dimensions: formData.geometry_type === 'point' ? `${formData.x_column},${formData.y_column}` : formData.wkt_column
      };
    }

    var request = {
      name: formData.name,
      source_uri: formData.source_uri,
      source_format: formData.source_format,
      beast_options: beastOptions
    };
    fetch('/datasets.json', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request)
    })
      .then(response => {
        if (!response.ok) {
          // Throw an error if the response status is not OK
          return response.json().then(err => { throw err; });
        }
        return response.json()
      })
      .then(data => {
        onDatasetCreate(data);
        // Check if there's an active workspace
        if (currentWorkspace && currentWorkspace.id) {
          // Add dataset to the workspace
          return fetch(`/workspaces/${currentWorkspace.id}/datasets/${data.name}`, {
            method: 'POST'
          });
        }
      })
      .then(workspaceResponse => {
        if (workspaceResponse && !workspaceResponse.ok) {
          // Handle errors when adding dataset to workspace
          throw new Error('Failed to add dataset to workspace');
        }
        closeModal(); // Close modal if everything is successful
      })
      .catch(error => {
        // Handle any errors that occur during fetch or in the .then() callbacks
        console.error('Error creating dataset:', error);
        // Assuming you have a state or method to show error messages in the UI
        setErrorMessage(error.error || "An unknown error occurred");
      });
  };

  return (
    <GenericModal closeModal={closeModal}>
      <div className="tabs">
        <label className={"tab " + (selectedTab === 'new' && "active")}>New<input type="radio" checked={selectedTab === 'new'} onChange={() => setSelectedTab("new")}></input></label>
        <label className={"tab " + (selectedTab === 'add' && "active")}>Add<input type="radio" checked={selectedTab === 'add'} onChange={() => setSelectedTab("add")}></input></label>
      </div>
      {selectedTab === 'new' && (<>
        <h3>Create a new Dataset</h3>
        <form onSubmit={handleSubmit}>
          <div className="controls">
            <label>
              <span>Name:</span>
              <input type="text" name="name" value={formData.name} onChange={handleChange} required />
            </label>
            <label>
              <span>Source URI:</span>
              <span>
                <input type="text" name="source_uri" value={formData.source_uri} onChange={handleChange} required />
                <span className="icon-button fas fa-folder" title="Browse" onClick={() => setFileBrowserVisible(true)} />
              </span>
            </label>
            <label>
              <span>Source Format:</span>
              <select name="source_format" value={formData.source_format} onChange={handleChange}>
                <option value="geojson">GeoJSON</option>
                <option value="shapefile">Shapefile</option>
                <option value="csv">CSV</option>
                <option value="gpx">GPX</option>
                <option value="json+wkt">JSON+WKT</option>
                <option value="geoparquet">GeoParquet</option>
                <option value="spatialparquet">SpatialParquet</option>
              </select>
            </label>
            {/* Conditional rendering for CSV options */}
            {formData.source_format === 'csv' && (
              <>
                <label><input name="header" type="checkbox" checked={formData.header} onChange={handleChange}></input>Header</label>
                <span>Separator:</span>
                {Object.entries(commonSeparators).map(([key, label]) => (
                  <label key={key}><input type="radio" name="separator" value={key} onChange={handleChange} checked={formData.separator === key}></input>{label}</label>
                ))}
                <label><input type="radio" name="separator" value="other" onChange={handleChange} checked={formData.separator === "other"}></input>Other</label>
                {formData.separator === 'other' && (
                  <input type="text" name="customSeparator" maxLength="1" value={formData.customSeparator} onChange={handleChange} />
                )}
                <span>Quotes:</span>
                <label><input type="radio" name="quotes" value="" onChange={handleChange} checked={formData.quotes === ""}></input>None</label>
                <label><input type="radio" name="quotes" value='"' onChange={handleChange} checked={formData.quotes === '"'}></input>Double Quotes (&quot;)</label>
                <label><input type="radio" name="quotes" value="'" onChange={handleChange} checked={formData.quotes === "'"}></input>Single Quote (&apos;)</label>
                <label><input type="radio" name="quotes" value="other" onChange={handleChange} checked={formData.quotes === "other"}></input>Other</label>
                {formData.quotes === 'other' && (
                  <input required="yes" type="text" name="customQuote" maxLength="1" value={formData.customQuote} onChange={handleChange} />
                )}
                <span>Geometry Type:</span>
                <label><input type="radio" name="geometry_type" value="point" onChange={handleChange} checked={formData.geometry_type === "point"}></input>Point</label>
                <label><input type="radio" name="geometry_type" value="wkt" onChange={handleChange} checked={formData.geometry_type === "wkt"}></input>WKT</label>
                {formData.geometry_type === 'point' && (
                  <>
                    <label>
                      <span>X Column:</span>
                      <input type="text" name="x_column" value={formData.x_column} onChange={handleChange} />
                    </label>
                    <label>
                      <span>Y Column:</span>
                      <input type="text" name="y_column" value={formData.y_column} onChange={handleChange} />
                    </label>
                  </>
                )}
                {formData.geometry_type === 'wkt' && (
                  <label>
                    <span>WKT Column:</span>
                    <input type="text" name="wkt_column" value={formData.wkt_column} onChange={handleChange} />
                  </label>
                )}
              </>
            )}
            {/* Conditional rendering for JSON+WKT options */}
            {formData.source_format === 'json+wkt' && (
              <label>
                <span>WKT Attribute:</span>
                <input type="text" name="wkt_attribute" value={formData.wkt_attribute} onChange={handleChange} required />
              </label>
            )}

            {/* Conditional rendering for GeoParquet and SpatialParquet options */}
            {(formData.source_format === 'geoparquet' || formData.source_format === 'spatialparquet') && (
              <label>
                <span>Geometry Column:</span>
                <input type="text" name="geometry_column" value={formData.geometry_column} onChange={handleChange} required />
              </label>
            )}
          </div>
          <div className="buttons-container">
            {(formData.source_format === 'csv' || formData.source_format === 'json+wkt') && (
              <button type="button" onClick={handleAutoDetect}>Auto-detect Format</button>
            )}
            <button type="submit">Create Dataset</button>
            <button type="button" onClick={closeModal}>Cancel</button>
          </div>
        </form>
      </>)}
      {selectedTab === 'add' && (
        <div className="dataset-list-container">
          <h4>Current Workspace Datasets</h4>
          <DatasetList
            datasets={workspaceDatasets}
            actions={[{icon: () => "fas fa-trash-alt red-icon", onClick: handleRemoveDataset }]}
          />
          <h4>Available Datasets</h4>
          <DatasetList
            datasets={ allDatasets.filter(ad => !workspaceDatasets.some(wd => wd.id === ad.id))}
            actions={[{icon: () => "fas fas fa-plus green-icon", onClick: handleAddDataset }]
            }
          />
          <div className="buttons-container">
            <button type="button" onClick={closeModal}>Done</button>
          </div>
        </div>
      )}
      {errorMessage && <div className="error-message">{errorMessage}</div>}
      {renderCSVTable()}
      {fileBrowserVisible && <FileSystemBrowser
          onSelection={setSourceURI}
          closeModal={() => setFileBrowserVisible(false)} />}
    </GenericModal>
  );
}

// DatasetDetails Component
function DatasetDetails({ dataset, opacity, updateOpacity, reloadDataset }) {
  const [downloadFormat, setDownloadFormat] = useState(".csv.gz");
  const downloadFormats = [
    {name: "CSV", extension: ".csv.gz"},
    {name: "KML", extension: ".kml"},
    {name: "Shapefile", extension: ".zip"},
    {name: "GeoJSON", extension: ".geojson.gz"},
    {name: "JSON+WKT", extension: ".json.gz"}
  ];
  const [exportDialogVisible, setExportDialogVisible] = useState(false);
  const processDataset = () => {
    return fetch(`/datasets/${dataset.name}/process`, {
      method: 'POST'
    }).then(response => {
      if (!response.ok)
        throw new Error('Network response was not ok');
      return response.json();
    }).then(() => {
      reloadDataset(dataset.name);
    });
  };
  return (
    <div className="dataset-details">
      {dataset && (<>
      <h3>{dataset.name}</h3>
      {dataset.num_features && (<div>Number of Features: {dataset.num_features}</div>)}
      {dataset.num_points && (<div>Number of Points: {dataset.num_points}</div>)}
      {dataset.size && (<div>Size: {dataset.size}</div>)}
      <span>Opacity:</span>
      <input type="range" min="0" max="100" className="opacity-slider"
        value={opacity}
        onChange={(e) => updateOpacity(dataset, parseInt(e.target.value))}
      />
      {dataset.schema && (<>
        <div>Schema:</div>
        <div style={{ display: 'flex', flexWrap: 'wrap' }}>
           {dataset.schema.map(field => (
            <div key={field.name} className="schema-card" title={field.type}>
              <span className={`schema-icon`} data-type={field.type || 'default'}></span>
              <span>{field.name}</span>
            </div>
          ))}
        </div>
        </>
      )}
      {dataset.progress < 100 && !dataset.in_progress && (
        <button onClick={processDataset}>Process Dataset</button>
      )}
      {dataset.progress === 100 && ( <>
        <div>
          Download:
          <select name="download_format" value={downloadFormat} onChange={(e) => setDownloadFormat(e.target.value)}>
            {downloadFormats.map((format) => (
              <option key={format.extension} value={format.extension}>{format.name}</option>
            ))}
          </select>
          <a href={`/datasets/${dataset.name}/download${downloadFormat}`}>Download Link</a>
        </div>
        <div>
          <button onClick={() => setExportDialogVisible(true)}>Export</button>
        </div>
      </>)}
      {dataset.error_message && ( <div className="dataset-error"> Error: {dataset.error_message} </div> )}
      </>
      )}
      {exportDialogVisible && (
        <DataExportDialog dataset={dataset} closeModal={() => {setExportDialogVisible(false); reloadDataset(dataset.name);}}>
        </DataExportDialog>
      )}
    </div>
  );
}

function DatasetCleanupModal({ closeModal }) {
  const [unusedDatasets, setUnusedDatasets] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch('/datasets/dangling.json')
      .then(response => response.json())
      .then(data => {
        setUnusedDatasets(data);
        setLoading(false);
      })
      .catch(error => console.error('Error fetching dangling datasets:', error));
  }, []);


  const handleDeleteDataset = (dataset) => {
    if (window.confirm(`Are you sure you want to delete the dataset '${dataset.name}' completely?`)) {
      fetch(`/datasets/${dataset.name}`, { method: 'DELETE' })
        .then(response => {
          if (response.ok) {
            setUnusedDatasets(unusedDatasets.filter(ds => ds.id !== dataset.id));
          } else {
            console.error('Failed to delete dataset:', dataset.name);
          }
        })
        .catch(error => console.error('Error deleting dataset:', datasetName, error));
    }
  };

  const datasetActions = [{icon: () => "fas fa-trash-alt", onClick: (dataset) => handleDeleteDataset(dataset)}];

  return (
    <GenericModal closeModal={closeModal}>
      <h3>Unused Datasets</h3>
      <DatasetList datasets={unusedDatasets} actions={datasetActions} />
      <div className="buttons-container">
        <button onClick={closeModal}>Close</button>
      </div>
    </GenericModal>
  );
}

// Dialog to export dataset
function DataExportDialog({ dataset, closeModal }) {
  const [fileBrowserVisible, setFileBrowserVisible] = useState(false);
  const [exportFormat, setExportFormat] = useState("GeoJSON");
  const [path, setPath] = useState("");
  const exportFormats = [
    {name: "GeoJSON", format: "geojson"},
    {name: "JSON+WKT", format: "json+wkt"},
    {name: "Shapefile", format: "shapefile"},
    {name: "GeoParquet", format: "geoparquet"},
    {name: "SpatialParquet", format: "spatialparquet"}
  ];

  const exportDataset = (e) => {
    e.preventDefault();
    // Construct the API endpoint URL
    const apiUrl = `/datasets/${encodeURIComponent(dataset.name)}/export`;
    // Set up the request options, including method, headers, and body
    const requestOptions = {
      method: 'POST', // Assuming the export operation is a POST request
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        format: exportFormat,
        path: path
      })
    };

    fetch(apiUrl, requestOptions)
      .then(response => {
        if (!response.ok) {
          // If server response is not ok, throw an error
          throw new Error(`Server responded with ${response.status}: ${response.statusText}`);
        }
        return response.json(); // Assuming the server returns JSON; adjust if different
      })
      .then(data => { closeModal(); })
      .catch(error => {
        console.error('Export failed:', error); // Handle any errors that occurred during the fetch
      });
  };


  return (
    <GenericModal closeModal={closeModal}>
      <h2>Export Dataset</h2>
      <form onSubmit={exportDataset}>
        <label>
          <span>Export Path</span>
          <span>
            <input type="text" name="path" value={path} onChange={(e) => setPath(e.target.value)} />
            <span className="icon-button fas fa-folder" title="Browse" onClick={() => setFileBrowserVisible(true)} />
          </span>
        </label>
        <label>
          <span>Format</span>
          <span>
            <select name="format" value={exportFormat} onChange={(e) => setExportFormat(e.target.value)} >
              {exportFormats.map((opt) => (<option key={opt.format} value={opt.format}>{opt.name}</option>) )}
            </select>
          </span>
        </label>
        <div className="buttons-container">
          <button type="submit">Export</button>
          <button type="button" onClick={closeModal}>Cancel</button>
        </div>
      </form>
      {fileBrowserVisible && <FileSystemBrowser onSelection={(path) => setPath(path)}
                closeModal={() => setFileBrowserVisible(false)} />}
    </GenericModal>
  );
}

function FileSystemBrowser({ onSelection, closeModal }) {
  const [currentPath, setCurrentPath] = useState('');
  const [filesAndDirs, setFilesAndDirs] = useState([]);
  const [errorMessage, setErrorMessage] = useState('');

  const fetchData = (path) => {
    fetch(path === ""? `/listfiles/` : `/listfiles${path}`)
      .then(response => response.json())
      .then(data => {
        if (data.contents) {
          const sortedContents = data.contents.sort((a, b) => {
            // Sort directories before files
            if (a.type === 'directory' && b.type !== 'directory') {
              return -1;
            } else if (a.type !== 'directory' && b.type === 'directory') {
              return 1;
            }
            // If both are files or both are directories, sort alphabetically
            return a.name.localeCompare(b.name);
          });
          setFilesAndDirs(sortedContents);
        } else {
          setFilesAndDirs([]);
        }
        if (currentPath !== data.path)
          setCurrentPath(data.path);
      })
      .catch(error => setErrorMessage('Error fetching file system data'));
  };

  useEffect(() => {
    fetchData(currentPath);
  }, [currentPath]);

  const handleFileOrDirClick = (item) => {
    if (item.type === 'directory') {
      setCurrentPath(item.path);
    } else {
      if (onSelection)
        onSelection(item.path);
      closeModal();
    }
  };

  const handleGoUp = () => {
    const upOneLevel = currentPath.substring(0, currentPath.lastIndexOf('/'));
    setCurrentPath(upOneLevel || '');
  };

  const selectCurrent = () => {
    if (onSelection)
      onSelection(currentPath);
    closeModal();
  };

  return (
    <GenericModal closeModal={closeModal}>
      <div>
        {errorMessage && <p>{errorMessage}</p>}
        <div className="buttons-container">
          <button onClick={handleGoUp} title="Go up one level">
            <i className="fas fa-arrow-up" /> Up
          </button>
        </div>
        <ul className="file-system-list">
          {filesAndDirs.map((item, index) => (
            <li key={index} onClick={() => handleFileOrDirClick(item)}>
              <i className={`fas ${item.type === 'directory' ? "fa-folder" : "fa-file"}`} />
              {item.name}
            </li>
          ))}
        </ul>
        <div className="buttons-container">
          <button onClick={selectCurrent} title="Select current">
            <i className="fas fa-check" />Select
          </button>
          <button onClick={closeModal} title="Close">Close</button>
        </div>
      </div>
    </GenericModal>
  );
}

// App Component (Main Component)
function App() {
  // The list of all datasets in the project
  const [datasets, setDatasets] = useState([]);
  // A map that holds styles for each dataset indexed by their ID.
  const [datasetStyles, setDatasetStyles] = useState({});
  // The dataset currently being selected for more details
  const [selectedDataset, setSelectedDataset] = useState(null);
  // The extent of all visible datasets
  const [mapExtent, setMapExtent] = useState(null);
  // Style modal
  const [styleModalVisible, setStyleModalVisible] = useState(false);
  // Create new dataset modal
  const [newDatasetModalVisible, setNewDatasetModalVisible] = useState(false);
  const [currentWorkspace, setCurrentWorkspace] = useState(null); // Current active workspace
  const [showDatasetCleanup, setShowDatasetCleanup] = useState(false);

  const handleDatasetCreate = (newDataset) => {
    const newDatasets = datasets.map(dataset => dataset.name == newDataset.name ? newDataset : dataset);
    setDatasets(newDatasets);
  };

  // Updates the style of this dataset in the server and in the page
  const updateStyle = (datasetID, newStyle) => {
    const datasetName = datasets.find(dataset => dataset.id === datasetID).name;
    try {
      return fetch(`/workspaces/${currentWorkspace.id}/datasets/${datasetName}/style.json`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(newStyle)
        }).then(response => {
          if (!response.ok)
            throw new Error('Network response was not ok');
          return response.json();
        }).then(() => {
          // Successful, update locally
          const newStyles = {...datasetStyles};
          newStyles[datasetID] = newStyle;
          setDatasetStyles(newStyles);
        });
    } catch (error) {
      console.error('Error updating style:', error);
    };
  }

  // Load the last open workspace initially
  useEffect(() => {
    const storedWorkspaceId = localStorage.getItem('currentWorkspaceId');
    if (storedWorkspaceId) {
      fetch(`/workspaces/${storedWorkspaceId}.json`)
        .then(response => response.json())
        .then(workspaceDetails => {
          handleWorkspaceSelect(workspaceDetails);
        })
    }
  }, []);

  // Reload any datasets that are still in_progress
  useEffect(() => {
    console.log("Reloading in progress datasets");
    let intervalDuration = 1000; // Start with 1 second
    const maxInterval = 60000; // Max interval of 60 seconds

    const intervalId = setInterval(() => {
      const inProgressDatasets = datasets.filter(dataset => dataset.in_progress);

      // Reload each in-progress dataset
      inProgressDatasets.forEach(dataset => reloadDataset(dataset.name));

      if (inProgressDatasets.length === 0) {
        clearInterval(intervalId);
      } else {
        intervalDuration = Math.min(intervalDuration * 2, maxInterval);
      }
    }, intervalDuration);

    return () => clearInterval(intervalId); // Clear interval on component unmount
  }, [datasets]); // Depend on datasets state

  const handleWorkspaceSelect = (workspace) => {
    setCurrentWorkspace(workspace);
    localStorage.setItem('currentWorkspaceId', workspace.id);
    // Fetch datasets for the selected workspace
    return fetch(`/workspaces/${workspace.id}/datasets.json`)
      .then(response => response.json())
      .then(data => {
        // Assuming the response is an array of datasets
        const newDatasets = data;
        const newDatasetStyles = {};
        newDatasets.forEach(dataset => {
          newDatasetStyles[dataset.id] = dataset.style || createRandomStyle();
        });
        setDatasetStyles(newDatasetStyles);
        setDatasets(newDatasets);
      })
      .catch(error => {
        console.error('Error fetching datasets for workspace:', workspace.name, error);
        // Handle errors, possibly resetting datasets
        setDatasets([]);
      });
  };

  const reloadDataset = (datasetName) => {
    fetch(`/datasets/${datasetName}.json`)
      .then(response => response.json())
      .then(updatedDataset => {
        const newDatasets = datasets.map(dataset => dataset.name == datasetName ? updatedDataset : dataset);
        setDatasets(newDatasets);
      })
  }

  const handleDatasetSelect = (dataset) => {
    if (!selectedDataset || dataset.id !== selectedDataset.id) {
      fetch(`/datasets/${dataset.name}.json`)
        .then(response => response.json())
        .then(newDataset => {
          const newDatasets = datasets.map(ds => ds.name == dataset.name ? newDataset : ds);
          setDatasets(newDatasets);
          setSelectedDataset(newDataset);
        })
        .catch(error => console.error('Error fetching dataset details:', error));
    }
  };

  const deleteDataset = (dataset) => {
    if (window.confirm(`Are you sure you want to remove the dataset '${dataset.name}' from this workspace?`)) {
      fetch(`/workspaces/${currentWorkspace.id}/datasets/${dataset.name}`, { method: 'DELETE' })
        .then(response => {
          if (!response.ok) {
            throw new Error('Network response was not ok');
          }
          // Notify the parent component to remove the deleted dataset
          setDatasets(datasets.filter(ds => ds.id !== dataset.id));
          console.log(`Dataset '${dataset.name}' removed successfully from the current workspace.`);
        })
        .catch(error => {
          console.error('Error removing dataset from workspace:', error);
        });
    }
  };

  const toggleDatasetVisibility = (datasetID) => {
    const newDatasetStyle = {...datasetStyles[datasetID]};
    newDatasetStyle.visible = !newDatasetStyle.visible ?? true;
    return updateStyle(datasetID, newDatasetStyle);
  };

  const updateDatasetOpacity = (dataset, newOpacity) => {
    const newDatasetStyle = {...datasetStyles[dataset.id]};
    newDatasetStyle.opacity = newOpacity;
    return updateStyle(dataset.id, newDatasetStyle);
  };

  const onReorderDatasets = (draggedIdx, droppedIdx) => {
    const newDatasets = [...datasets];
    const draggedItem = newDatasets[draggedIdx];

    // Remove the dragged item and insert it at the new position
    newDatasets.splice(draggedIdx, 1);
    newDatasets.splice(droppedIdx <= draggedIdx ? droppedIdx : droppedIdx - 1, 0, draggedItem);

    // Prepare the ordered list of dataset IDs to send to the backend
    const orderedDatasetIds = newDatasets.map(dataset => dataset.id);

    // Call the backend to update the order in the database
    return fetch(`/workspaces/${currentWorkspace.id}/datasets/reorder`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(orderedDatasetIds),
    }).then(response => {
      if (!response.ok) {
        throw new Error('Failed to update dataset order');
      }
      setDatasets(newDatasets);
    }).catch((error) => {
      console.error('Error updating dataset order:', error);
      // Optionally, inform the user of the error
      // Revert to original dataset order if necessary
    });
  };

  const runQuery = () => {
    // TODO Query logic here
  };

  const zoomToDataset = (dataset) => {
    if (dataset.mbr) {
      // Assuming dataset.mbr is in the format [minX, minY, maxX, maxY]
      let extent = dataset.mbr;
      // Transform extent from 'EPSG:4326' to the map's projection 'EPSG:3857'
      let transformedExtent = ol.proj.transformExtent(extent, 'EPSG:4326', 'EPSG:3857');
      setMapExtent(transformedExtent);
    }
  }

  const datasetActions = [
    {icon: () => "fas fa-search-plus", onClick: zoomToDataset},
    {icon: (dataset) => "fas "+((datasetStyles[dataset.id] && datasetStyles[dataset.id].visible)? "fa-eye" : "fa-eye-slash"),
      onClick: (dataset) => toggleDatasetVisibility(dataset.id)},
    {icon: () => "fas fa-trash-alt", onClick: (dataset) => deleteDataset(dataset)}
  ];

  return (
    <div>
      <div className="sidebar">
        <div className="logo">
          <img src="beast-logo.svg" alt="Beast" />
        </div>
        <div className="toolbar">
          <span className={`icon-button fas fa-plus ${currentWorkspace ? '' : 'disabled'}`} title="Add Dataset"
            onClick={() => currentWorkspace && setNewDatasetModalVisible(true)} />
          <span className={`fas fa-cog icon-button ${currentWorkspace ? '' : 'disabled'}`} title="Run Query"
            onClick={() => currentWorkspace && runQuery()} />
          <span className="icon-button fas fa-eraser" title="Cleanup Datasets"
            onClick={() => {setShowDatasetCleanup(true)}} />
        </div>
        {currentWorkspace ? ( <>
          <h2 className="workspace-name">{currentWorkspace.name}
            <span className="icon-button close-workspace-button fas fa-times" title="Close workspace"
                onClick={() => {setCurrentWorkspace(null); setSelectedDataset(null);}} />
          </h2>
          <DatasetList
            datasets={datasets}
            selectedDataset={selectedDataset}
            actions={datasetActions}
            clickAction={handleDatasetSelect}
            doubleClickAction={() => { setStyleModalVisible(true); }}
            enableReorder={true}
            onReorderDatasets={onReorderDatasets}
          />
          </>
        ) : (
          <WorkspaceManager onWorkspaceSelect={handleWorkspaceSelect} currentWorkspace={currentWorkspace}/>
        )}
        {selectedDataset && (
          <DatasetDetails dataset={selectedDataset} opacity={datasetStyles[selectedDataset.id].opacity}
              updateOpacity={updateDatasetOpacity} reloadDataset={reloadDataset} />
        )}
      </div>
      <MapViewer
        datasets={datasets}
        datasetStyles={datasetStyles}
        extent={mapExtent}
      />
      {styleModalVisible && (
        <StyleModal
          dataset={selectedDataset}
          datasetStyle={datasetStyles[selectedDataset.id]}
          workspace={currentWorkspace}
          closeModal={() => { setStyleModalVisible(false); }}
          updateStyle={updateStyle}
        />
      )}
      {newDatasetModalVisible && (
        <NewDatasetModal
          closeModal={() => handleWorkspaceSelect(currentWorkspace).then(() => setNewDatasetModalVisible(false)) }
          currentDatasets={datasets}
          currentWorkspace={currentWorkspace}
          onDatasetCreate={handleDatasetCreate}
        />
      )}
      {showDatasetCleanup && <DatasetCleanupModal closeModal={() => setShowDatasetCleanup(false)} />}
    </div>
  );
}

// Render the App component to the DOM
ReactDOM.render(<App />, document.getElementById('root'));
