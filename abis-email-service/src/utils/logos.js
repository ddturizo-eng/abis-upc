import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const logosDir = resolve('C:/PROYECTOS P3/abis-upc/abis-backend/src/main/resources/assets');

let cache = null;

export function getLogosDataUri() {
  if (cache) return cache;

  cache = {
    logoIng: dataUri(readFileSync(resolve(logosDir, 'logo-ing sistemas.png'))),
    simboloUPC: dataUri(readFileSync(resolve(logosDir, 'SIMBOLO-UNICESAR-2024.png'))),
    abisLogo: dataUri(readFileSync(resolve(logosDir, 'img', 'ABIS-UPC-LOGO.png')))
  };

  return cache;
}

function dataUri(buffer) {
  return 'data:image/png;base64,' + buffer.toString('base64');
}
