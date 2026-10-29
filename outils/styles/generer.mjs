// Génère app/src/main/assets/styles/{clair,sombre}.json depuis @protomaps/basemaps (noms en français).
// Usage : npm install && npm run generer
import { layers, namedFlavor } from "@protomaps/basemaps";
import fs from "fs";
const renommer = { "Noto Sans Regular": "NotoSans-Regular", "Noto Sans Medium": "NotoSans-Medium", "Noto Sans Italic": "NotoSans-Italic" };
const out = process.argv[2];
fs.mkdirSync(`${out}/styles`, { recursive: true });
for (const flavor of ["light", "dark"]) {
  let json = JSON.stringify(layers("protomaps", namedFlavor(flavor), { lang: "fr" }));
  for (const [a, b] of Object.entries(renommer)) json = json.split(`"${a}"`).join(`"${b}"`);
  const style = {
    version: 8,
    name: `Sente ${flavor === "light" ? "clair" : "sombre"} (d'après Protomaps)`,
    glyphs: "asset://fonts/{fontstack}/{range}.pbf",
    sprite: `asset://sprites/${flavor}`,
    sources: {
      protomaps: {
        type: "vector",
        url: "pmtiles://__FICHIER_REGION__",
        attribution: "<a href=\"https://www.openstreetmap.org/copyright\">© contributeurs OpenStreetMap</a> · <a href=\"https://protomaps.com\">Protomaps</a>"
      }
    },
    layers: JSON.parse(json)
  };
  const nom = flavor === "light" ? "clair" : "sombre";
  fs.writeFileSync(`${out}/styles/${nom}.json`, JSON.stringify(style));
  console.log(nom, style.layers.length, "couches,", JSON.stringify(style).length, "octets");
}
