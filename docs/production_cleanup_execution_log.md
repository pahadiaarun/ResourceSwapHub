# Production cleanup execution log
started_utc=2026-11-06T16:14:18.172975Z
script=resourceswaphub-database/scripts/production_test_data_cleanup.sql
target=bookbridge@165.22.211.167/32 flyway={11}

| table | before | after | delta | expected | verdict |
|---|---|---|---|---|---|
| books | 13 | 12 | -1 | -1 | OK |
| cities | 634 | 634 | 0 | 0 | OK |
| collection_items | 4 | 0 | -4 | -4 | OK |
| collections | 2 | 0 | -2 | -2 | OK |
| conversation_participants | 8 | 0 | -8 | -8 | OK |
| conversations | 4 | 0 | -4 | -4 | OK |
| countries | 2 | 2 | 0 | 0 | OK |
| districts | 791 | 791 | 0 | 0 | OK |
| encryption_replay_cache | 0 | 0 | 0 | 0 | OK |
| encryption_sessions | 0 | 0 | 0 | 0 | OK |
| exchanges | 0 | 0 | 0 | 0 | OK |
| flyway_schema_history | 11 | 11 | 0 | 0 | OK |
| institution_aliases | 46 | 46 | 0 | 0 | OK |
| institution_suggestions | 0 | 0 | 0 | 0 | OK |
| institutions | 24 | 24 | 0 | 0 | OK |
| listing_images | 0 | 0 | 0 | 0 | OK |
| listing_status_history | 0 | 0 | 0 | 0 | OK |
| listings | 11 | 0 | -11 | -11 | OK |
| location_aliases | 104 | 104 | 0 | 0 | OK |
| media_assets | 0 | 0 | 0 | 0 | OK |
| messages | 8 | 0 | -8 | -8 | OK |
| moderation_actions | 0 | 0 | 0 | 0 | OK |
| notifications | 0 | 0 | 0 | 0 | OK |
| refresh_tokens | 42 | 27 | -15 | -15 | OK |
| resource_alerts | 0 | 0 | 0 | 0 | OK |
| resource_categories | 11 | 11 | 0 | 0 | OK |
| resource_reports | 0 | 0 | 0 | 0 | OK |
| resource_requests | 0 | 0 | 0 | 0 | OK |
| saved_collections | 0 | 0 | 0 | 0 | OK |
| saved_resources | 0 | 0 | 0 | 0 | OK |
| skills | 0 | 0 | 0 | 0 | OK |
| states | 36 | 36 | 0 | 0 | OK |
| subjects | 0 | 0 | 0 | 0 | OK |
| topic_aliases | 8 | 8 | 0 | 0 | OK |
| topics | 48 | 48 | 0 | 0 | OK |
| user_education | 0 | 0 | 0 | 0 | OK |
| user_employment | 0 | 0 | 0 | 0 | OK |
| user_interests | 0 | 0 | 0 | 0 | OK |
| user_skills | 0 | 0 | 0 | 0 | OK |
| user_subjects | 0 | 0 | 0 | 0 | OK |
| user_topics | 0 | 0 | 0 | 0 | OK |
| users | 10 | 3 | -7 | -7 | OK |

total_deleted=60
RESULT=COMMITTED
committed_utc=2026-11-06T16:14:23.754702Z

post-cleanup counts:
  books                            12
  cities                          634
  countries                         2
  districts                       791
  flyway_schema_history            11
  institution_aliases              46
  institutions                     24
  location_aliases                104
  refresh_tokens                   27
  resource_categories              11
  states                           36
  topic_aliases                     8
  topics                           48
  users                             3
