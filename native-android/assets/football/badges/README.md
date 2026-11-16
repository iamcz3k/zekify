# Zekify global football badge bundle

The release workflow generates this folder from the current country, national-team and tournament collections at https://football-logos.cc/.

- Country coverage: all country and territory pages discovered from the source index
- Formats: 128px transparent PNG source images optimized to 128px WebP for Android
- Identity keys: provider IDs when available, then country-qualified names, aliases and normalized names
- Missing logos: MainActivity continues to use the match provider's genuine image URL and existing local cache

The generated `index.json` maps stable names and country/name pairs to bundled assets. The image files and index are generated during the release build so the repository does not carry thousands of binary files.

## Attribution and usage

Source: https://football-logos.cc/

Please credit and link to football-logos.cc. Its usage disclaimer describes informational, editorial and fan-based use, including non-commercial fan projects. Zekify is distributed free and without advertising. Do not use the collection for commercial products, merchandise, branding, promotion, or to imply affiliation or endorsement.

All club, league, federation and association names and marks remain the intellectual property of their respective owners. The source is independent and is not affiliated with those rights holders.

Full terms: https://football-logos.cc/license/
