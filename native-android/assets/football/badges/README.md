# Zekify football badge catalogue

This directory is the bundled, stable-ID-first badge collection used by
`TeamBadgeCatalog.kt`.

## Adding badges

1. Use only badge artwork that Zekify has permission to redistribute.
2. Prefer optimised SVG or transparent WebP files.
3. Store each image beneath this directory.
4. Add the exact football-data provider ID to `index.json`, pointing to its
   relative asset path, for example:

```json
{
  "schema_version": 1,
  "teams": {
    "provider-team-id": "football/badges/teams/provider-team-id.svg"
  },
  "competitions": {}
}
```

5. Keep source URL, licence/permission and attribution details in the
   corresponding source manifest before bundling.

An empty catalogue is intentional until a source's redistribution terms are
verified. Zekify continues to use the match provider's supplied badge URL and
its existing on-device image cache whenever no bundled badge is available.
