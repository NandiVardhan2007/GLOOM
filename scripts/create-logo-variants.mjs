import fs from 'node:fs';

const lightSvg = fs.readFileSync('assets/logo.svg', 'utf8');
const darkSvg = lightSvg.replace(/fill="#FCFCFC"/g, 'fill="#111111"');

// Write dark version to assets/
fs.writeFileSync('assets/logo-dark.svg', darkSvg, 'utf8');

// Write square version for icon generation (1308 x 1308)
const squareSvg = lightSvg
  .replace('viewBox="0 0 1308 1203" width="1308" height="1203"', 'viewBox="0 0 1308 1308" width="1308" height="1308"')
  .replace('<path ', '<g transform="translate(0, 52.5)"><path ')
  .replace('</svg>', '</g></svg>');
fs.writeFileSync('assets/logo-square.svg', squareSvg, 'utf8');

// Write both to Desktop/public/
fs.writeFileSync('Desktop/public/gloom.svg', lightSvg, 'utf8');
fs.writeFileSync('Desktop/public/gloom-dark.svg', darkSvg, 'utf8');
fs.writeFileSync('Desktop/public/logo.svg', lightSvg, 'utf8');
fs.writeFileSync('Desktop/public/logo-dark.svg', darkSvg, 'utf8');

console.log('Successfully created logo SVG variants:');
console.log(' - assets/logo.svg (light icon for dark/black backgrounds)');
console.log(' - assets/logo-dark.svg (dark icon for light backgrounds)');
console.log(' - assets/logo-square.svg (squared vector icon)');
console.log(' - Desktop/public/gloom.svg');
console.log(' - Desktop/public/gloom-dark.svg');
console.log(' - Desktop/public/logo.svg');
console.log(' - Desktop/public/logo-dark.svg');

