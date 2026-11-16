# Zekify football badge catalogue

This directory is populated during the v3.6.3 build by
`scripts/prepare-football-badges.mjs`.

## Bundled teams and competitions

The build imports the country-organised SVG collection from
[JoseArroyave/football-logos](https://github.com/JoseArroyave/football-logos).
Its upstream README states that the collection is free to use for personal and
commercial projects. Zekify retains source attribution in the generated index.

The build separates club and national-team badges from league and competition
marks, then creates country-qualified identity keys and safe global aliases.
Stable provider IDs are checked first when a mapping exists.

Club, league and federation marks may still be protected by trademark or other
rights. They are displayed for team and competition identification. The upstream
permission statement is not a representation that Zekify owns those marks.

## Remote fallback and offline cache

When a real badge is not included or cannot be matched confidently, Zekify uses
the genuine logo URL supplied by its football data source and keeps the existing
on-device badge cache. Competition logos use the same remote fallback.

## Rebuild

Run:

```sh
node scripts/prepare-football-badges.mjs
```

The build fails if fewer than 3,000 unique team badges or 25 unique competition
badges are imported. It also rejects unsafe SVG content and verifies every
catalogue path exists.
