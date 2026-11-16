# Zekify football badge catalogue

This directory is populated during the v3.6.3 build by
`scripts/prepare-football-badges.mjs`.

## Bundled team badges

The build imports the country-organised SVG collection from
[JoseArroyave/football-logos](https://github.com/JoseArroyave/football-logos).
Its upstream README states that the collection is free to use for personal and
commercial projects. Zekify retains source attribution in the generated index.

Club, league and federation marks may still be protected by trademark or other
rights. They are displayed for team and competition identification. The upstream
permission statement is not a representation that Zekify owns those marks.

The generated `index.json` maps country-qualified names and common name aliases
to local SVG assets. Stable provider IDs are checked first when a mapping exists.
When no bundled badge is matched, Zekify uses the real logo URL supplied by its
football data source and keeps its existing on-device badge cache.

## Competitions

Competition badges continue to use the real logo URL supplied by the football
data source and are cached locally. Only competition artwork with a clear
redistribution basis should be added to the bundled `competitions` index.

## Rebuild

Run:

```sh
node scripts/prepare-football-badges.mjs
```

The build fails if fewer than 1,000 valid team SVGs are imported.
