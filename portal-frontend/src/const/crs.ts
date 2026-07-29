export const crsOptions = [
  {
    label: 'EPSG:4326  – WGS84 (Standard)',
    value: 'EPSG:4326',
    nativeBounds: [-180.0, -90.0, 180.0, 90.0], // degrees – https://epsg.io/4326
    proj4def: '+proj=longlat +datum=WGS84 +no_defs',
  },
  {
    label: 'EPSG:3857  – Web Mercator',
    value: 'EPSG:3857',
    nativeBounds: [-20037508.34, -20048966.1, 20037508.34, 20048966.1], // metres – https://epsg.io/3857
    proj4def:
      '+proj=merc +a=6378137 +b=6378137 +lat_ts=0 +lon_0=0 +x_0=0 +y_0=0 +k=1 +units=m +nadgrids=@null +wktext +no_defs',
  },
  {
    label: 'EPSG:25832 – UTM Zone 32N',
    value: 'EPSG:25832',
    // metres – realistic extent: EPSG area-of-use (6–12°E) reprojected via PROJ 9.2.1.
    // NOT epsg.io "Projected bounds" (whole domain), which is far too wide (negative easting)
    // and made external clients see no data / broke the GeoServer preview.
    nativeBounds: [239323.44, 4290145.58, 761545.65, 9365801.91],
    proj4def: '+proj=utm +zone=32 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs',
  },
  {
    label: 'EPSG:25833 – UTM Zone 33N',
    value: 'EPSG:25833',
    // metres – realistic extent: EPSG area-of-use (12–18°E) reprojected via PROJ 9.2.1.
    nativeBounds: [269387.69, 5138493.34, 731380.98, 9375835.77],
    proj4def: '+proj=utm +zone=33 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs',
  },
  {
    label: 'EPSG:4258  – ETRS89',
    value: 'EPSG:4258',
    nativeBounds: [-16.1, 32.88, 40.18, 84.73], // degrees – EPSG area-of-use (PROJ 9.2.1)
    proj4def: '+proj=longlat +ellps=GRS80 +no_defs',
  },
]
