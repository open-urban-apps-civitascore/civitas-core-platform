export const crsOptions = [
  {
    label: 'EPSG:4326  – WGS84 (Standard)',
    value: 'EPSG:4326',
    nativeBounds: [-180.0, -90.0, 180.0, 90.0], // degrees – https://epsg.io/4326
  },
  {
    label: 'EPSG:3857  – Web Mercator',
    value: 'EPSG:3857',
    nativeBounds: [-20037508.34, -20048966.1, 20037508.34, 20048966.1], // metres – https://epsg.io/3857
  },
  {
    label: 'EPSG:25832 – UTM Zone 32N',
    value: 'EPSG:25832',
    nativeBounds: [-1866822.47, 3680224.65, 3246120.36, 9483069.2], // metres – https://epsg.io/25832
  },
  {
    label: 'EPSG:25833 – UTM Zone 33N',
    value: 'EPSG:25833',
    nativeBounds: [-2450512.62, 3680451.78, 2665647.82, 9493779.8], // metres – https://epsg.io/25833
  },
  {
    label: 'EPSG:4258  – ETRS89',
    value: 'EPSG:4258',
    nativeBounds: [-16.1, 33.26, 38.01, 84.73], // degrees – https://epsg.io/4258
  },
]
